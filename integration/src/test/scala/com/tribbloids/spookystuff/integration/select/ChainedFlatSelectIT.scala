package com.tribbloids.spookystuff.integration.select

import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.dsl._
import com.tribbloids.spookystuff.integration.IntegrationSuite

/**
  * Created by peng on 11/26/14.
  */
class ChainedFlatSelectIT extends IntegrationSuite {

  override lazy val drivers = Seq(
    null
  )

  override def doMain() {

    val r1 = spooky
      .fetch(
        Wget("http://webscraper.io/test-sites/e-commerce/allinone") //this site is unstable, need to revise
      )
      .flatExtract(S"div.thumbnail", ordinalField = 'i1)(
        A"p".attr("class") ~ 'p_class
      )

    val r2 = r1
      .flatExtract(A"h4", ordinalField = 'i2)(
        'A.attr("class") ~ 'h4_class
      )

    val result = r2
      .flatExtract(S"notexist", ordinalField = 'notexist_key)( //this is added to ensure that temporary joinKey in KV store won't be used.
        'A.attr("class") ~ 'notexist_class
      )
      .toDF(sort = true)

    result.schema.treeString.shouldBe(
      """
        |root
        | |-- i1: array (nullable = true)
        | |    |-- element: integer (containsNull = true)
        | |-- p_class: string (nullable = true)
        | |-- i2: array (nullable = true)
        | |    |-- element: integer (containsNull = true)
        | |-- h4_class: string (nullable = true)
        | |-- notexist_key: array (nullable = true)
        | |    |-- element: integer (containsNull = true)
        | |-- notexist_class: string (nullable = true)
      """.stripMargin
    )

    val formatted = result.toJSON.collect().toSeq
    assert(
      formatted ===
        """
          |{"i1":[0],"p_class":"description","i2":[0],"h4_class":"pull-right price"}
          |{"i1":[0],"p_class":"description","i2":[1]}
          |{"i1":[1],"p_class":"description","i2":[0],"h4_class":"pull-right price"}
          |{"i1":[1],"p_class":"description","i2":[1]}
          |{"i1":[2],"p_class":"description","i2":[0],"h4_class":"pull-right price"}
          |{"i1":[2],"p_class":"description","i2":[1]}
        """.stripMargin.trim.split('\n').toSeq
    )
  }

  override def numPages= 1

  override def numDrivers = 0
}