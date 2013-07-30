package com.novus.unfinagled

import org.scalatest.FeatureSpec
import unfiltered.scalatest.Hosted
import dispatch.classic.{ Handler, Http => DHttp }
import unfiltered.netty.cycle.Plan.Intent

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