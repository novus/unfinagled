package com.novus.unfinagled

import com.twitter.finagle.builder.{ ServerBuilder => FServerBuilder, Server }
import com.twitter.finagle.Service
import com.twitter.finagle.netty3.Netty3Listener
import com.twitter.util.Await
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import unfiltered.util.RunnableServer

case class Http private (
  override val serviceName: String,
  override val port: Int,
  override val finagleService: Option[Service[HttpRequest, HttpResponse]])
    extends HttpServer {
  type ServerBuilder = Http

  def service(s: Service[HttpRequest, HttpResponse]) = copy(finagleService = Some(s))
}

object Http {
  def apply(serviceName: String = "Unfiltered", port: Int = 8080): Http =
    Http(serviceName, port, None)
}

trait HttpServer extends RunnableServer { self =>

  type ServerBuilder >: self.type <: HttpServer
  @volatile private var server: Option[Server] = None

  private lazy val underlying =
    FServerBuilder()
      .bindTo(new InetSocketAddress(port))
      .name(serviceName)
      .codec(UnfilteredCodec())

  protected def serviceName: String

  protected def finagleService: Option[Service[HttpRequest, HttpResponse]]

  def service(s: Service[HttpRequest, HttpResponse]): ServerBuilder

  def start(): ServerBuilder = {
    server = finagleService.map(underlying.build)
    HttpServer.this
  }

  def stop(): ServerBuilder = {
    for {
      svr <- server
      svc <- finagleService
    } Await.result {
      for {
        _ <- svr.close()
        _ <- svc.close()
      } ()
    }
    HttpServer.this
  }

  def destroy(): ServerBuilder = {
    Netty3Listener.channelFactory.shutdown()
    HttpServer.this
  }

}
