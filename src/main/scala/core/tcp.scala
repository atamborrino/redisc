package redisc.core.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import akka.util.ByteStringBuilder
import akka.actor.ActorLogging
import scala.collection.immutable.Vector
import scala.concurrent.Promise

import redisc.core.protocol._

case class RediscPromise(command: String, p: Promise[_])
case class ToSend(data: ByteString, redisPromise: RediscPromise)
case class Sent(redisPromise: RediscPromise)
case class Cancelled(redisPromise: RediscPromise)
case object TcpClose

object BufferedTcpClient {
  def apply(remote: InetSocketAddress, replies: ActorRef, 
      redisEvt: ByteString => (Iterable[RedisMessage], Iterable[ByteString])) =
    Props(classOf[BufferedTcpClient], remote, replies, redisEvt)
}
 
class BufferedTcpClient(remote: InetSocketAddress, listener: ActorRef, 
    redisEvt: ByteString => (Iterable[RedisMessage], Iterable[ByteString])) extends Actor with ActorLogging {
 
  import Tcp._
  import context.system

  case object Ack extends Event
 
  IO(Tcp) ! Connect(remote)
 
  // io
  def receive = {
    case CommandFailed(c: Connect) =>
      log.error("Connection failed to " + c.remoteAddress.getHostName + ":" + c.remoteAddress.getPort)
      context stop self

    case toSend: ToSend =>
      buffer(toSend)
 
    case c @ Connected(remote, local) =>
      listener ! c
      val connection = sender
      connection ! Register(self)

      def writeBehaviour: Actor.Receive  = {
        case Received(data) => handleEvt(data)
        case TcpClose => connection ! Close
        case _: ConnectionClosed => context stop self
        case toSend @ ToSend(data, _) => 
          buffer(toSend)
          connection ! Write(data, Ack)
          context.become(ackBehaviour, discardOld = false)
      }

      def ackBehaviour: Actor.Receive = {
          case toSend: ToSend  => buffer(toSend)
          case Ack => acknowledge(connection)
          case Received(data) => handleEvt(data)
          case _: ConnectionClosed => context stop self
          case TcpClose => connection ! Close
      } 

      if (!storage.isEmpty) {
        context.become(writeBehaviour)
        connection ! Write(storage(0).data, Ack)
        context.become(ackBehaviour, discardOld = false)
      } else {
        context.become(writeBehaviour)        
      }

  }

  private def handleEvt(data: ByteString): Unit = {
    log.debug("Received data chunk: " + data.utf8String + ".")
    val (messages, _) = redisEvt(data)
    messages foreach { listener ! _ }
  }

  // buffering
  var storage = Vector.empty[ToSend]
  var stored = 0L
  val maxStored = 200000000L

  private def buffer(w: ToSend): Unit = {
    storage :+= w
    stored += w.data.size
   
    if (stored > maxStored) {
      log.warning("buffer overrun. Cancelling command")
      listener ! Cancelled(w.redisPromise)
    }
  }
   
  private def acknowledge(connection: ActorRef): Unit = {
    require(storage.nonEmpty, "storage was empty")
    val sent = storage(0)
    val size = sent.data.size
    stored -= size
    storage = storage drop 1
    
    listener ! Sent(sent.redisPromise)
    
    if (storage.isEmpty) {
      context.unbecome()
    } else {
      connection ! Write(storage(0).data, Ack)
    }
  }

}


