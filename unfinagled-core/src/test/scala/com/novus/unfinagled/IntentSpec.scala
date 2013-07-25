package com.novus.unfinagled

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.ShouldMatchers
import unfiltered.response._
import unfiltered.request._
import dispatch.classic.Handler

class IntentSpec extends Served with GivenWhenThen with ShouldMatchers {

  def intent = {
    case GET(Path("/foobar")) => ResponseString("baz")
  }

  feature("Unfiltered front end") {
    scenario("GET foobar") {
      http(host / "foobar" as_str) should be("baz")
    }

    scenario("POST foobar") {
      withHttp { _.x(Handler((host / "foobar").POST, status)) should be(404) }
    }
  }

}