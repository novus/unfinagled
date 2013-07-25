package com.novus.unfinagled

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import com.twitter.finagle.http.Http
import com.twitter.finagle.ServerCodecConfig
import java.net.InetSocketAddress
import scala.collection.JavaConversions._

class CodecSpec extends WordSpec with ShouldMatchers {

  val conf = ServerCodecConfig("serverConf", new InetSocketAddress(0))
  val ufPipeline = UnfilteredCodec().server(conf).pipelineFactory.getPipeline
  val httpPipeline = Http().server(conf).pipelineFactory.getPipeline

  "An UnfilteredCodec's pipeline" should {

    "contain a single additional handler" in {
      ufPipeline.getNames.size() == httpPipeline.getNames.size() + 1
    }

    "contain the unfiltered handler in the last position" in {
      ufPipeline.getLast == ufPipeline.get(UnfilteredCodec.handlerName)
    }

    "contain the same handlers as finagle http pipeline" in {
      val uf = ufPipeline.toMap
      val http = httpPipeline.toMap
      http.forall {
        case (name, handler) => handler == uf.get(name)
      }
    }
  }

}
