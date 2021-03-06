package com.tribbloids.spookystuff.actions

import com.tribbloids.spookystuff.SpookyEnvSuite
import com.tribbloids.spookystuff.doc.Doc
import com.tribbloids.spookystuff.session.Session
import com.tribbloids.spookystuff.tests.TestMixin

case class User(
                 name: String,
                 properties: Array[Tuple2[Int, Int]]
               )

class TestTrace extends SpookyEnvSuite with TestMixin {

  import com.tribbloids.spookystuff.dsl._

  import scala.concurrent.duration._

  test("inject output names should change output doc names") {

    val t1 = (
      Visit("http://webscraper.io/test-sites/e-commerce/ajax/computers/laptops")
        :: Snapshot().as('a)
        :: Loop (
        ClickNext("button.btn","1"::Nil)
          +> Delay(2.seconds)
          +> Snapshot() ~'b
      ):: Nil
      )

    val t2 = (
      Visit("http://webscraper.io/test-sites/e-commerce/ajax/computers/laptops")
        :: Snapshot().as('c)
        :: Loop (
        ClickNext("button.btn","1"::Nil)
          +> Delay(2.seconds)
          +> Snapshot() ~'d
      ):: Nil
      )

    t1.injectFrom(t2.asInstanceOf[t1.type ])

    assert(t1.outputNames === Set("c","d"))
  }

  test("dryrun should discard preceding actions when calculating Driverless action's backtrace") {

    val dry = (Delay(10.seconds) +> Wget("http://dum.my")).head.dryrun
    assert(dry.size == 1)
    assert(dry.head == Seq(Wget("http://dum.my")))

    val dry2 = (Delay(10.seconds) +> OAuthV2(Wget("http://dum.my"))).head.dryrun
    assert(dry2.size == 1)
    assert(dry2.head == Seq(OAuthV2(Wget("http://dum.my"))))
  }

  test("Trace.correct should not modify empty Trace") {

    assert(TraceView(List[Action]()).correct == List[Action]())
  }

  test("Trace.correct should append Snapshot to non-empty Trace that doesn't end with Export OR Block") {

    val trace = List(
      Visit("dummy"),
      Snapshot() ~ 'A,
      Click("dummy")
    )

    assert(trace.correct == trace :+ Snapshot())
  }

  test("Trace.correct should append Snapshot to non-empty Trace that has no output") {

    val trace = List(
      Visit("dummy"),
      Snapshot() ~ 'A,
      Loop(
        TextInput("dummy", 'dummy) +>
          Click("dummy")
      )
    )

    assert(trace.correct == trace :+ Snapshot())
  }

  test("TraceView.toString should have identations of TreeNode") {
    import com.tribbloids.spookystuff.dsl._

    val traces: Set[Trace] = (
      Visit(HTML_URL)
        +> Click("dummy")
        +> Snapshot()
        +> Loop(
        Click("next")
          +> TextInput("box", "something")
          +> Snapshot()
          +> If(
          {(v: Doc, _: Session) => v.uri startsWith "http" },
          Click("o1")
            +> TextInput("box1", "something1")
            +> Snapshot(),
          Click("o2")
            +> TextInput("box2", "something2")
            +> Snapshot()
        )
      )
      )

    traces.foreach{
      trace =>
        val view = TraceView(trace)
        println(view.toString)
        assert(view.toString contains "\n")
    }
  }

//  test("Click.toString should work") {
//    val action = Click("o1")
//    val json = action.toString()
//    println(json)
//  }
//
//  test("Wget.toString should work") {
//    val action = Wget("http://dummy.com")
//    val json = action.toString()
//    println(json)
//  }
//
//  test("Loop.toString should work") {
//    val action = Loop(
//      Click("o1")
//        +> Snapshot()
//    )
//    val json = action.toString()
//    println(json)
//  }

  test("Click.toJSON should work") {
    val action = Click("o1")
    val json = action.toJSON
    println(json)
  }

  //TODO: enable these
//  test("Wget.toJSON should work") {
//    val action = Wget("http://dummy.com")
//    val json = action.toJSON
//    println(json)
//  }

  test("Loop.toJSON should work") {
    val action = Loop(
      Click("o1")
        +> Snapshot()
    )
    val json = action.toJSON
    println(json)
  }

//  test("Action.toJSON should work") {
//    val actions = Loop(
//      Click("next")
//        +> TextInput("box", "something")
//        +> Snapshot()
//        +> If(
//        { (v: Doc, _: Session) => v.uri startsWith "http" },
//        Click("o1")
//          +> TextInput("box1", "something1")
//          +> Snapshot(),
//        Click("o2")
//          +> TextInput("box2", "something2")
//          +> Snapshot()
//      ))
//
//    val jsons = actions.map(
//      v =>
//        v.toJSON
//    )
//
//    jsons.foreach(println)
//  }

  //  test("Trace has a Dataset Encoder") {
  //    val trace =(
  //      Visit(HTML_URL)
  //        +> Click("dummy")
  //        +> Snapshot()
  //        +> Loop(
  //        Click("next")
  //          +> TextInput("box", "something")
  //          +> Snapshot()
  //          +> If(
  //          {v: Doc => v.uri startsWith "http" },
  //          Click("o1")
  //            +> TextInput("box1", "something1")
  //            +> Snapshot(),
  //          Click("o2")
  //            +> TextInput("box2", "something2")
  //            +> Snapshot()
  //        ))
  //      )
  //
  //    val df = sql.read.json(sc.parallelize(trace.toSeq.map(_.toJSON)))
  //
  //    implicit val encoder = Encoders.kryo[TraceView]
  //
  //    val ds = df.as[TraceView]
  //
  //    ds.collect().foreach(println)
  //  }
}
