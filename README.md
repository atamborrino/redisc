# Redisc

Redisc is a non-blocking Redis client based on new Akka IO 2.2. Redisc is:
*  simple and high-performance
*  fully non-blocking
*  type-safe
*  taking advantages of multi-core CPUs in a transparent way (thanks to Akka)

Example:
```scala
import redisc.api._

val client = Redisc("127.0.0.1", 6379)

//GET
val futureValue = client.get("key") // Future[String]

// SET with NX option
val futureStatus = client.set("key", "value", nx = true) // Future[String]
```

That's it. You can see below what are the possible exceptions that can come into the futures:
```scala
val futureMsgToPrint = futureValue map { "We got this value: " + _ } recover {
  // all possible exception are listed below
  case RedisNull => "No value was associated with the key"
  case RedisError(err) => "We got this Redis error: " + err
  case ConnectionEnded(hostname, port) => "The connection with " + hostname + " " + port + "has ended."
  case BufferFull => "You reached the 200 MB buffer of redisc. I think there had been a problem."
  case _ => "Oh oh, unexpected exception, it is very probably a bug in redisc..."
}

val anotherFutureMsg = futureStatus map {
  case "OK" => "The key-value has been correctly set" // "OK" is the Redis status "OK"
} recover {
  case RedisNull => "The set operation has not been performed because the key already exists (NX option)"
  case _ => "RedisError or ConnectionEnded or BufferFull..."
}
```

## Installation
No hosted Maven repository for now, so you have to clone this repo and do a ```sbt publish-local``` to publish it in your
local Ivy repository).

### Play Framework
For usage in combination with Play2.0, you have to use a Play2.0 version compiled against Akka 2.2, until Akka 2.2 integration is pushed into mainstream, you can find a version at: https://github.com/gideondk/Play2.0.

## API
Refer to the Redisc class in the [Scala doc](http://atamborrino.github.io/redisc/target/scala-2.10/api/#redisc.api.Redisc). 
Note that Redisc API always follows the [Redis command API](http://redis.io/commands) (for example, for every Redis command that results in a nil response (a NULL bulk reply), the resulting future will always be filled with a RedisNull exception).
Therefore you can know what the resulting Future of a command can contain by looking at the Redis command API.

## Status
You can see what Redis commands and functionalities are implemented in the Scala doc](http://atamborrino.github.io/redisc/target/scala-2.10/api/#redisc.api.Redisc).

###TODO:
*   More Redis commands
*   Pipelining
*   Master-slave replication support
*   Publish-subscribe

The code is modular and quite simple, so contributors and contributions are very welcomed!

## Performance test
I set up an instance of Play 2.2 snapshot (upgraded with Akka 2.2-M3) on localhost accessing a Nano Redis instance hosted at RedisToGo. Each HTTP GET request on '/' involves a 'GET key' request on Redis. I used Redisc and then Jedis (main Java Redis client) with a JedisPool (Jedis comes with his own thread-pool when used in multi-threaded environment).

```ab -n 5000 -c 20 localhost:9000/``` was served 2.4x faster (requests/s) with Redisc than with Jedis.
