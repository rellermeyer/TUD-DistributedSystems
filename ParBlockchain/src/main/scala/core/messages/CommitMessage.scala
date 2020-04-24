package core.messages

import core.data_structures.Record

import scala.collection.immutable
import scala.collection.mutable

case class CommitMessage(changedState: immutable.Set[(String, mutable.Set[Record])], override val sender: String, override val receiver: String)
      extends Message(MessageType.commit, sender, receiver)
