package util

import akka.actor.typed.ActorRef
import util.Messages.Decision.Decision

object Messages {

  type Coordinator = ActorRef[CoordinatorMessage]
  type PrimaryCoordinator = ActorRef[PrimaryCoordinatorMessage]
  type Participant = ActorRef[ParticipantMessage]
  type Initiator = ActorRef[ParticipantMessage]
  type View = Int
  type TransactionID = Int
  type DecisionCertificate = Map[Participant, (SignedRegistration, SignedVote)]
  type Digest = Int

  sealed trait ParticipantMessage

  sealed trait CoordinatorMessage

  sealed trait InitiatorMessage extends ParticipantMessage

  sealed trait PrimaryCoordinatorMessage extends CoordinatorMessage

  sealed trait ViewChangeState

  final case class ViewChangeStateBaNotPrePrepared(v: View, t: TransactionID, c: DecisionCertificate) extends ViewChangeState // "A backup suspects the primary and initiates a view change immediately if the ba-pre-prepare message fails the verification."

  final case class ViewChangeStateBaPrePrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate) extends ViewChangeState

  final case class ViewChangeStateBaPrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, baPrepared: Set[BaPrepare]) extends ViewChangeState // BaPrepared acc to paper "and 2f matching ba-prepared messages from different replicas"

  final case class Transaction(id: TransactionID) // TODO: add payload to Transaction

  final case class SignedRegistration(t: TransactionID, j: Participant) // TODO: add signature

  final case class SignedVote(t: TransactionID, vote: Decision) // TODO: add signature

  // We added this message to let the participant know when to start sending commit requests
  final case class ParticipantStart() extends ParticipantMessage

  final case class SendCoordinatorSet(coordinatorSet: Set[Coordinator]) extends CoordinatorMessage

  final case class Prepare(t: TransactionID, from: Coordinator) extends ParticipantMessage

  final case class Commit(t: TransactionID, BAResult: Decision, from: Coordinator) extends ParticipantMessage

  final case class RegisterWithCoordinator(from: Participant) extends CoordinatorMessage

  final case class Prepared(t: TransactionID, from: Participant) extends CoordinatorMessage

  final case class CommitOutcome(t: TransactionID, commitResult: Decision, from: Participant) extends CoordinatorMessage

  final case class PropagateTransaction(t: Transaction, from: Initiator) extends ParticipantMessage

  final case class InitCommit(t: TransactionID, from: Initiator) extends CoordinatorMessage // TODO: implement abort

  final case class ViewChange(new_v: View, t: TransactionID, p: ViewChangeState, from: Coordinator) extends CoordinatorMessage

  final case class BaPrepare(v: View, t: TransactionID, decisionCertificateDigest: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaCommit(v: View, t: TransactionID, decisionCertificateDigest: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaPrePrepare(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, from: PrimaryCoordinator) extends CoordinatorMessage

  // No need to ask for endpoint references they are already in the registration certificate
  // final case class SendUnknownParticipants(participants: Set[Participant], from: PrimaryCoordinator) extends CoordinatorMessage
  // final case class RequestUnknownParticipants(from: Coordinator) extends PrimaryCoordinatorMessage

  object Decision extends Enumeration {
    type Decision = Value
    val COMMIT, ABORT = Value
  }

}
