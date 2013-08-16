package com.novus.unfinagled

import unfiltered.request.HttpRequest
import unfiltered.netty.ReceivedMessage
import unfiltered.response.ResponseFunction
import org.jboss.netty.handler.codec.http.{ HttpResponse => NHttpResponse }

object FuturePlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], scala.concurrent.Future[ResponseFunction[NHttpResponse]]]
}
