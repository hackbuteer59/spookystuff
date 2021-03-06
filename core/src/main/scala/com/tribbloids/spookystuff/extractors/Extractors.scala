package com.tribbloids.spookystuff.extractors

import com.tribbloids.spookystuff.doc._
import com.tribbloids.spookystuff.extractors.GenExtractor.{AndThen, Leaf, Static, StaticType}
import com.tribbloids.spookystuff.row.{DataRowSchema, _}
import com.tribbloids.spookystuff.utils.{SpookyUtils, UnreifiedScalaType}
import org.apache.spark.sql.catalyst.ScalaReflection.universe._
import org.apache.spark.sql.types._

import scala.collection.TraversableOnce
import scala.collection.immutable.ListMap
import scala.reflect.ClassTag
import com.tribbloids.spookystuff.utils.ImplicitUtils._

object Extractors {

  def GroupIndexExpr = GenExtractor.fromFn{
    (v1: FR) => v1.dataRow.groupIndex
  }

  def GetUnstructuredExpr(field: Field) = GenExtractor.fromOptionFn {
    (v1: FR) =>
      v1.getUnstructured(field)
        .orElse(v1.getUnstructured(field.copy(isWeak = true)))
  }

  def GetPageExpr(field: Field) = GenExtractor.fromOptionFn {
    (v1: FR) => v1.getPage(field.name)
  }
  def GetOnlyPageExpr = GenExtractor.fromOptionFn {
    (v1: FR) => v1.getOnlyPage
  }
  def GetAllPagesExpr = GenExtractor.fromFn {
    (v1: FR) => new Elements(v1.pages.toList)
  }

  case class FindAllMeta(arg: Extractor[Unstructured], selector: String)
  def FindAllExpr(arg: Extractor[Unstructured], selector: String) = arg.andThen(
    {
      v1: Unstructured => v1.findAll(selector)
    },
    Some(FindAllMeta(arg, selector))
  )

  case class ChildrenMeta(arg: Extractor[Unstructured], selector: String)
  def ChildrenExpr(arg: Extractor[Unstructured], selector: String) = arg.andThen(
    {
      v1 => v1.children(selector)
    },
    Some(ChildrenMeta(arg, selector))
  )

  def ExpandExpr(arg: Extractor[Unstructured], range: Range) = {
    arg match {
      case AndThen(_,_,Some(FindAllMeta(argg, selector))) =>
        argg.andThen(_.findAllWithSiblings(selector, range))
      case AndThen(_,_,Some(ChildrenMeta(argg, selector))) =>
        argg.andThen(_.childrenWithSiblings(selector, range))
      case _ =>
        throw new UnsupportedOperationException("expression does not support expand")
    }
  }

  def ReplaceKeyExpr(str: String) = GenExtractor.fromOptionFn {
    (v1: FR) =>
      v1.dataRow.replaceInto(str)
  }
}

object Literal {

  def apply[T: TypeTag](v: T): Literal[T] = {
    Literal[T](Option(v), UnreifiedScalaType.apply[T])
  }

  lazy val NULL: Literal[Null] = Literal(null)
}

class GenLiteral[T, +R](val valueOpt: Option[R], val dataType: DataType) extends Static[T, R] {

  def value: R = valueOpt.getOrElse(
    throw new UnsupportedOperationException("NULL Literal")
  )

  override def toString = valueOpt.map(
    v =>
      //      "'" + v + "':" + dataType //TODO: remove single quotes? Add Type? Use JSON str?
      "'" + v + "'" //TODO: remove single quotes? Add Type? Use JSON str?
  )
    .getOrElse("NULL")

  override val self: PartialFunction[T, R] = Unlift({ _: T => valueOpt})
}

//just a simple wrapper for T, this is the only way to execute a action
//this is the only serializable LiftedExpression that can be shipped remotely
//TODO: not quite compatible with product2String, e.g.: /WpostImpl/Literal/Some/http/172.17.0.2/5000/registrar/(unreified)_String/MustHaveTitle/
final case class Literal[+T](
                              override val valueOpt: Option[T],
                              override val dataType: DataType
                            ) extends GenLiteral[FR, T](valueOpt, dataType) {
}

