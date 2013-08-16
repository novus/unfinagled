package com.novus.unfinagled

import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import com.twitter.finagle.Service

trait Served extends FinagleServed[unfiltered.netty.cycle.Plan.Intent] {
  def service: Service[HttpRequest, HttpResponse] = UnfilteredService(intent)
}
