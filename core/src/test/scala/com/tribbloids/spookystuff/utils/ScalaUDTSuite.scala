package com.tribbloids.spookystuff.utils

import com.tribbloids.spookystuff.SpookyEnvSuite
import com.tribbloids.spookystuff.actions.Action
import com.tribbloids.spookystuff.doc.{Doc, Fetched, Unstructured}
import com.tribbloids.spookystuff.tests.TestMixin
import org.apache.spark.sql.types.DataType

/**
  * Created by peng on 28/05/16.
  */
class ScalaUDTSuite extends SpookyEnvSuite with TestMixin {

  import ImplicitUtils._
  import org.apache.spark.sql.catalyst.ScalaReflection.universe._

  def getAndTestReifiedType[T: TypeTag]: DataType = {
    val unreified = UnreifiedScalaType.apply[T]
    assureSerializable(unreified)

    val reified = TypeUtils.catalystTypeFor[T]
    assert(reified == unreified.reify)
    assureSerializable(reified)
    reified
  }

  test("Int has a datatype") {

    val reified = getAndTestReifiedType[Int]
    reified.toString.shouldBe(
      """
        |IntegerType
      """.stripMargin
    )
  }

  test("Array[Int] has a datatype") {

    val reified = getAndTestReifiedType[Array[Int]]
    reified.toString.shouldBe(
      """
        |ArrayType(IntegerType,false)
      """.stripMargin
    )
  }

  test("Action has a datatype") {

    val reified = getAndTestReifiedType[Action]
    reified.toString.shouldBe(
      """
        |action
      """.stripMargin
    )
  }

  test("Array[Action] has a datatype") {

    val reified = getAndTestReifiedType[Array[Action]]
    reified.toString.shouldBe(
      """
        |ArrayType(action,true)
      """.stripMargin
    )
  }

  test("Unstructured has a datatype") {

    val reified = getAndTestReifiedType[Unstructured]
    reified.toString.shouldBe(
      """
        |unstructured
      """.stripMargin
    )
  }

  test("Fetched has a datatype") {

    val reified = getAndTestReifiedType[Fetched]
    reified.toString.shouldBe(
      """
        |fetched
      """.stripMargin
    )
  }

  test("Doc has a datatype") {

    val reified = getAndTestReifiedType[Doc]
    reified.toString.shouldBe(
      """
        |doc
      """.stripMargin
    )
  }
}