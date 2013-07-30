package com.novus.unfinagled

import com.twitter.finagle.builder.{ ServerBuilder => FServerBuilder, ServerConfig, Server }
import com.twitter.finagle.Service
import com.twitter.util.Await
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import unfiltered.util.RunnableServer
import org.jboss.netty.channel.ServerChannelFactory
import org.jboss.netty.channel.socket.nio.{ NioWorkerPool, NioServerSocketChannelFactory }
import java.util.concurrent.Executors
import com.twitter.concurrent.NamedPoolThreadFactory
import com.twitter.finagle.netty3.Netty3Listener

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

  // TODO cleanup / move / make saner
  // Temporary hacky fix for https://github.com/novus/unfinagled/issues/1.
  //   - replace the channel factory with one we can isolate; this allows us to reliably shut it down.
  //   - forcibly shutdown Netty3Listener.channelFactory as part of destroy, otherwise the associated executor and
  //     worker pool will carry on
  def channelFactory: ServerChannelFactory = {
    val e = Executors.newCachedThreadPool(
      new NamedPoolThreadFactory("unfinagled/netty3", true /*daemon*/ ))
    val wp = new NioWorkerPool(e, Runtime.getRuntime().availableProcessors() * 2)
    new NioServerSocketChannelFactory(e, wp)
  }

  def apply(serviceName: String = "Unfiltered", port: Int = 8080): Http =
    Http(serviceName, port, None, None)
}

trait HttpServer extends RunnableServer { self =>

  override type ServerBuilder >: self.type <: HttpServer

  @volatile private var server: Option[Server] = None

  private lazy val cf = Http.channelFactory

  private lazy val underlying =
    FServerBuilder()
      .channelFactory(cf)
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
    // Make sure this is dead, otherwise CPU will go nuts (https://github.com/novus/unfinagled/issues/1)
    Netty3Listener.channelFactory.shutdown()
    cf.releaseExternalResources()
    HttpServer.this
  }

}
