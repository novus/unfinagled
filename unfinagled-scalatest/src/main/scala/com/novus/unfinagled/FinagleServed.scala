package com.novus.unfinagled

import com.twitter.finagle.Service
import dispatch.classic.{ Handler, Http => DHttp }
import java.util.UUID.randomUUID
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import org.scalatest.FeatureSpec
import unfiltered.scalatest.Hosted

trait FinagleServed[T] extends FeatureSpec with Hosted {

  def intent: T

  def service: Service[HttpRequest, HttpResponse]

  def getServer =
    Http(randomUUID.toString + "@" + port, port)
      .service(service)

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
