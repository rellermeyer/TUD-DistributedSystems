package chatapp

import chatapp.model.{Chat, ChatMessage}
import chatapp.ui.REPL
import rover.rdo.ObjectId
import rover.rdo.comms.http.{ClientForServerOverHttp, ServerHttpEndpointPaths}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

// TODO: Rover-Credentials should contain the ObjectId of object user has access to, for now ObjectId.from suffices

class ChatAppClient(serverAddress: String)  {
	
	var user: ChatUser = null
	var chat: Chat = null;
	
	val printer = (string: String) => {
		val cls = s"${string.split("\n").map(c => s"${REPL.UP}${REPL.ERASE_LINE_BEFORE}${REPL.ERASE_LINE_AFTER}").mkString("")}"
		// Prepend two spaces to match input indentation of "> "
		val text = string.split("\n").map(line => s"  $line").mkString("\n")

		s"${REPL.SAVE_CURSOR}$cls\r$text${REPL.RESTORE_CURSOR}"
	}

	// TODO: This is hacky, figure out a better way to do this
	val updater: Chat#Updater = state => async {
		val text = state.immutableState.takeRight(ChatAppClient.SIZE).map(m => m.toString()).mkString("\n")
		print(s"${printer(text)}")
	}

	def login(user: ChatUser): Future[Unit] = {
		async {
			this.user = user

			val client = new ClientForServerOverHttp[List[ChatMessage]](ServerHttpEndpointPaths.atServer(serverAddress, "chatapp"))
			chat = Chat.fromServer(client, ObjectId.chatAppChat)

//			println(s"Initial state: ${chat.state}")
		}
	}

	def send(message: String): Future[Unit] = {
//		println(s"Sending message with intial state: ${chat.state}")
		async {
			await(
				chat.send(new ChatMessage(message, user))
			)
			
			chat.requestSync()
		}
	}

	def updateLoop(): Future[Unit] = {
		async {
			while(true) {
//				println(s"Updating state from update loop...")
				chat.requestSync()
//				println(s"Got updated state: $state")

//				println(s"Rendering chat state: ${chat.state}")
				updater(chat.state) // Force re-render
				Thread.sleep(ChatAppClient.UPDATE_DELAY_MS)
			}
		}
	}

	def render(): Future[Unit] = {
		println(s"  Welcome to Rover Chat! Connected to: $serverAddress")
		print((1 to ChatAppClient.SIZE).map(i => "\n").mkString(""))

		val reader = () => {
			print("> ")
			val s = scala.io.StdIn.readLine()
			s
		}
		val executor = (input: String) => {
		async {
			val p = send(input)
			await(p)
				// This clears the input line
				print(s"${REPL.UP}${REPL.ERASE_LINE_AFTER}")
				chat.state.immutableState.takeRight(ChatAppClient.SIZE).map(m => s"${m.toString()}").mkString("\n")
			}
		}
		val repl: REPL[String] = new REPL(reader, executor, printer)
//		Await.result(repl.loop(), Duration.Inf)
		updateLoop() // TODO: Memory leak
		repl.loop()
	}

}

object ChatAppClient {
	val SIZE = 10
	val UPDATE_DELAY_MS = 2000

	def main(args: Array[String]): Unit = {
		val serverAddress = "http://localhost:8080"
		val client = new ChatAppClient(serverAddress)
		val f = async {
			await(client.login(ChatUser.Steffan))
			await(client.render())
		}

		Await.result(f, Duration.Inf)
	}
}

object Bot {
		def main(args: Array[String]): Unit = {
			val serverAddress = "http://localhost:8080"
			val client = new ChatAppClient(serverAddress)
			val f = async {
				await(client.login(ChatUser.Giannis))
				client.updateLoop() // TODO: Memory leak

		// Simulate conversation
				Thread.sleep(3000)
				await(client.send("Hey man!"))
	
				Thread.sleep(3000)
				await(client.send("How's it going?"))
	
				Thread.sleep(10000)
				await(client.send("Yea man I'm good"))
		}

		Await.result(f, Duration.Inf)
	}
}