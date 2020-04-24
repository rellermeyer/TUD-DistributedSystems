package core.communication

import core.communication.RPC.RPCCommunication
import core.{Config, Node}
import core.communication.thread.{ThreadCommunication}

object CommunicationType extends Enumeration {
  type CommunicationType = Value
  val thread, rpc = Value
}

object CommunicationFactory {
  def getCommunicationLayer(owner: Node): CommunicationLayer = {
    Config.communicationType match {
      case CommunicationType.thread => new ThreadCommunication(owner)
      case CommunicationType.rpc => new RPCCommunication(owner)
    }
  }
}
