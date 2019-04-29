package chatapp

import org.scalatest.{FeatureSpec, GivenWhenThen}
import rover.rdo.ObjectId

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ChatClientServerTestScenario extends FeatureSpec with GivenWhenThen {

	feature("Client - Server communication") {

		val serverAddress = "http://localhost:8080"
		val client = new ChatAppClient(serverAddress)
		val server = ChatServer.start()

		Await.ready(client.login(ChatUser.Steffan), Duration.Inf)


		scenario("Sending a message from client") {
			Await.ready(client.send("Hello"), Duration.Inf)
			Await.ready(client.send("HOw are you?"), Duration.Inf)
			client.chat.requestSync()

			val state = server.serverImpl.storage(ObjectId.chatAppChat)

			assert(state.immutableState.size == 2)
			println(state.immutableState)
			println(state.log)
		}
		
	}
	
}
