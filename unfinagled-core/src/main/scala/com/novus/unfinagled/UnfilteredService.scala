package com.novus.unfinagled

import com.twitter.finagle.Service
import com.twitter.util.{ FuturePool, Future }
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import unfiltered.response.{ Connection, ContentLength, NotFound, Responder, HttpResponse => UHttpResponse }
import unfiltered.netty.ResponseBinding

/** Functions for constructing finagle Services from unfiltered types.
 */
object UnfilteredService {

  @deprecated("Do not use this. If needed, construct one.", "0.2.0")
  val nettyResponse: HttpResponseStatus => HttpResponse =
    new DefaultHttpResponse(HTTP_1_1, _)

  /** Creates a Finagle wrapper service around a Unfiltered synchronous / cycle intent.
   *
   *  @param intent the intent to wrap
   *  @param futurePool the pool on which the intent will be applied.
   *                   The default is the an immediate pool that assumes the intent is non-blocking and thus
   *                   can be executed on the current I/O worker thread.
   *  @return the Finagle service wrapper
   */
  def apply(intent: unfiltered.netty.cycle.Plan.Intent, futurePool: FuturePool = FuturePool.immediatePool) =
    new Service[HttpRequest, HttpResponse] {
      def apply(request: HttpRequest): Future[HttpResponse] =
        request match {
          case uf: RequestAdapter =>
            futurePool {
              val rf = intent.lift(uf.binding).getOrElse(NotFound)
              val nres = new ResponseBinding(response(request, OK))
              (rf ~> keepAlive(request))(nres).underlying
            }
          case _ =>
            // TODO should probably log this as it indicates a problem with handler ordering in the pipeline.
            Future(response(request, INTERNAL_SERVER_ERROR))
        }
    }

  private final def response(request: HttpRequest, status: HttpResponseStatus): HttpResponse =
    new DefaultHttpResponse(request.getProtocolVersion, status)

  private final def keepAlive(request: HttpRequest) = new Responder[HttpResponse] {
    def respond(res: UHttpResponse[HttpResponse]) {
      res.outputStream.close()
      (if (HttpHeaders.isKeepAlive(request))
        Connection("Keep-Alive") ~>
        ContentLength(
          res.underlying.getContent().readableBytes().toString)
      else unfiltered.response.Connection("close"))(res)
    }
  }

}