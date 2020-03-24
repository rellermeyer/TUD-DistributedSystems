package util

import akka.actor.typed.ActorRef
import util.Messages.Decision.Decision

import scala.collection.mutable

object Messages {

  type Coordinator = ActorRef[CoordinatorMessage]
  type Participant = ActorRef[ParticipantMessage]
  type View = Int
  type TransactionID = Int
  type DecisionCertificate = mutable.Map[Participant, DecisionCertificateEntry]
  type Digest = Int

  sealed trait ParticipantMessage

  sealed trait CoordinatorMessage

  sealed trait ViewChangeState

  case class DecisionCertificateEntry(registration: Messages.Register, vote: Option[VotePrepared], abort: Option[InitAbort])

  final case class ViewChangeStateBaNotPrePrepared(v: View, t: TransactionID, c: DecisionCertificate) extends ViewChangeState // "A backup suspects the primary and initiates a view change immediately if the ba-pre-prepare message fails the verification."

  final case class ViewChangeStateBaPrePrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate) extends ViewChangeState

  final case class ViewChangeStateBaPrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, baPrepared: Set[BaPrepare]) extends ViewChangeState // BaPrepared acc to paper "and 2f matching ba-prepared messages from different replicas"

  final case class Transaction(id: TransactionID) // TODO: add payload to Transaction

  final case class Setup(coordinators: Array[Coordinator]) extends CoordinatorMessage

  final case class Prepare(t: TransactionID, from: Coordinator) extends ParticipantMessage

  final case class Commit(t: TransactionID, from: Coordinator) extends ParticipantMessage

  final case class Rollback(t: TransactionID, from: Coordinator) extends ParticipantMessage

  final case class Register(t: TransactionID, from: Participant) extends CoordinatorMessage // TODO: add signature

  final case class VotePrepared(t: TransactionID, vote: Decision, from: Participant) extends CoordinatorMessage // TODO: add signature

  final case class Committed(t: TransactionID, commitResult: Decision, from: Participant) extends CoordinatorMessage

  final case class PropagateTransaction(t: Transaction) extends ParticipantMessage // from: Initiator

  final case class InitCommit(t: TransactionID, from: Participant) extends CoordinatorMessage  // from: Initiator

  final case class InitAbort(t: TransactionID, from: Participant) extends CoordinatorMessage  // from: Initiator

  final case class ViewChange(new_v: View, t: TransactionID, p: ViewChangeState, from: Coordinator) extends CoordinatorMessage

  final case class BaPrepare(v: View, t: TransactionID, c: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaCommit(v: View, t: TransactionID, c: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaPrePrepare(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, from: Coordinator) extends CoordinatorMessage // from: PrimaryCoordinator

  // No need to ask for endpoint references they are already in the registration certificate
  // final case class SendUnknownParticipants(participants: Set[Participant], from: PrimaryCoordinator) extends CoordinatorMessage
  // final case class RequestUnknownParticipants(from: Coordinator) extends PrimaryCoordinatorMessage

  object Decision extends Enumeration {
    type Decision = Value
    val COMMIT, ABORT = Value
  }

}
