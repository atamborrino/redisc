package redisctest

import org.scalatest.FeatureSpec
import org.scalatest.GivenWhenThen
import redisc.api._
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
 
class RedisSpec extends FeatureSpec with GivenWhenThen {

  val timeout = 1 second

  given("a Redisc client connected to a localhost Redis")
  val client = Redisc("127.0.0.1", 6379)

  feature("AUTH") {
    scenario("auth to redis server") {
      assert(Await.result(client.auth("foo"), timeout) === "OK")
    }
  }

  feature("SET and GET") {

    scenario("set a key value pair and then get it") {
      val f = client.set("key", "value")
      assert(Await.result(f, timeout) === "OK")
      assert(Await.result(client.get("key"), timeout) === "value")
    }

    scenario("get a inexistant key") {
      intercept[RedisNull.type] {
        Await.result(client.get("inexistant"), timeout)
      }
    }

    scenario("set a existant key with option NX") {
      intercept[RedisNull.type] {
        Await.result(client.set("key", "foo", nx = true), timeout)
      }
    }

    scenario("set a key with PX ") {
      assert(Await.result(client.set("keypx", "foo", px = Some(10)), timeout) === "OK")
      Thread.sleep(20)
      intercept[RedisNull.type] {
        Await.result(client.get("keypx"), timeout)
      }
    }

  }

  and("close the connection")
  client.close()

}
