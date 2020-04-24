package core.messages

import core.applications.Application._
import core.data_structures.{BlockChainBlock, DependencyGraph}

import scala.collection.immutable

case class BlockMessage(sequenceNumber: Int, block: BlockChainBlock, dependencyGraph: DependencyGraph, hash: String,
                        applicationSet: immutable.Set[Application], override val sender: String, override val receiver: String)
      extends Message(MessageType.new_block, sender, receiver)
