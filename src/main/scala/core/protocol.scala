package redisc.core.protocol

import akka.io._
import akka.util.ByteString
import scala.annotation.tailrec

case class RedisMessage(kind: String, args: IndexedSeq[String])
case class RedisContext extends PipelineContext

class RedisProtocolStage extends SymmetricPipelineStage[PipelineContext, RedisMessage, ByteString] { 
  override def apply(ctx: PipelineContext) = new SymmetricPipePair[RedisMessage, ByteString] {

      var buffer = None: Option[String]
      val delim = "\r\n"
  
      @tailrec
      def extractEvents(s: String, acc: List[RedisMessage]): (Option[String], List[RedisMessage]) = {
        if (s.isEmpty) {
          (None, acc)
        } else if (s(0) == '+') { // status reply
          s.drop(1).split(delim, 2).toList match {
            case evt :: Nil =>
              (Some(s), acc)
            case evt :: rest :: Nil =>
              extractEvents(rest, RedisMessage("+", IndexedSeq(evt)) :: acc)
          }
        } else if (s(0) == '$') { // bulk reply
          s.drop(1).split(delim, 2).toList match {
            case len :: Nil =>
              (Some(s), acc)
            case len :: reply :: Nil =>
              if (len == "-1") {
                val rest = reply
                extractEvents(rest, RedisMessage("$", IndexedSeq()) :: acc)
              } else if (reply.length >= (len.toInt + delim.length)) {
                val (valueWithDelim, rest) = reply.splitAt(len.toInt + delim.length)
                val value = valueWithDelim.dropRight(2)
                extractEvents(rest, RedisMessage("$", IndexedSeq(value)) :: acc)
              } else {
                (Some(s), acc)
              }
          }
        } else if (s(0) == '-') { // error reply
          s.drop(1).split(delim, 2).toList match {
            case evt :: Nil =>
              (Some(s), acc)
            case evt :: rest :: Nil =>
              extractEvents(rest, RedisMessage("-", IndexedSeq(evt)) :: acc)
        }
        } else {
          (None, acc)
        }
      }
  
      override def commandPipeline = { mess: RedisMessage =>
        val s = new StringBuilder()
        if (mess.kind == "*") {
          s.append("*" + mess.args.length + delim)
          mess.args.foreach { arg =>
            s.append("$" + arg.length + delim + arg + delim)
          }
          ctx.singleCommand(ByteString(s.toString,"UTF-8"))
        } else {
          ctx.nothing
        }
      }
       
      override def eventPipeline = { bs: ByteString =>
        val s = bs.utf8String
        val data = if (buffer.isEmpty) s else buffer.get ++ s
        val (rest, redisMessages) = extractEvents(data, Nil)
        buffer = rest

        redisMessages match {
          case Nil        => Nil
          case one :: Nil => ctx.singleEvent(one)
          case many       => many.reverse map (Left(_))
        }
      }
    }
}