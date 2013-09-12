package com.novus.unfinagled

import unfiltered.request.HttpRequest
import unfiltered.netty.ReceivedMessage
import unfiltered.response.ResponseFunction
import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.http.service.RoutingService
import com.twitter.util.Future

object TwitterFuturePlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], Future[ResponseFunction[HttpResponse]]]
}
