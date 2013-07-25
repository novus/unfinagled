package com.novus.unfinagled

import com.twitter.finagle.Service
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import unfiltered.response.{ Connection, ContentLength, NotFound, Responder, HttpResponse => UHttpResponse }
import unfiltered.netty.ResponseBinding
import unfiltered.netty.cycle.Plan.Intent

/** Functions for constructing finagle Services from unfiltered types.
 */
object UnfilteredService {

  val nettyResponse: HttpResponseStatus => HttpResponse =
    new DefaultHttpResponse(HTTP_1_1, _)

  def apply(intent: Intent) =
    new Service[HttpRequest, HttpResponse] {
      def apply(request: HttpRequest): Future[HttpResponse] =
        request match {
          case uf: RequestAdapter =>
            Future {
              val rf = intent.lift(uf.binding).getOrElse(NotFound)
              val nres = new ResponseBinding(nettyResponse(OK))
              val keepAlive = HttpHeaders.isKeepAlive(request)
              val closer = new Responder[HttpResponse] {
                def respond(res: UHttpResponse[HttpResponse]) {
                  res.outputStream.close()
                  (if (keepAlive)
                    Connection("Keep-Alive") ~>
                    ContentLength(
                      res.underlying.getContent().readableBytes().toString)
                  else unfiltered.response.Connection("close"))(res)
                }
              }
              (rf ~> closer)(nres).underlying
            }
          case _ =>
            // TODO should probably log this as it indicates a problem with handler ordering in the pipeline.
            Future(nettyResponse(INTERNAL_SERVER_ERROR))
        }
    }
}