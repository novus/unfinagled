package com.novus.unfinagled

import com.twitter.util.StorageUnit
import com.twitter.finagle.http.codec.ChannelBufferUsageTracker
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Http
import com.twitter.finagle.{ Codec, CodecFactory }
import org.jboss.netty.handler.codec.http.{ HttpResponse, HttpRequest }
import org.jboss.netty.channel._
import unfiltered.netty.{ RequestBinding, ReceivedMessage }

/** The unfiltered http codec.
 *
 *  The unfiltered codec is one that functions exactly as the provided `com.twitter.finagle.http.Http` codec but augments
 *  the netty pipeline with a new handler. This handler ensures that the request is compatible with both the stock
 *  http channel handlers and the `unfiltered-netty` module by transforming request representation propagated through
 *  the message event. Specifically, the `org.jboss.netty.handler.codec.http.HttpRequest` is taken from the event,
 *  transformed into a `com.novus.unfinagled.RequestAdapter` and placed in a new upstream event.
 *  It is this new event that is sent upstream.
 */
object UnfilteredCodec {

  val handlerName = "unfilteredRequestEncoder"

  def apply(
    _compressionLevel: Int = 0,
    _maxRequestSize: StorageUnit = 1.megabyte,
    _maxResponseSize: StorageUnit = 1.megabyte,
    _decompressionEnabled: Boolean = true,
    _channelBufferUsageTracker: Option[ChannelBufferUsageTracker] = None,
    _annotateCipherHeader: Option[String] = None,
    _enableTracing: Boolean = false,
    _maxInitialLineLength: StorageUnit = 4096.bytes,
    _maxHeaderSize: StorageUnit = 8192.bytes): CodecFactory[HttpRequest, HttpResponse] =
    new CodecFactory[HttpRequest, HttpResponse] {

      /*
       * From here on, all we care about is replicating the exact behavior of the http server codec as if it were
       * produced by finagle. Assuming relative API stability, this reduces the maintenance costs of unfiltered
       * integration as we simply piggyback on their construction, however it evolves.
       */

      lazy val underlying =
        Http(
          _compressionLevel,
          _maxRequestSize,
          _maxResponseSize,
          _decompressionEnabled,
          _channelBufferUsageTracker,
          _annotateCipherHeader,
          _enableTracing,
          _maxHeaderSize)

      override def client = underlying.client

      override def server = { config =>
        new Codec[HttpRequest, HttpResponse] {
          lazy val serverCodec = underlying.server(config)
          override def pipelineFactory: ChannelPipelineFactory = new ChannelPipelineFactory {
            override def getPipeline: ChannelPipeline = {
              val pipeline = serverCodec.pipelineFactory.getPipeline

              pipeline.addLast(handlerName, new SimpleChannelUpstreamHandler {
                override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
                  val req = e.getMessage.asInstanceOf[HttpRequest]
                  val rich = ReceivedMessage(req, ctx, e)
                  val binding = new RequestBinding(rich)
                  val adapter = RequestAdapter(binding)
                  val event = new UpstreamMessageEvent(ctx.getChannel, adapter, e.getRemoteAddress)
                  ctx.sendUpstream(event)
                }
              })

              pipeline
            }
          }
        }
      }
    }
}
