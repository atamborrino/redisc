package redisctest

import org.scalatest.FunSpec
import redisc.core.protocol._
import akka.io._
import akka.util.ByteString

class RedisProtocolSpec extends FunSpec {
  describe("transform a tcp stream into RedisMessages") {
    
    it("should correctly transform bulk rplies ") {
      val stage = new RedisProtocolStage()
      val PipelinePorts(cmd, evt, _) = PipelineFactory.buildFunctionTriple(RedisContext(), stage)

      val resp1 = "$3\r\nfoo\r\n"
      val resp2 = "$3\r\nbar\r\n"
      val b1 = ByteString(resp1.slice(0, 4), "UTF-8")
      val b2 = ByteString(resp1.slice(4, 6), "UTF-8")
      val b3 = ByteString(resp1.slice(6, resp1.length) + resp2.slice(0,1), "UTF-8")
      val b4 = ByteString(resp2.slice(1, 8), "UTF-8")
      val b5 = ByteString(resp2.slice(8, resp2.length), "UTF-8")

      val (mess1, _) = evt(b1)
      assert(mess1.toList.isEmpty)
      val (mess2, _) = evt(b2)
      assert(mess2.toList.isEmpty)
      val (mess3, _) = evt(b3)
      assert(mess3.head === RedisMessage("$", IndexedSeq("foo")))
      val (mess4, _) = evt(b4)
      assert(mess4.toList.isEmpty)
      val (mess5, _) = evt(b5)
      assert(mess5.head === RedisMessage("$", IndexedSeq("bar")))
    }

    it("should correctly transform status replies") {
      val stage = new RedisProtocolStage()
      val PipelinePorts(cmd, evt, _) = PipelineFactory.buildFunctionTriple(RedisContext(), stage)

      val resp = "+OK\r\n" + "+OK\r\n"
      val (messages, _) = evt(ByteString(resp, "UTF-8"))
      assert(messages.toList.length === 2)
      messages.toList.foreach { mess =>
        assert(mess === RedisMessage("+",IndexedSeq("OK")))
      }
    }

   }


}