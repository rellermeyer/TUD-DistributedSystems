package utilities

import scala.math.{pow, sqrt}

class Results(private val values: List[Long] = List[Long]()) {

	def withAddedResult(result: Long): Results = {
		return new Results(values :+ result)
	}
	
	def apply(index: Int): Long = {
		return values(index)
	}

	lazy val stdDev: Double = {
		val meanOfSquares = values.map(pow(_, 2)).sum / values.size
		val squareOfMean = pow(mean, 2)

		sqrt(meanOfSquares - squareOfMean)
	}

	lazy val mean: Double = {
		values.sum / values.size
	}
	
	override def toString: String = {
		return s"mean ${mean}, std dev: ${stdDev}\n values: ${values.toString()}"
	}
}
