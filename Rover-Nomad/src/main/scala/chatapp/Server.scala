package chatapp

import chatapp.model.ChatMessage
import rover.rdo.ObjectId
import rover.rdo.comms.{Server, ServerConfiguration}
import rover.rdo.comms.http.{EphemeralServer, ServerHttpInterface}
import rover.rdo.state.AtomicObjectState

// Previous impl:
//class ChatServer extends HTTPServer[List[ChatMessage]](_mapToStates = Map("chat" -> ChatServer.CHAT_STATE)) {
//
//}

class ChatServer(val serverImpl: EphemeralServer[List[ChatMessage]]) extends Server[List[ChatMessage]] {
	
	private val restInterface = new ServerHttpInterface[List[ChatMessage]]("chatapp", 8080, serverImpl)
	
	override def create(): AtomicObjectState[List[ChatMessage]] = {
		return serverImpl.create()
	}
	
	override def get(objectId: ObjectId): Option[AtomicObjectState[List[ChatMessage]]] = {
		return serverImpl.get(objectId)
	}
	
	override def accept(incomingState: AtomicObjectState[List[ChatMessage]]): Unit = {
		serverImpl.accept(incomingState)
	}
	
	override def status(objectId: ObjectId): Unit = {
		serverImpl.status(objectId)
	}
}

object ChatServer {
	//new ChatMessage("test", ChatUser.Steffan))
	private val INITIAL = List[ChatMessage]()

	val SERVER_ADRESS = "http://localhost:8080"
	
	private val startingServerStateStore = {
		val initialAtomicObjectState = AtomicObjectState.initial(INITIAL)
		
		println(s"Created objectId for chat testing: ${initialAtomicObjectState.objectId}")
		// set for testing
		ObjectId.chatAppChat = initialAtomicObjectState.objectId
		
		// return
		Map(initialAtomicObjectState.objectId -> initialAtomicObjectState)
	}
	
	def start(): ChatServer = {
		val serverConfig = new ServerConfiguration[List[ChatMessage]](INITIAL, new ChatConflictResolutionMechanism)
		val serverImpl = new EphemeralServer[List[ChatMessage]](serverConfig, startingServerStateStore)
		val server = new ChatServer(serverImpl)
		
		return server
	}

	def main(args: Array[String]): Unit = {
		start()
		println("Chat application server has started")
	}
}
