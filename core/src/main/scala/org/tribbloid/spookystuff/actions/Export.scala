package org.tribbloid.spookystuff.actions

import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.net.ssl.SSLContext

import org.apache.commons.io.IOUtils
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.{ClientProtocolException, RedirectException}
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLContexts
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.{HttpHost, StatusLine}
import org.openqa.selenium.{OutputType, TakesScreenshot}
import org.tribbloid.spookystuff.ActionException
import org.tribbloid.spookystuff.entity.PageRow
import org.tribbloid.spookystuff.expressions.{Expression, Literal}
import org.tribbloid.spookystuff.http._
import org.tribbloid.spookystuff.pages._
import org.tribbloid.spookystuff.session.Session
import org.tribbloid.spookystuff.utils.Utils

/**
 * Export a page from the browser or http client
 * the page an be anything including HTML/XML file, image, PDF file or JSON string.
 */
abstract class Export extends Named{

  final override def outputNames = Set(this.name)

  final override def trunk = None //have not impact to driver

  final def doExe(session: Session) = doExeNoName(session)

  def doExeNoName(session: Session): Seq[PageLike]
}

/**
 * Export the current page from the browser
 * interact with the browser to load the target page first
 * only for html page, please use wget for images and pdf files
 * always export as UTF8 charset
 */
case class Snapshot() extends Export{

  // all other fields are empty
  override def doExeNoName(pb: Session): Seq[Page] = {

    //    import scala.collection.JavaConversions._

    //    val cookies = pb.driver.manage().getCookies
    //    val serializableCookies = ArrayBuffer[SerializableCookie]()
    //
    //    for (cookie <- cookies) {
    //      serializableCookies += cookie.asInstanceOf[SerializableCookie]
    //    }

    val page = new Page(
      PageUID(pb.backtrace :+ this, this),
      pb.driver.getCurrentUrl,
      "text/html; charset=UTF-8",
      pb.driver.getPageSource.getBytes("UTF8")
      //      serializableCookies
    )

    Seq(page)
  }
}

//this is used to save GC when invoked by anothor component
object DefaultSnapshot extends Snapshot()

case class Screenshot() extends Export {

  override def doExeNoName(pb: Session): Seq[Page] = {

    val content = pb.driver match {
      case ts: TakesScreenshot => ts.getScreenshotAs(OutputType.BYTES)
      case _ => throw new UnsupportedOperationException("driver doesn't support snapshot")
    }

    val page = new Page(
      PageUID(pb.backtrace :+ this, this),
      pb.driver.getCurrentUrl,
      "image/png",
      content
    )

    Seq(page)
  }
}

object DefaultScreenshot extends Screenshot()

/**
 * use an http GET to fetch a remote resource deonted by url
 * http client is much faster than browser, also load much less resources
 * recommended for most static pages.
 * actions for more complex http/restful API call will be added per request.
 * @param uri support cell interpolation
 */
