package util

import akka.actor.typed.ActorRef

object Messages {
  sealed trait ParticipantMessage
  sealed trait CoordinatorMessage
  sealed trait InitiatorMessage extends ParticipantMessage
  type Coordinator = ActorRef[CoordinatorMessage]
  type Participant = ActorRef[ParticipantMessage]
  type Initiator = ActorRef[ParticipantMessage]

  final case class Transaction(id: Int)
  final case class View(id: Int)

  // We added this message to let the participant know when to start sending commit requests
  final case class ParticipantStart() extends ParticipantMessage
  final case class SendCoordinatorSet(coordinatorSet: Set[Coordinator]) extends CoordinatorMessage
  //Coordinator to Participant
  final case class Prepare(from: Coordinator) extends ParticipantMessage
  final case class Commit(from: Coordinator, BAResult: Boolean /*true =commit,false=abort*/) extends ParticipantMessage
  //Participant to Initiator
  final case class RegisterWithInitiator(from: Participant) extends InitiatorMessage
  //Participant to Coordinator
  final case class RegisterWithCoordinator(from: Participant) extends CoordinatorMessage
  final case class Prepared(t: Transaction, from: Participant) extends CoordinatorMessage
  final case class CommitOutcome(t: Transaction, commitResult: Boolean /*true =commit,false=abort*/, from: Participant) extends CoordinatorMessage

  //Initiator to Coordinator and Participant
  final case class InitCommit(t: Transaction, /*commitRequest,*/ proposeCommit: Boolean/*true =commit,false=abort*/, from: Initiator) extends CoordinatorMessage with ParticipantMessage
  //Coordinator to Coordinator
  final case class InitViewChange(from: Coordinator) extends CoordinatorMessage
  /****************** some fields are commented out as no type is defined for them yet ************/
  final case class NewView(new_v: View/*, viewCertificate, transactionID, decisionCertificate*/, proposeCommit: Boolean, from: Coordinator) extends CoordinatorMessage
  final case class BaPrepare(v: View, t: Transaction /*,decisionCertificate, */, proposeCommit: Boolean, from: Coordinator) extends CoordinatorMessage
  final case class BaCommit(v: View, t: Transaction /*,decisionCertificate*/, proposeCommit: Boolean, from: Coordinator) extends CoordinatorMessage
  //Coordinator(primary) to Coordinator
  final case class BaPrePrepare(v: View, t: Transaction/*, decisionCertificate*/, proposeCommit: Boolean, from: Coordinator) extends CoordinatorMessage
  final case class SendUnknownParticipants(participants: Set[Participant], from: Coordinator) extends CoordinatorMessage
  final case class RequestUnknownParticipants(from: Coordinator) extends CoordinatorMessage

  // other messages for debugging purposes
  final case class Greet(whom: String, replyTo: ActorRef[Greeted]) extends CoordinatorMessage with ParticipantMessage
  final case class Greeted(whom: String, from: ActorRef[Greet])
}
