unfinagled
==========

Unfinagled is an experimental module for using [unfiltered](https://github.com/unfiltered/unfiltered) as a
[finagle](https://github.com/twitter/finagle) frontend.

Finagle is oriented around the idea of [services](http://twitter.github.io/finagle/guide/ServicesAndFilters.html#services)
which, as it turns out, are not unlike unfiltered [intents](http://unfiltered.databinder.net/Plans+and+Intents.html). If we
treat finagle as another server runtime, the strategy becomes the transformation of an unfiltered intent into a finagle
service.

### Portable, Naturally

Unfiltered focuses on the processing of HTTP requests and abstracting away their representation; this module is the same.
If you have written portable intents, ie, no extractors reach into the requests's ``underlying`` member or explicitly
require a specific representation, then running these within finagle will be a matter of changing the referenced intent
type, at most. This module does use unfiltered's provided ``unfiltered-netty`` module for its internal representation
and processing, so if you currrently use this, nothing changes.

Consider this intent:

    object FooIntent {
      def intent: unfiltered.netty.cycle.Plan.Intent = {
        case GET(Path("/")) => ResponseString("Hi")
        case GET(Path("/foobar")) => ResponseString("GOT foobar!")
      }
    }
    
Unfinagled provides a function to transform such an intent into a finagle service: ``UnfilteredService``. It looks like
this:

    val ufService = UnfilteredService(FooIntent.intent)
    
With this in hand, we can now create a finagle server using unfiltered intents as the request processors:

    val server: Server =
      ServerBuilder()
        .bindTo(address)
        .name("UnfilteredHttpServer")
        .codec(UnfilteredCodec())
        .build(ufService)
        
