package com.novus.unfinagled

import com.twitter.finagle.builder.{ ServerBuilder => FServerBuilder, ServerConfig, Server }
import com.twitter.finagle.Service
import com.twitter.util.Await
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import unfiltered.util.RunnableServer

case class Http private (
  override val serviceName: String,
  override val port: Int,
  override val finagleService: Option[Service[HttpRequest, HttpResponse]],
  override val configurator: Option[Http.FullyConfigured => Http.FullyConfigured])
    extends HttpServer {

  override type ServerBuilder = Http

  override def configure(c: Http.FullyConfigured => Http.FullyConfigured) =
    copy(configurator = Some(c))

  override def service(s: Service[HttpRequest, HttpResponse]) =
    copy(finagleService = Some(s))
}

object Http {

  type FullyConfigured = FServerBuilder[HttpRequest, HttpResponse, ServerConfig.Yes, ServerConfig.Yes, ServerConfig.Yes]

  def apply(serviceName: String = "Unfiltered", port: Int = 8080): Http =
    Http(serviceName, port, None, None)
}

trait HttpServer extends RunnableServer { self =>

  override type ServerBuilder >: self.type <: HttpServer

  @volatile private var server: Option[Server] = None

  private lazy val underlying =
    FServerBuilder()
      .bindTo(new InetSocketAddress(port))
      .name(serviceName)
      .codec(UnfilteredCodec())

  /** The finagle service name. */
  protected def serviceName: String

  /** The finagle service. */
  protected def finagleService: Option[Service[HttpRequest, HttpResponse]]

  /** A function to further configure a fully configured builder (ie, has a name, socket binding, and codec). */
  protected def configurator: Option[Http.FullyConfigured => Http.FullyConfigured]

  def configure(c: Http.FullyConfigured => Http.FullyConfigured): ServerBuilder

  def service(s: Service[HttpRequest, HttpResponse]): ServerBuilder

  override def start(): ServerBuilder = {
    server = finagleService.map(underlying.build) // TODO could prevent None-services with more types
    HttpServer.this
  }

  override def stop(): ServerBuilder = {
    for {
      svr <- server
      svc <- finagleService
    } Await.result {
      for {
        _ <- svr.close()
        _ <- svc.close() // TODO confirm if this is necessary
      } ()
    }
    destroy()
  }

  override def destroy(): ServerBuilder = {
    HttpServer.this
  }

}
