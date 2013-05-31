package redisc.api

import redisc.core.protocol._
import redisc.core.tcp._
import akka.actor._
import java.net.InetSocketAddress
import scala.concurrent.{Future, Promise, promise, future}
import akka.pattern.ask
import akka.util.Timeout
import akka.io.PipelineContext
import akka.io.PipelineFactory
import akka.io.PipelinePorts
import scala.collection.immutable.Queue
import com.typesafe.config.ConfigFactory
import akka.io.Tcp.{ Connected, ConnectionClosed}

class Redisc(hostname: String, port: Int = 6379) {

  private val connectionName = (hostname + port.toString) filter { _.toString.matches("[a-zA-Z0-9]") }
  private val redisc = Redisc.system.actorOf(RediscActor(hostname, port), name = connectionName)

  def get(key: String): Future[String] = {
    val p = promise[String]
    redisc ! RedisRequest(RedisMessage("*", IndexedSeq("GET",key)), p)
    p.future.mapTo[String]
  }
  
  def set(key: String, value: String, ex: Option[Int] = None, px: Option[Int] = None, nx: Boolean = false, xx: Boolean = false)
  : Future[String] = {
    val p = promise[String]
    var args = IndexedSeq("SET", key, value)
    ex foreach { t => args ++= IndexedSeq("EX", t.toString) }
    px foreach { t => args ++= IndexedSeq("PX", t.toString) }
    if (nx) args :+= "NX"
    if (xx) args :+= "XX"
    redisc ! RedisRequest(RedisMessage("*", args), p)
    p.future.mapTo[String]
  }

  def auth(password: String): Future[String] = {
    val p = promise[String]
    redisc ! RedisRequest(RedisMessage("*", IndexedSeq("AUTH", password)), p)
    p.future.mapTo[String]
  }

  def close(): Unit = redisc ! UserClose

}

object Redisc {
  val config = ConfigFactory.load()
  val system = ActorSystem("redisc", config.getConfig("redisc").withFallback(config))
  def apply(hostname: String, port: Int) = new Redisc(hostname, port)
}

class RediscActor(hostname: String, port: Int) extends Actor with ActorLogging {
  import context._

  var promiseQueue = Vector.empty[RediscPromise]
  val stage = new RedisProtocolStage()
  val PipelinePorts(cmd, evt, _) = PipelineFactory.buildFunctionTriple(RedisContext(), stage)

  val tcp = actorOf(BufferedTcpClient(new InetSocketAddress(hostname, port), self, evt), name = "tcp")
  watch(tcp)

  def receive = {
    case RedisRequest(message, p) =>
      val (_, data) = cmd(message)
      data foreach { tcp ! ToSend(_, RediscPromise(message.args(0),p)) }

    case Sent(p) =>
      promiseQueue = promiseQueue :+ p

    case Cancelled(rediscPromise) =>
      rediscPromise.p.failure(BufferFull)

    case Connected(_, _) =>
      log.info("Successfully connected to Redis server " + hostname + ":" + port)

    case UserClose =>
      tcp ! TcpClose

    case Terminated(_) =>
      log.info("Connection to " + hostname + ":" + port + " closed.")
      promiseQueue foreach { rediscp => rediscp.p.failure(ConnectionEnded(hostname, port)) }
      context stop self

    case m: RedisMessage =>
      val rediscPromise = promiseQueue(0)
      promiseQueue = promiseQueue drop 1
      handleRedisResponse(rediscPromise, m)
  }

  def handleRedisResponse(redsicp: RediscPromise, m: RedisMessage): Unit = {
    // in the following, asInstanceOf are unsafe due to type erasure, but the resulting futures will be safe 
    // thanks to future.mapTo[T]
    val RedisMessage(kind, args) = m

    if (kind == "+") { // status
      redsicp.p.asInstanceOf[Promise[String]].success(args(0))
    } else if (kind == "$") { // bulk reply
      if (args.length == 0) {
        redsicp.p.failure(RedisNull)
      } else {
        redsicp.p.asInstanceOf[Promise[String]].success(args(0))
      }
    } else if (kind == "-") { // error reply
      redsicp.p.failure(RedisError(args(0)))
    }
  }

}

object RediscActor {
  def apply(hostname: String, port: Int) = Props(classOf[RediscActor], hostname, port)
}


case class RedisRequest(message: RedisMessage, p: Promise[_])
case object UserClose

case object BufferFull extends Exception
case class ConnectionEnded(remoteHostname: String, remotePort: Int) extends Exception
case class RedisError(error: String) extends Exception
case object RedisNull extends Exception

