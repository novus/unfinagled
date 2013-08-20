package com.novus.unfinagled

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers
import unfiltered.response._
import unfiltered.request._
import com.twitter.util.Future
import dispatch.classic.StatusCode

class TwitterFutureIntentSpec extends FinagleServed[TwitterFuturePlan.Intent] with GivenWhenThen with ShouldMatchers {

  def service = UnfilteredService(intent)

  def intent = {
    case GET(Path("/ping")) => Future {
      ResponseString(http(host / "ping-req" as_str))
    }
    case GET(Path("/ping-req")) => Future(ResponseString("pong"))
    case GET(Path("/error"))    => Future.exception(new RuntimeException("foo"))
  }

  feature("Twitter futures front-end") {
    scenario("GET ping") {
      http(host / "ping" as_str) should be("pong")
    }
    scenario("Server error") {
      intercept[StatusCode] {
        http(host / "error" as_str)
      }.code should be(500)
    }
  }

}