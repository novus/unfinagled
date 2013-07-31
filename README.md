unfinagled
==========

Unfinagled is an experimental module for using [unfiltered](https://github.com/unfiltered/unfiltered) as a
[finagle](https://github.com/twitter/finagle) frontend.

Finagle is oriented around the idea of [services](http://twitter.github.io/finagle/guide/ServicesAndFilters.html#services)
which, as it turns out, are not unlike unfiltered [intents](http://unfiltered.databinder.net/Plans+and+Intents.html). If we
treat Finagle as another server runtime, the strategy becomes the transformation of an unfiltered intent into a Finagle
service.

### Modules

Unfinagled is composed a few modules named similarly to those found in Unfiltered.

#### unfinagled-core

Unfiltered focuses on the processing of HTTP requests and abstracting away their representation; this module is the same.
If you have written portable intents, ie, no extractors reach into the request's ``underlying`` member or explicitly
require a specific representation, then running these within Finagle will be a matter of changing the referenced intent
type, at most. This module does use unfiltered's provided ``unfiltered-netty`` module for its internal representation
and processing, so if you currently use this, nothing changes.

Consider this intent:

    object FooIntent {
      def intent: unfiltered.netty.cycle.Plan.Intent = {
        case GET(Path("/")) => ResponseString("Hi")
        case GET(Path("/foobar")) => ResponseString("GOT foobar!")
      }
    }

Unfinagled provides a function to transform such an intent into a Finagle service: ``UnfilteredService``. It looks like
this:

    val ufService = UnfilteredService(FooIntent.intent)

With this in hand, we can now create a Finagle server using unfiltered intents as the request processors:

    val server: Server =
      ServerBuilder()
        .bindTo(address)
        .name("UnfilteredHttpServer")
        .codec(UnfilteredCodec())
        .build(ufService)

#### unfinagled-server

The server module provides conveniences for building and running intents as Finagle services. If you prefer to use
Finagle's ``ServerBuilder`` directly, you have no need of this module. What you get from this module is a slightly more
streamlined construction process, a familiar Unfiltered feel, and an implementation of Unfiltered's ``RunnableServer``.
This is great for development within sbt as you can easily start and stop your server without killing the sbt session.
Usage looks like this:

    Http("myservice", 8080)
      .service(UnfilteredService(intent))
      .start()

#### unfinagled-scalatest

This is a module facilitating integration or black-box testing within ScalaTest. It implements Unfiltered's ``Hosted``
trait so you can write tests for Unfinagled just like you would with Unfiltered on jetty or netty. An example:

    class IntentSpec extends Served with GivenWhenThen with ShouldMatchers {

      def intent = {
        case GET(Path("/foobar")) => ResponseString("baz")
      }

      feature("Unfiltered front end") {
        scenario("GET foobar") {
          http(host / "foobar" as_str) should be("baz")
        }
      }
    }

You provide the intent and you're given a server for each test.
