package com.novus.unfinagled

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers
import unfiltered.response._
import unfiltered.request._
import dispatch.classic.Handler
import com.twitter.util.Future

class TwitterFutureIntentSpec extends FinagleServed[TwitterFuturePlan.Intent] with GivenWhenThen with ShouldMatchers {

  def service = UnfilteredService.twitterFuture(intent)

  def intent = {
    case GET(Path("/ping")) => Future {
      ResponseString(http(host / "ping-req" as_str))
    }
    case GET(Path("/ping-req")) => Future(ResponseString("pong"))
  }

  feature("Twitter futures front-end") {
    scenario("GET ping") {
      http(host / "ping" as_str) should be("pong")
    }
  }

}