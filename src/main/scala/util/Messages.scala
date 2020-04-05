package util

import java.security.{PrivateKey, PublicKey}

import akka.actor.typed.ActorRef
import util.Messages.Decision.Decision

import scala.collection.mutable
import scala.math.BigInt

object Messages {

  type CoordinatorRef = ActorRef[Signed[CoordinatorMessage]]
  type ParticipantRef = ActorRef[Signed[ParticipantMessage]]
  type View = Int
  type TransactionID = Int
  type DecisionCertificate = mutable.Map[ParticipantRef, DecisionCertificateEntry]
  type Digest = Int
  type Signature = Array[Byte]
  type SignatureTuple = (Signature, SignedPublicKey)
  type SignedPublicKey = (PublicKey, Signature)
  type KeyTuple = (PrivateKey, SignedPublicKey)

  sealed trait ParticipantMessage {
    def sign(keyTuple: KeyTuple) = new Signed(this, keyTuple._1, keyTuple._2)

    def fakesign() = new Signed(this)
  }

  sealed trait CoordinatorMessage {
    def sign(keyTuple: KeyTuple) = new Signed(this, keyTuple._1, keyTuple._2)
  }

  sealed trait ViewChangeState

  class Signed[M](val m: M, signature: Signature, publicKey: PublicKey, signaturePublicKey: Signature, empty: Int) {
    def this(t: M, privateKey: PrivateKey, publicKey: PublicKey, signaturePublicKey: Signature) = {
      this(t, Signed.sign(t, privateKey), publicKey, signaturePublicKey, 0)
    }

    def this(t: M, privateKey: PrivateKey, signedPublicKey: SignedPublicKey) = {
      this(t, privateKey, signedPublicKey._1, signedPublicKey._2)
    }

    def this(t: M) = { // fakesign
      this(t, null, null, null, 0)
    }

    def verify(masterKey: PublicKey): Boolean = {
      if (publicKey == null) return false
      val s: java.security.Signature = java.security.Signature.getInstance("SHA512withRSA")
      s.initVerify(publicKey)
      s.update(BigInt(m.hashCode()).toByteArray)
      if (!s.verify(signature)) return false
      s.initVerify(masterKey)
      s.update(BigInt(publicKey.hashCode()).toByteArray)
      if (!s.verify(signaturePublicKey)) return false
      // TODO: verify sender(from) in message
      true
    }

    override def toString = s"Signed($m)"
  }

  case class DecisionCertificateEntry(registration: Messages.Register, vote: Option[VotePrepared], abort: Option[InitAbort])

  final case class ViewChangeStateBaNotPrePrepared(v: View, t: TransactionID, c: DecisionCertificate) extends ViewChangeState // "A backup suspects the primary and initiates a view change immediately if the ba-pre-prepare message fails the verification."


  final case class ViewChangeStateBaPrePrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate) extends ViewChangeState

  final case class ViewChangeStateBaPrepared(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, baPrepared: mutable.Set[BaPrepare]) extends ViewChangeState // BaPrepared acc to paper "and 2f matching ba-prepared messages from different replicas"

  final case class Transaction(id: TransactionID) // TODO: add payload to Transaction

  final case class Setup(coordinators: Array[CoordinatorRef]) extends CoordinatorMessage

  final case class Prepare(t: TransactionID, from: CoordinatorRef) extends ParticipantMessage

  final case class Commit(t: TransactionID, from: CoordinatorRef) extends ParticipantMessage

  final case class Rollback(t: TransactionID, from: CoordinatorRef) extends ParticipantMessage

  final case class Register(t: TransactionID, from: ParticipantRef) extends CoordinatorMessage

  final case class ConfirmRegistration(t: TransactionID, to: ParticipantRef, from: CoordinatorRef) extends ParticipantMessage

  final case class VotePrepared(t: TransactionID, vote: Decision, from: ParticipantRef) extends CoordinatorMessage

  final case class Committed(t: TransactionID, commitResult: Decision, from: ParticipantRef) extends CoordinatorMessage

  final case class AppointInitiator(t: Transaction, initAction: Decision, participants: Array[ParticipantRef], from: ParticipantRef) extends ParticipantMessage

  final case class PropagateTransaction(t: Transaction, from: ParticipantRef) extends ParticipantMessage // from: Initiator

  final case class PropagationReply(t: TransactionID, from: ParticipantRef) extends ParticipantMessage // from: Participant

  final case class InitCommit(t: TransactionID, from: ParticipantRef) extends CoordinatorMessage // from: Initiator

  final case class InitAbort(t: TransactionID, from: ParticipantRef) extends CoordinatorMessage // from: Initiator

  final case class ViewChange(new_v: View, t: TransactionID, p: ViewChangeState, from: CoordinatorRef) extends CoordinatorMessage

  final case class BaPrepare(v: View, t: TransactionID, c: Digest, o: Decision, from: CoordinatorRef) extends CoordinatorMessage

  final case class BaCommit(v: View, t: TransactionID, c: Digest, o: Decision, from: CoordinatorRef) extends CoordinatorMessage

  final case class BaPrePrepare(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, from: CoordinatorRef) extends CoordinatorMessage // from: PrimaryCoordinator

  object Signed {
    def sign[M](m: M, privateKey: PrivateKey): Array[Byte] = {
      val s: java.security.Signature = java.security.Signature.getInstance("SHA512withRSA")
      s.initSign(privateKey)
      s.update(BigInt(m.hashCode()).toByteArray) // TODO: this is not secure. Sign the serialized message?
      s.sign()
    }
  }

  object Decision extends Enumeration {
    type Decision = Value
    val COMMIT, ABORT = Value
  }

}
