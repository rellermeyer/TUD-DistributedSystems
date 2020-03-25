package util

import akka.actor.typed.ActorRef
import util.Messages.Decision.Decision
import java.security.{PrivateKey, PublicKey, Signature}
import scala.collection.mutable
import scala.math.BigInt

object Messages {

  type Coordinator = ActorRef[CoordinatorMessage]
  type Participant = ActorRef[ParticipantMessage]
  type View = Int
  type TransactionID = Int
  type DecisionCertificate = mutable.Map[Participant, DecisionCertificateEntry]
  type Digest = Int
  type Signature = Array[Byte]
  type SignatureTuple = (Signature, SignedPublicKey)
  type SignedPublicKey = (PublicKey, Signature)
  type KeyTuple = (PrivateKey, SignedPublicKey)

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

  final case class Register(t: TransactionID, s: SignatureTuple, from: Participant) extends CoordinatorMessage // TODO: add signature

  final case class VotePrepared(t: TransactionID, vote: Decision, s: SignatureTuple, from: Participant) extends CoordinatorMessage // TODO: add signature

  final case class Committed(t: TransactionID, commitResult: Decision, from: Participant) extends CoordinatorMessage

  final case class PropagateTransaction(t: Transaction) extends ParticipantMessage // from: Initiator

  final case class InitCommit(t: TransactionID, from: Participant) extends CoordinatorMessage // from: Initiator

  final case class InitAbort(t: TransactionID, from: Participant) extends CoordinatorMessage // from: Initiator

  final case class ViewChange(new_v: View, t: TransactionID, p: ViewChangeState, from: Coordinator) extends CoordinatorMessage

  final case class BaPrepare(v: View, t: TransactionID, c: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaCommit(v: View, t: TransactionID, c: Digest, o: Decision, from: Coordinator) extends CoordinatorMessage

  final case class BaPrePrepare(v: View, t: TransactionID, o: Decision, c: DecisionCertificate, from: Coordinator) extends CoordinatorMessage // from: PrimaryCoordinator

  object Decision extends Enumeration {
    type Decision = Value
    val COMMIT, ABORT = Value
  }


  def sign(data: String, privateKey: PrivateKey): Signature = {
    var s: java.security.Signature = Signature.getInstance("SHA512withRSA");
    s.initSign(privateKey)
    s.update(hash(data.getBytes()))
    return s.sign()
  }

  def verify(data: String, signature: Signature, publicKey: PublicKey): Boolean = {
    var s: java.security.Signature = Signature.getInstance("SHA512withRSA");
    s.initVerify(publicKey)
    s.update(hash(data.getBytes()))

    return s.verify(signature)
  }

  def verify(data: String, signatureTuple: SignatureTuple, masterPublicKey: PublicKey): Boolean = {
    var signature = signatureTuple._1
    var signatureSignature = signatureTuple._2._2
    var signaturePublicKey = signatureTuple._2._1
    if (verify(signaturePublicKey.toString, signatureSignature, masterPublicKey)) { //Check if public key should be trusted
      return verify(data, signature, signaturePublicKey) // Check if data is signed by public key
    } else {
      //Untrusted public key
      return false
    }
  }

  def hash(data: DecisionCertificate): Int = {
    scala.util.hashing.MurmurHash3.mapHash(data)
  }

  def hash(data: Array[Byte]): Array[Byte] = {
    var i = scala.util.hashing.MurmurHash3.bytesHash(data)
    BigInt(i).toByteArray
  }

}