case class Wget(
                 uri: Expression[Any],
                 hasTitle: Boolean = true
                 ) extends Export with Driverless with Timed {

  override def doExeNoName(session: Session): Seq[PageLike] = {

    val uriStr = uri.asInstanceOf[Literal[String]].value.trim()
    if ( uriStr.isEmpty ) return Seq ()

    val uriURI = HttpUtils.uri(uriStr)

    val proxy = session.spooky.conf.proxy()
    val userAgent = session.spooky.conf.userAgent()
    val headers = session.spooky.conf.headers()

    val requestConfig = {
      val timeoutMillis = this.timeout(session).toMillis.toInt

      var builder = RequestConfig.custom()
        .setConnectTimeout ( timeoutMillis )
        .setConnectionRequestTimeout ( timeoutMillis )
        .setSocketTimeout( timeoutMillis )
        .setRedirectsEnabled(true)
        .setCircularRedirectsAllowed(true)
        .setRelativeRedirectsAllowed(true)
        .setAuthenticationEnabled(false)
      //        .setCookieSpec(CookieSpecs.BEST_MATCH)

      if (proxy!=null && !proxy.protocol.startsWith("socks")) builder=builder.setProxy(new HttpHost(proxy.addr, proxy.port, proxy.protocol))

      val result = builder.build()
      result
    }

    val sslContext = SSLContext.getInstance( "SSL" )
    sslContext.init(null, Array(new InsecureTrustManager()), null)
    val hostVerifier = new InsecureHostnameVerifier()

    val httpClient = if (proxy !=null && proxy.protocol.startsWith("socks")) {
      val reg = RegistryBuilder.create[ConnectionSocketFactory]
        .register("http", new SocksProxyConnectionSocketFactory())
        .register("https", new SocksProxySSLConnectionSocketFactory(sslContext))
        .build()
      val cm = new PoolingHttpClientConnectionManager(reg)

      val httpClient = HttpClients.custom
        .setConnectionManager(cm)
        .setDefaultRequestConfig ( requestConfig )
        .setRedirectStrategy(new ResilientRedirectStrategy())
        .setSslcontext(sslContext)
        .setHostnameVerifier(hostVerifier)
        .build

      httpClient
    }
    else {
      val httpClient = HttpClients.custom
        .setDefaultRequestConfig ( requestConfig )
        .setRedirectStrategy(new ResilientRedirectStrategy())
        .setSslcontext(sslContext)
        .setHostnameVerifier(hostVerifier)
        .build()

      httpClient
    }

    val request = {
      val request = new HttpGet(uriURI)
      if (userAgent != null) request.addHeader("User-Agent", userAgent)
      for (pair <- headers) {
        request.addHeader(pair._1, pair._2)
      }

      request
    }
    val context = if (proxy !=null && proxy.protocol.startsWith("socks")) {
      val socksaddr: InetSocketAddress = new InetSocketAddress(proxy.addr, proxy.port)
      val context: HttpClientContext = HttpClientContext.create
      context.setAttribute("socks.address", socksaddr)

      context
    }
    else null

    try {
      val response = httpClient.execute ( request, context )
      try {
        val httpStatus: StatusLine = response.getStatusLine
        assert(httpStatus.getStatusCode.toString.startsWith("2"), httpStatus.toString)
        val entity = response.getEntity

        val stream = entity.getContent
        try {
          val content = IOUtils.toByteArray ( stream )
          val contentType = entity.getContentType.getValue

          val result = new Page(
            PageUID(Seq(this), this),
            uriURI.toASCIIString,
            contentType,
            content
          )

          if (result.root.isInstanceOf[HtmlElement] && hasTitle){
            try{
              assert(!result.code.get.contains("<title></title>"))
            }
            catch {
              case e: Throwable =>
                var message = "\n\n+>" + this.toString

                val errorDump: Boolean = session.spooky.conf.errorDump

                if (errorDump) {
                  message += "\nSnapshot: " +this.errorDump(message, result, session.spooky)
                }

                throw new ActionException(message, e)
            }
          }

          Seq(result)
        }
        finally {
          stream.close()
        }
      }
      finally {
        response.close()
      }
    }
    catch {
      case e: ClientProtocolException =>
        val cause = e.getCause
        if (cause.isInstanceOf[RedirectException]) Seq(NoPage(session.backtrace :+ this))
        else throw e
      case e: Throwable =>
        throw e
    }
  }

  override def doInterpolate(pageRow: PageRow): Option[this.type] = {
    val first = this.uri(pageRow).flatMap(Utils.encapsulateAsIterable(_).headOption)

    val uriStr: Option[String] = first.flatMap {
      case element: Unstructured => element.href
      case str: String => Option(str)
      case obj: Any => Option(obj.toString)
      case other => None
    }

    uriStr.map(
      str =>
        this.copy(uri = new Literal(str)).asInstanceOf[this.type]
    )
  }
}