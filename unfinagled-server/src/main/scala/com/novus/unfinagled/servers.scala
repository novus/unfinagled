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

  /*
   * TODO cleanup / move / make saner
   * Temporary hacky fix for https://github.com/novus/unfinagled/issues/1.
   *  - replace the channel factory with one we can isolate; this allows us to reliably shut it down.
   *  - forcibly shutdown Netty3Listener.channelFactory as part of destroy, otherwise the associated executor and
   *    worker pool will carry on
   */
  private[unfinagled] def channelFactory: ServerChannelFactory = {
    val e = Executors.newCachedThreadPool(
      new NamedPoolThreadFactory("unfinagled/netty3", true))
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

  /** An extension point for further configuring finagle. */
  def configure(c: Http.FullyConfigured => Http.FullyConfigured): ServerBuilder

  /** Set the finagle service. This would generally be the last step before booting the server. */
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
    /*
     * TL;DR Without this an underlying Executor will remain alive and result in 100% cpu usage on console (or run)
     * exit (see https://github.com/novus/unfinagled/issues/1).
     *
     * Finagle includes a global, eagerly evaluated (val) ServerChannelFactory [1] which initializes a global, shared
     * Executor. This channel factory is referenced as a default argument to ServerConfig instances [3], and so the
     * executor should be expected to be alive in any finagle instance, whether it's actually used or not.
     *
     * Such executors tend to prevent sbt from being able to exit console or exit run-main. However, because this
     * executor yields daemon threads, exit is possible, but at the cost of the cpu running wildly at 100%. Profiling
     * indicates a tight-looped read call, but the connection between this and a daemonized executor is something into
     * which I have yet to dig further.
     *
     * Fortunately multiple shutdown calls are harmless. Unfortunately this is a global, and thus potentially shared,
     * channel factory, and if it is shared then the dependent processes will be incapacitated henceforth (one could
     * imagine a single JVM being used to spawn multiple finagle servers). I think the right solution is not using
     * global, eager executors in finagle; a design choice I find quite odd but I can't yet say that I have a holistic
     * understanding of its architecture.
     *
     * [1] https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/netty3/server.scala#L35
     * [2] https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/netty3/package.scala#L17
     * [3] https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/builder/ServerBuilder.scala#L99
     */
    Netty3Listener.channelFactory.shutdown()
    cf.releaseExternalResources()
    HttpServer.this
  }

}
