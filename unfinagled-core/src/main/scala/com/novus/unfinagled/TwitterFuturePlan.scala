package com.novus.unfinagled

import unfiltered.request.HttpRequest
import unfiltered.netty.ReceivedMessage
import unfiltered.response.ResponseFunction
import org.jboss.netty.handler.codec.http.{ HttpResponse => NHttpResponse }

object TwitterFuturePlan {
  type Intent = PartialFunction[HttpRequest[ReceivedMessage], com.twitter.util.Future[ResponseFunction[NHttpResponse]]]
}
