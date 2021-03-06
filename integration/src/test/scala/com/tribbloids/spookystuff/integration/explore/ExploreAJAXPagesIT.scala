package com.tribbloids.spookystuff.integration.explore

import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.integration.IntegrationSuite

import scala.concurrent.duration._

/**
  * Created by peng on 12/10/14.
  */
class ExploreAJAXPagesIT extends IntegrationSuite {

  import com.tribbloids.spookystuff.dsl._

  override lazy val drivers = Seq(
    phantomJS //TODO: HtmlUnit does not support Backbone.js
  )

  override def doMain(): Unit = {

    val snapshotAllPages = (
      Snapshot()
        +> Loop (
        ClickNext("button.btn", "1"::Nil)
          +> Delay(2.seconds)
          +> Snapshot()
      ))

    val base = spooky
      .fetch(
        Visit("http://webscraper.io/test-sites/e-commerce/ajax")
          +> snapshotAllPages
      )

    val result = base
      .explore(S"div.sidebar-nav a", ordinalField = 'index)(
        Visit('A.href)
          +> snapshotAllPages,
        depthField = 'depth
      )(
        G ~+ 'page_index.`#`,
        S"button.btn.btn-primary".text ~ 'page_number,
        'A.text ~ 'category,
        S.findAll("h1").text ~ 'title,
        S"a.title".size ~ 'num_product
      )
      .toDF(sort = true)

    result.schema.treeString.shouldBe(
      """
        |root
        | |-- depth: integer (nullable = true)
        | |-- index: array (nullable = true)
        | |    |-- element: integer (containsNull = true)
        | |-- page_index: array (nullable = true)
        | |    |-- element: integer (containsNull = true)
        | |-- page_number: string (nullable = true)
        | |-- category: string (nullable = true)
        | |-- title: string (nullable = true)
        | |-- num_product: integer (nullable = true)
      """.stripMargin
    )

    val formatted = result.toJSON.collect().mkString("\n")
    formatted.shouldBe (
      """
        |{"depth":0,"page_index":[0],"title":"E-commerce training site","num_product":3}
        |{"depth":1,"index":[1],"page_index":[0,0],"category":"Computers","title":"Computers category","num_product":3}
        |{"depth":1,"index":[2],"page_index":[0,0],"category":"Phones","title":"Phones category","num_product":3}
        |{"depth":2,"index":[1,2],"page_index":[0,0,0],"page_number":"1","category":"Laptops","title":"Computers / Laptops","num_product":6}
        |{"depth":2,"index":[1,2],"page_index":[0,0,1],"page_number":"2","category":"Laptops","title":"Computers / Laptops","num_product":6}
        |{"depth":2,"index":[1,2],"page_index":[0,0,2],"page_number":"3","category":"Laptops","title":"Computers / Laptops","num_product":2}
        |{"depth":2,"index":[1,3],"page_index":[0,0,0],"page_number":"1","category":"Tablets","title":"Computers / Tablets","num_product":6}
        |{"depth":2,"index":[1,3],"page_index":[0,0,1],"page_number":"2","category":"Tablets","title":"Computers / Tablets","num_product":6}
        |{"depth":2,"index":[1,3],"page_index":[0,0,2],"page_number":"3","category":"Tablets","title":"Computers / Tablets","num_product":6}
        |{"depth":2,"index":[1,3],"page_index":[0,0,3],"page_number":"4","category":"Tablets","title":"Computers / Tablets","num_product":3}
        |{"depth":2,"index":[2,3],"page_index":[0,0,0],"page_number":"1","category":"Touch","title":"Phones / Touch","num_product":6}
        |{"depth":2,"index":[2,3],"page_index":[0,0,1],"page_number":"2","category":"Touch","title":"Phones / Touch","num_product":3}
      """.stripMargin
    )
  }

  override def numSessions =  6

  override def numPages = 12
}
