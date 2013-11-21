package com.novus.unfinagled

import java.lang.Iterable
import java.util.{ List, Set }
import java.util.Map.Entry
import org.jboss.netty.handler.codec.http.{ HttpMessage, HttpMethod, HttpVersion, HttpRequest }
import org.jboss.netty.buffer.ChannelBuffer
import unfiltered.netty.RequestBinding

/** A request wrapper that captures an `unfiltered.netty.RequestBinding` while being an instance of
 *  `org.jboss.netty.handler.codec.http.HttpRequest`, delegating calls to the binding's underlying request.
 *  This perhaps unfortunate code is merited in that it allows an unfiltered codec to function with stock netty channel
 *  handlers expecting an `org.jboss.netty.handler.codec.http.HttpRequest`.
 */
class RequestAdapter private (val binding: RequestBinding) extends HttpRequest {
  val underlying = binding.underlying.request
  override def headers() = underlying.headers()
  override def getHeader(name: String): String = underlying.getHeader(name)
  override def getHeaders(name: String): List[String] = underlying.getHeaders(name)
  override def getHeaders: List[Entry[String, String]] = underlying.getHeaders
  override def containsHeader(name: String): Boolean = underlying.containsHeader(name)
  override def getHeaderNames: Set[String] = underlying.getHeaderNames
  override def getProtocolVersion: HttpVersion = underlying.getProtocolVersion
  override def setProtocolVersion(version: HttpVersion) { underlying.setProtocolVersion(version) }
  override def getContent: ChannelBuffer = underlying.getContent
  override def setContent(content: ChannelBuffer) { underlying.setContent(content) }
  override def addHeader(name: String, value: Any) { underlying.addHeader(name, value) }
  override def setHeader(name: String, value: Any) { underlying.setHeader(name, value) }
  override def setHeader(name: String, values: Iterable[_]) { underlying.setHeader(name, values) }
  override def removeHeader(name: String) { underlying.removeHeader(name) }
  override def clearHeaders() { underlying.clearHeaders() }
  override def isChunked: Boolean = underlying.isChunked
  override def setChunked(chunked: Boolean) { underlying.setChunked(chunked) }
  override def getMethod: HttpMethod = underlying.getMethod
  override def setMethod(method: HttpMethod) { underlying.setMethod(method) }
  override def getUri: String = underlying.getUri
  override def setUri(uri: String) { underlying.setUri(uri) }
}

object RequestAdapter {
  def apply(binding: RequestBinding) = new RequestAdapter(binding)
}