package core

import core.communication.{CommunicationFactory, CommunicationLayer}
import core.messages.{BlockMessage, CommitMessage, Message, RequestMessage}

import scala.collection.immutable

class Node(nodeId: String, exes: immutable.Seq[String], orders: immutable.Seq[String]) {
  val id: String = nodeId
  val executors: immutable.Seq[String] = exes
  val orderers: immutable.Seq[String] = orders

  val communication: CommunicationLayer = CommunicationFactory.getCommunicationLayer(this)
  communication.init()
  new Thread(communication).start()

  def handleMessage(msg: Message): Unit = {
    msg.messageType match {
      case core.messages.MessageType.request => onRequestMessage(msg.asInstanceOf[RequestMessage])
      case core.messages.MessageType.new_block => onNewBlockMessage(msg.asInstanceOf[BlockMessage])
      case core.messages.MessageType.commit => onCommitMessage(msg.asInstanceOf[CommitMessage])
    }
  }

  // Children should overwrite these functions if they want to change the behaviour
  def onRequestMessage(msg: RequestMessage): Unit = {}
  def onNewBlockMessage(msg: BlockMessage): Unit = {}
  def onCommitMessage(msg: CommitMessage): Unit = {}
}
