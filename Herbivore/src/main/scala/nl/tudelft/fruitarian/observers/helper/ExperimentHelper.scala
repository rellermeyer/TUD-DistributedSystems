package nl.tudelft.fruitarian.observers.helper

import scala.util.Random

object ExperimentHelper {
  val random = new Random()
  val characters = "abcdefghijklmnopqrstuvwxyz".split("")

  def generateRandomMessage(msgSize: Int): String = {
    var msg = ""
    for (x <- 1 to msgSize) {
      msg += characters(random.nextInt(characters.length))
    }
    msg
  }
}
