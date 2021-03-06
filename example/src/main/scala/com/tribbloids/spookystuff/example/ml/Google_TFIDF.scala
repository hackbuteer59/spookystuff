package com.tribbloids.spookystuff.example.ml

import org.apache.spark.mllib.feature.{HashingTF, IDF}
import com.tribbloids.spookystuff.SpookyContext
import com.tribbloids.spookystuff.dsl._
import com.tribbloids.spookystuff.example.{Common, QueryCore}
import com.tribbloids.spookystuff.http.HttpUtils
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD

object Google_TFIDF extends QueryCore {

  override def doMain(spooky: SpookyContext) = {
    import spooky.dsl._
    import sql.implicits._

    val raw = sc.parallelize("Dell, DJI, Starbucks, McDonald".split(",").map(_.trim)).fetch(
      Common.googleSearch('_)
    ).wgetExplore(S"a:contains(next)", maxDepth = 1, depthKey = 'page, optimizer = Narrow
    ).wgetJoin(S"li.g h3.r a".hrefs.flatMap {
      uri =>
        val query = HttpUtils.uri(uri).getQuery
        val realURI = if (query == null) Some(uri)
        else if (uri.contains("/url?")) query.split("&").find(_.startsWith("q=")).map(_.replaceAll("q=",""))
        else None
        realURI
    },failSafe = 2 ).select(
      S.boilerPipe.orElse(Some("")) ~ 'text
    ).toDF()

    val tokenized: RDD[(String, Seq[String])] =
    //      new RegexTokenizer().setInputCol("text").setOutputCol("words").setMinTokenLength(3).setPattern("[^A-Za-z]").transform(raw)
      raw
        .select('_, 'words).map(row => row.getAs[String](0) -> row.getAs[Seq[String]](1)).reduceByKey(_ ++ _)
    //    val stemmed: RDD[(String, Seq[String])] = tokenized.mapValues(_.map(v => v.toLowerCase))
    val stemmed = tokenized.mapValues(_.map(v => PorterStemmer.apply(v.toLowerCase)))

    val hashingTF: HashingTF = new HashingTF()
    val tf: RDD[Vector] = hashingTF.transform(stemmed.values).persist()
    val idf = new IDF().fit(tf)
    val tfidf: RDD[Vector] = idf.transform(tf)

    //TODO: zip is dangerous if source is sorted
    val zipped: RDD[((String, Seq[String]), Vector)] = stemmed.zip(tfidf)
    val all: RDD[(String, Seq[String], Vector)] = zipped.map(tuple => (tuple._1._1, tuple._1._2.distinct, tuple._2))
    val wordvec: RDD[(String, (String, Double))] = all.map(
      tuple => (tuple._1, tuple._2.map(
        word =>(word, tuple._3.apply(hashingTF.indexOf(word)))
      )
        .sortBy(- _._2))
    )
      .mapValues(_.slice(0,10)).flatMapValues(v => v)

    val df = wordvec.map(v => EntityKeyword(v._1, v._2._1, v._2._2.toString))
    df.toDF()
  }
}