package com.novus.unfinagled

import org.scalatest.FeatureSpec
import unfiltered.scalatest.Hosted
import dispatch.classic.{ Handler, Http => DHttp }
import unfiltered.netty.cycle.Plan.Intent
import org.jboss.netty.channel.ServerChannelFactory
import java.util.concurrent.Executors
import com.twitter.concurrent.NamedPoolThreadFactory
import org.jboss.netty.channel.socket.nio.{NioServerSocketChannelFactory, NioWorkerPool}

trait Served extends FeatureSpec with Hosted {

  def intent: Intent

  def getServer =
    Http(java.util.UUID.randomUUID.toString + "@" + port, port)
      .service(UnfilteredService(intent))

  val status: Handler.F[Int] = { case (c, _, _) => c }

  def withHttp[T](req: DHttp => T): T = {
    val h = new DHttp
    try { req(h) }
    finally { h.shutdown() }
  }

  override protected def withFixture(test: NoArgTest) {
    val server = getServer
    try {
      server.start()
      test() // Invoke the test function
    }
    finally {
      server.stop()
    }
  }
}

object F {
  def channelFactory: ServerChannelFactory = {
    val e = Executors.newCachedThreadPool(
      new NamedPoolThreadFactory("finagle/netty3", true /*daemon*/ ))
    val wp = new NioWorkerPool(e, Runtime.getRuntime().availableProcessors() * 2)
    new NioServerSocketChannelFactory(e, wp)
  }
}
