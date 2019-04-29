package chatapp.model

import chatapp.{ChatConflictResolutionMechanism, ChatServer, ChatUser}
import rover.rdo.comms.SyncDecision.SyncDecision
import rover.rdo.comms.http.{ClientForServerOverHttp, ServerHttpEndpointPaths}
import rover.rdo.comms.{Client, SelfSyncingRdo, SyncDecision}
import rover.rdo.conflict.{CommonAncestor, ConflictedState}
import rover.rdo.state.{AtomicObjectState, InitialAtomicObjectState}
import rover.rdo.{ObjectId, RdObject}

import scala.async.Async.async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


// FIXME: ensure messages can be read, but not modified or reassigned...(crypto)
// FIXME: after state & rd object impl change
class Chat(
	    private val client: Client[List[ChatMessage]],
		private val _checkpointedState: AtomicObjectState[List[ChatMessage]]
	)
	extends RdObject[List[ChatMessage]](
		_checkpointedState
	)
	with SelfSyncingRdo[List[ChatMessage]]
{

	type Updater = AtomicObjectState[List[ChatMessage]] => Future[Unit]
	val _onStateModified: Chat#Updater = null

	def send(message: ChatMessage): Future[Unit] = {
		val appendTheMessage = (s: List[ChatMessage]) => s :+ message

		async {
			modifyState(appendTheMessage)
		}
	}

	/* SelfSyncing impl */
	override def onStateModified(oldState: AtomicObjectState[List[ChatMessage]]): Future[Unit] = {
		_onStateModified(state)
	}

	override def beforeSync(currentState: List[ChatMessage]): SyncDecision = {
		return SyncDecision.sync
	}

	override def afterSync(newState: List[ChatMessage]): Unit = {
		// do nothing
	}

	override protected def fetchServerVersion(): AtomicObjectState[List[ChatMessage]] = {
		// TODO: move to SelfSyncing?
//		println("Chat: going to fetch")
		val fetched = client.fetch(this.state.objectId)
		return fetched
	}

	override protected def pushLocalVersion(localVersion: AtomicObjectState[List[ChatMessage]]): Unit = {
		// TODO: move to SelfSyncing?
//		println("Chat: going to push")
		client.push(localVersion)
	}
}

trait DebuggableRdo[A <: Serializable] {
	this: RdObject[A] =>

	val atomicObjectState: AtomicObjectState[A] = {
		this.state
	}
}


object Chat {
	def fromServer(client: Client[List[ChatMessage]], objectId: ObjectId): Chat = {
		val fetched = client.fetch(objectId)
		return new Chat(client, fetched)
	}
	
	def fromCheckpointedState(checkPointedState: AtomicObjectState[List[ChatMessage]]): Chat = {
		val serverAddress = ChatServer.SERVER_ADRESS
		val client = new ClientForServerOverHttp[List[ChatMessage]](ServerHttpEndpointPaths.atServer(serverAddress, "chatapp"))
		new Chat(client, checkPointedState)
	}

	def copyOf(chat: Chat): Chat = {
		new Chat(chat.client, chat.state)
	}

	def initial(): Chat = {
		val client = new ClientForServerOverHttp[List[ChatMessage]](ServerHttpEndpointPaths.atServer(ChatServer.SERVER_ADRESS, "chatapp"))
		new Chat(client, AtomicObjectState.initial(List[ChatMessage]()))
	}

	def initialDubuggable(): Chat with DebuggableRdo[List[ChatMessage]] = {
		val chat = new Chat(null, AtomicObjectState.initial(List[ChatMessage]())) with DebuggableRdo[List[ChatMessage]]
		return chat
	}
}

object test {
	def main(args: Array[String]): Unit ={
		val initialState = new InitialAtomicObjectState[List[ChatMessage]](List(new ChatMessage("Welcome", new ChatUser("system"))))
		val chat = new Chat(null, initialState)
		val THREAD_SLEEP = 1000

		//stage 1: Copying
		val res = chat.send(new ChatMessage("Hey", ChatUser.Giannis))
		Thread.sleep(THREAD_SLEEP)
		val chat2 = Chat.copyOf(chat)

		println("**** Stage 1: Should be equal here ****")
		println(chat.state.immutableState.last.toString)
		println(chat2.state.immutableState.last.toString)

		//staget 2: forking
		chat.send(new ChatMessage("Wassup", ChatUser.Steffan))
		chat.send(new ChatMessage("Yo", ChatUser.Steffan))

		chat2.send(new ChatMessage("YoYo", ChatUser.Steffan))


		Thread.sleep(THREAD_SLEEP)
		println(s"\n\n")
		println("**** Stage 2: Diverged ****")
		println(chat.state.immutableState.last.toString)
		println(chat2.state.immutableState.last.toString)

		val commmonAncestor = CommonAncestor.from(chat, chat2)
		val commonAncestorState = commmonAncestor.state

		println(s"\n\n")
		println("**** Common Ancestor ****")
		println(s"state: ${commonAncestorState.immutableState.last.toString}")


		val resolved = new ChatConflictResolutionMechanism().resolveConflict(ConflictedState.from(chat, chat2))
		println(s"\n\n")
		println("**** Conflict Resolution ****")
		println(resolved.asAtomicObjectState.immutableState.last.toString)
		println(resolved.asAtomicObjectState.log.asList.mkString("\n     "))

	}
}




