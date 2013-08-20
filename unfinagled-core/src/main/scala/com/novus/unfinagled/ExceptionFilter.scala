package com.novus.unfinagled

import com.twitter.finagle.http.Status
import com.twitter.finagle.{ CancelledRequestException, Service, SimpleFilter }
import com.twitter.logging.Logger
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.{ HttpResponseStatus, DefaultHttpResponse, HttpResponse, HttpRequest }
import unfiltered.util.control.NonFatal

/** General purpose exception filter for raw Netty req / resp.
 *
 *  Uncaught exceptions are converted to 500 Internal Server Error. Cancellations
 *  are converted to 499 Client Closed Request. 499 is an Nginx extension for
 *  exactly this situation, see:
 *   http://trac.nginx.org/nginx/browser/nginx/trunk/src/http/ngx_http_request.h
 */
class ExceptionFilter extends SimpleFilter[HttpRequest, HttpResponse] {
  import ExceptionFilter.ClientClosedRequestStatus

  private val log = Logger("finagle-http")

  def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]): Future[HttpResponse] =
    {
      try {
        service(request)
      }
      catch {
        // apply() threw an exception - convert to Future
        case NonFatal(e) => Future.exception(e)
      }
    } rescue {
      case e: CancelledRequestException =>
        // This only happens when ChannelService cancels a reply.
        log.warning("cancelled request: uri:%s", request.getUri)
        val response = new DefaultHttpResponse(request.getProtocolVersion, ClientClosedRequestStatus)
        Future.value(response)
      case e =>
        try {
          log.warning(e, "exception: uri:%s exception:%s", request.getUri, e)
          val response = new DefaultHttpResponse(request.getProtocolVersion, Status.InternalServerError)
          Future.value(response)
        }
        catch {
          // logging or internals are broken.  Write static string to console -
          // don't attempt to include request or exception.
          case NonFatal(e2) =>
            Console.err.println("ExceptionFilter failed")
            throw e2
        }
    }
}

object ExceptionFilter extends ExceptionFilter {
  private[ExceptionFilter] val ClientClosedRequestStatus =
    new HttpResponseStatus(499, "Client Closed Request")
}
