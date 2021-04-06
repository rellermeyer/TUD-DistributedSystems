package FileSystem

import scala.util.Random

class Container(newLatency: Int, newBlockingProb: Double) {

  /**
   * Case class for handling failed method calls
   * @param reason a textual explanation of the reason for failure
   */
  case class FailResult(reason:String)

  /**
   * Private class fields
   */
  private val _latency: Int = newLatency
  private val _blockingProb: Double = newBlockingProb
  private var _representatives: Seq[Representative] = Seq.empty[Representative]

  /**
   * accessor methods
   */
  def latency: Int = _latency
  def representatives: Seq[Representative] = _representatives

  /**
   * Attempts to find a representative by ID. May fail if if representative does not exists, or if container blocks
   * @param suiteId the ID of the representative to be found
   * @return either the representative or None
   */
  def findRepresentative(suiteId: Int): Option[Representative] = {
    val r: Random = scala.util.Random
    val event: Double = r.nextDouble()
    if (event >= _blockingProb) {
      _representatives.find(element => element.repId == suiteId)
    }
    else {
      None
    }
  }

  /**
   * Set tentative representative states for all representatives at the start of a new transaction
   */
  def initTentativeContainer(): Unit = {
    for (r <- _representatives) {
      r.initTentativeRep()
    }
  }

  /**
   * Set definitive representative states for all representatives when committing a transaction
   */
  def commitTentativeContainer(): Unit = {
    for (r <- _representatives) {
      r.commitTentativeRep()
    }
  }

  /**
   * Instantiate a new representative. May fail if a representative with the provided ID exists already
   * @param suiteId ID of the new representative
   * @param suiteR r value of the suite the rep. belongs to
   * @param suiteW r value of the suite the rep. belongs to
   * @param suiteInfo prefix information of the suite the rep. belongs to
   * @param repWeight voting weight of the new representative
   * @return either failure or nothing
   */
  def createRepresentative(suiteId: Int, suiteR: Int, suiteW: Int, suiteInfo: Seq[Int], repWeight: Int): Either[FailResult, Unit] = {
    if (!_representatives.exists(e => e.repId == suiteId)) {
      val newRepresentative = Representative(suiteId: Int, suiteR: Int, suiteW: Int, suiteInfo: Seq[Int], repWeight: Int)
      _representatives = _representatives :+ newRepresentative
      Right()
    }
    else {
      Left(FailResult("createRepresentative failed: a representative of file " + suiteId + " exists already"))
    }
  }

  /**
   * Delete a representative. May fail if a representative with the provided ID does not exist
   * @param suiteId ID of the rep. that is to be deleted
   * @return either failure or nothing
   */
  def deleteRepresentative(suiteId: Int): Either[FailResult, Unit] = {
    if (_representatives.exists(e => e.repId == suiteId)) {
      _representatives = representatives.filter(e => e.repId != suiteId)
      Right()
    }
    else {
      Left(FailResult("deleteRepresentative failed: no representative of file " + suiteId + " exists"))
    }
  }

  /**
   * Return the content of a representative. May fail if representative cannot be found
   * @param suiteId ID of the rep. that is to be read
   * @return either failure or a tuple containing the content and the latency for this container
   */
  def readRepresentative(suiteId: Int): Either[FailResult, (Int, Int)] = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      Right(rep.get.contentTentative, _latency)
    }
    else {
      Left(FailResult("readRepresentative failed: no representative of file " + suiteId + " could be found in container"))
    }
  }

  /**
   * Write new content to a representative. May fail if representative cannot be found
   * @param suiteId ID of the rep. that is to be written to
   * @param newContent new integer content that is to be written
   * @param increment flag to determine if version number should be incremented
   * @return either failure or the latency for this container
   */
  def writeRepresentative(suiteId: Int, newContent: Int, increment: Boolean): Either[FailResult, Int] = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      rep.get.writeTentative(newContent)
      if (increment) {
        rep.get.incrementNumber()
      }
      Right(_latency)
    }
    else {
      Left(FailResult("writeRepresentative failed: no representative of file " + suiteId + " could be found in container"))
    }
  }
}

/**
 * companion object
 */
object Container {
  def apply(newLatency: Int, newBlockingProb: Double): Container = {
    val newContainer = new Container(newLatency, newBlockingProb)
    newContainer
  }
}
