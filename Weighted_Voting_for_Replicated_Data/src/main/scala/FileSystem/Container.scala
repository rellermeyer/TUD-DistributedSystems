package FileSystem

import scala.util.Random

class Container(newLatency: Int, newBlockingProb: Double) {

  /**
   * constructor
   */
  case class FailResult(reason:String)

  private val _latency: Int = newLatency
  private val _blockingProb: Double = newBlockingProb
  private var _representatives: Seq[Representative] = Seq.empty[Representative]

  /**
   * accessor methods
   */
  def latency: Int = _latency

  def representatives: Seq[Representative] = _representatives


  /**
   * Looks for representatives within a suite
   * @param suiteId
   * @return representative ID
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

  def initTentativeContainer(): Unit = {
    for (r <- _representatives) {
      r.initTentativeRep()
    }
  }

  def commitTentativeContainer(): Unit = {
    for (r <- _representatives) {
      r.commitTentativeRep()
    }
  }


  /**
   * Initialize a single representative
   * @param suiteId
   * @param suiteR
   * @param suiteW
   * @param repWeight
   * @return
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
   * Returns the contents of a representative
   * @param suiteId
   * @return content
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
   * Write content to a representative
   * @param suiteId
   * @param newContent
   * @return
   */

  def writeRepresentative(suiteId: Int, newContent: Int, increment: Boolean): Either[FailResult, Unit] = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      rep.get.writeTentative(newContent)
      if (increment) {
        rep.get.incrementNumber()
      }
      Right()
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
