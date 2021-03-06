package com.tribbloids.spookystuff.dsl

import com.tribbloids.spookystuff.actions.DocFilter
import com.tribbloids.spookystuff.doc.Doc
import com.tribbloids.spookystuff.session.Session
import com.tribbloids.spookystuff.utils.PrettyToStringMixin
import org.slf4j.LoggerFactory

//TODO: support chaining & extends ExpressionLike/TreeNode
trait AbstractDocFilter extends DocFilter with PrettyToStringMixin {

  def assertStatusCode(page: Doc){
    page.httpStatus.foreach {
      v =>
        assert(v.getStatusCode.toString.startsWith("2"), v.toString)
    }
  }
}

object DocFilters {

  case object AllowStatusCode2XX extends AbstractDocFilter {

    override def apply(result: Doc, session: Session): Doc = {
      assertStatusCode(result)
      result
    }
  }

  case object MustHaveTitle extends AbstractDocFilter {

    override def apply(result: Doc, session: Session): Doc = {
      assertStatusCode(result)
      if (result.mimeType.contains("html")){
        assert(result.\("html").\("title").text.getOrElse("").nonEmpty, s"Html Page @ ${result.uri} has no title")
        LoggerFactory.getLogger(this.getClass).info(s"Html Page @ ${result.uri} has no title:\n${result.code}")
      }
      result
    }
  }
}