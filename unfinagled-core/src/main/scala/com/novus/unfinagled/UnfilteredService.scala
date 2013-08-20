package com.novus.unfinagled

import com.twitter.finagle.Service
import com.twitter.util.{ Promise, FuturePool, Future }
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import unfiltered.response.{ HttpResponse => UHttpResponse, _ }
import unfiltered.netty.{ ReceivedMessage, ResponseBinding }
import scala.concurrent.ExecutionContext

/** Functions for constructing finagle Services from unfiltered types.
 */
object UnfilteredService {
  type HttpService = Service[HttpRequest, HttpResponse]

  @deprecated("Do not use this. If needed, construct one.", "0.2.0")
  val nettyResponse: HttpResponseStatus => HttpResponse =
    new DefaultHttpResponse(HTTP_1_1, _)

  /** Creates a Finagle wrapper service around a Twitter Future returning intent.
   *
   *  @param intent the intent to wrap
   *  @return the Finagle service wrapper
   */
  def apply(intent: TwitterFuturePlan.Intent): HttpService =
    new UnfilteredService(intent)

  /** Creates a Finagle wrapper service around an Unfiltered synchronous / cycle intent.
   *
   *  @param intent the intent to wrap
   *  @param futurePool the pool on which the intent will be applied.
   *                   The default is the an immediate pool that assumes the intent is non-blocking and thus
   *                   can be executed on the current I/O worker thread.
   *  @return the Finagle service wrapper
   */
  def apply(intent: unfiltered.netty.cycle.Plan.Intent, futurePool: FuturePool = FuturePool.immediatePool): HttpService = {
    val futureIntent: TwitterFuturePlan.Intent = {
      case x if intent.isDefinedAt(x) => futurePool(intent.apply(x))
    }
    apply(futureIntent)
  }

  /** Creates a Finagle wrapper service around a Scala Future returning intent.
   *
   *  @param intent the intent to wrap
   *  @param ec the execution context to handle future processing with
   *  @return the Finagle service wrapper
   */
  def apply(intent: FuturePlan.Intent)(implicit ec: ExecutionContext): HttpService =
    apply {
      // Convert the Scala future Intent into a Twitter Future one
      intent.andThen { scFuture =>
        val twResult = Promise[ResponseFunction[HttpResponse]]()
        scFuture.onComplete {
          case scala.util.Success(resp) => twResult.setValue(resp)
          case scala.util.Failure(t)    => twResult.setException(t)
        }
        twResult
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

class UnfilteredService(intent: TwitterFuturePlan.Intent) extends Service[HttpRequest, HttpResponse] {
  import UnfilteredService._

  def apply(request: HttpRequest): Future[HttpResponse] = request match {
    case uf: RequestAdapter =>
      val responder: Future[ResponseFunction[HttpResponse]] =
        intent.andThen(_.map(_ ~> keepAlive(request))).applyOrElse(uf.binding, notFound)

      responder.map(_.apply(new ResponseBinding(response(request, OK))).underlying)
  }

  private def notFound(req: unfiltered.request.HttpRequest[ReceivedMessage]): com.twitter.util.Future[ResponseFunction[HttpResponse]] = Future(NotFound)
}