case class GetExpr(field: Field) extends Leaf[FR, Any] {

  override def resolveType(tt: DataType): DataType = tt match {
    case schema: DataRowSchema =>
      schema
        .typedFor(field)
        .orElse{
          schema.typedFor(field.*)
        }
        .map(_.dataType)
        .getOrElse(NullType)
    case _ =>
      throw new UnsupportedOperationException("Can only resolve type against SchemaContext")
  }

  override def resolve(tt: DataType): PartialFunction[FR, Any] = Unlift(
    v =>
      v.dataRow.orWeak(field)
  )

  def GetSeqExpr: GenExtractor[FR, Seq[Any]] = this.andOptionTyped[Any, Seq[Any]](
    {
      case v: TraversableOnce[Any] => Some(v.toSeq)
      case v: Array[Any] => Some(v.toSeq)
      case _ => None
    },
    {
      _.ensureArray
    }
  )

  def AsSeqExpr: GenExtractor[FR, Seq[Any]] = this.andOptionTyped[Any, Seq[Any]](
    {
      case v: TraversableOnce[Any] => Some(v.toSeq)
      case v: Array[Any] => Some(v.toSeq)
      case v@ _ => Some(Seq(v))
    },
    {
      _.asArray
    }
  )
}

object AppendExpr {

  def create[T: ClassTag](
                           field: Field,
                           expr: Extractor[T]
                         ): Alias[FR, Seq[T]] = {

    AppendExpr[T](GetExpr(field), expr).withAlias(field.!!)
  }
}

case class AppendExpr[+T: ClassTag] private(
                                             get: GetExpr,
                                             expr: Extractor[T]
                                           ) extends Extractor[Seq[T]] {

  override def resolveType(tt: DataType): DataType = {
    val existingType = expr.resolveType(tt)

    existingType.asArray
  }

  override def resolve(tt: DataType): PartialFunction[FR, Seq[T]] = {
    val getSeqResolved = get.AsSeqExpr.resolve(tt).lift
    val exprResolved = expr.resolve(tt).lift

    PartialFunction({
      v1: FR =>
        val lastOption = exprResolved.apply(v1)
        val oldOption = getSeqResolved.apply(v1)

        oldOption.toSeq.flatMap{
          old =>
            SpookyUtils.asIterable[T](old)
        } ++ lastOption
    })
  }

  override def _args: Seq[GenExtractor[_, _]] = Seq(get, expr)
}

case class InterpolateExpr(parts: Seq[String], _args: Seq[Extractor[Any]]) extends Extractor[String] with StaticType[FR, String] {

  override def resolve(tt: DataType): PartialFunction[FR, String] = {
    val rs = _args.map(_.resolve(tt).lift)

    Unlift({
      row =>
        val iParts = parts.map(row.dataRow.replaceInto(_))

        val vs = rs.map(_.apply(row))
        val result = if (iParts.contains(None) || vs.contains(None)) None
        else Some(iParts.zip(vs).map(tpl => tpl._1.get + tpl._2.get).mkString + iParts.last.get)

        result
    })
  }

  override val dataType: DataType = StringType
}

//TODO: delegate to And_->
//TODO: need tests
case class ZippedExpr[T1,+T2](
                               arg1: Extractor[Iterable[T1]],
                               arg2: Extractor[Iterable[T2]]
                             )
  extends Extractor[Map[T1, T2]] {

  override val _args: Seq[GenExtractor[FR, _]] = Seq(arg1, arg2)

  override def resolveType(tt: DataType): DataType = {
    val t1 = arg1.resolveType(tt).unboxArrayOrMap
    val t2 = arg2.resolveType(tt).unboxArrayOrMap

    MapType(t1, t2)
  }

  override def resolve(tt: DataType): PartialFunction[FR, Map[T1, T2]] = {
    val r1 = arg1.resolve(tt).lift
    val r2 = arg2.resolve(tt).lift

    Unlift({
      row =>
        val z1Option = r1.apply(row)
        val z2Option = r2.apply(row)

        if (z1Option.isEmpty || z2Option.isEmpty) None
        else {
          val map: ListMap[T1, T2] = ListMap(z1Option.get.toSeq.zip(
            z2Option.get.toSeq
          ): _*)

          Some(map)
        }
    })
  }
}