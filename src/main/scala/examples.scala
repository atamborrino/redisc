import redisc.api._
import concurrent.ExecutionContext.Implicits.global

object Main extends Application {

	val client = Redisc("127.0.0.1", 6379)

	//GET
	val futureValue = client.get("key") // Future[String]

	// SET with NX option
	val futureStatus = client.set("key", "value", nx = true) // Future[String]


	val futureMsgToPrint = futureValue map { "We got this value: " + _ } recover {
		// all possible exception are listed below
		case RedisNull => "No value was associated with the key"
		case RedisError(err) => "Ouch, we got this Redis error: " + err
		case ConnectionEnded(hostname, port) => "The connection with " + hostname + " " + port + "has ended."
		case BufferFull => "You reached the 200 MB buffer of redisc. I think there had been a problem."
		case _ => "Oh oh, unexpected exception, it is very probably a bug in redisc..."
	}

	val anotherFutureMsg = futureStatus map {
		case "OK" => "The key-value has been correctly set" // "OK" is the Redis OK status
	} recover {
		case RedisNull => "The set operation has not been performed because the key already exists (NX option)"
		case _ => "RedisError or ConnectionEnded or BufferFull..."
	}

	futureMsgToPrint foreach (println(_))
	anotherFutureMsg foreach (println(_))

}