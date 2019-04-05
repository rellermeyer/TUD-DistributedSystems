package monitor

import java.util.Random

import common._

object ExpressionGenerator {

  val random = new Random()

  def generateRandomExpression(size: Int): Expression = {
    if (size == 0) {
      NumberLiteral(random.nextInt())
    } else {
      val leftSize = random.nextInt(size)
      val rightSize = size - 1 - leftSize
      val operator = random.nextInt(3) match {
        case 0 => PlusOp()
        case 1 => MinusOp()
        case 2 => MultOp()
        case _ => sys.error("Something went wrong...")
      }
      BinaryExpression(operator, generateRandomExpression(leftSize), generateRandomExpression(rightSize))
    }
  }

}
