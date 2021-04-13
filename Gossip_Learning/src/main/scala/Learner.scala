package main.scala

import main.scala.compression.Compression

/**
 * The machine learning module.
 *
 * @param batches the data set to perform learning on, chopped up into batches
 * @param eta the (starting) learning rate
 * @param lambda the overfitting reduction weight
 * @param compression the object to perform compression with
 * @tparam T the type of compressed data
 */
class Learner[T](batches: Iterable[Iterable[(Iterable[Double], Int)]], testSet: Iterable[(Iterable[Double], Int)], eta: Double, lambda: Double, compression: Compression[T]) {
  /**
   * The current iteration.
   */
  var t = 0

  /**
   * The number of items in the test set.
   */
  val n: Int = testSet.size

  /**
   * The current model.
   */
  var w: Iterable[Double] = testSet.head match {
    case (xi, _) => xi.map(_ => 0.0)
  }

  /**
   * Calculates the inner product between two vectors
   *
   * @param a the first vector
   * @param b the second vector
   * @return the inner product between a and b
   */
  def innerProduct(a: Iterable[Double], b: Iterable[Double]): Double =
    a.zip(b).map({ case (ai, bi) => ai * bi }).sum

  /**
   * Calculates the cost of a single example for the current model.
   *
   * @param xi the parameters of the example
   * @param yi whether the example should be classified as "in the set", denoted by yi = 1 or "not in the set", denoted by yi = 0
   * @return the cost for this example
   */
  def predictionError(xi: Iterable[Double], yi: Int): Int = (yi - math.round(1 - 1 / (1 + math.exp(innerProduct(xi, w)))).toInt).abs

  /**
   * Calculates the total cost for the current model.
   *
   * @return the total cost
   */
  def J(): Double =
    testSet.map({ case (xi, yi) => predictionError(xi, yi) }).sum.toDouble / n

  /**
   * Calculates the gradient of the cost function for a batch.
   *
   * @param batch the batch to calculate with
   * @return the gradient of the cost function, calculated on this batch
   */
  def gradient(batch: Iterable[(Iterable[Double], Int)]): Iterable[Double] =
    batch.map({
      case (xi, yi) =>
        val exp = math.exp(innerProduct(xi, w))
        var factor = 1 - yi - 1 / (1 + exp); // Adapted from https://www.wolframalpha.com/input/?i=d%2Fdx+ln%28y+%2B+%281+-+2+*+y%29+%2F+%281+%2B+e%5E%28a+*+x+%2B+b%29%29%29

        if (factor.isNaN) {
          println(s"Encountered NaN while processing example ${(xi, yi)} with model ${w} which gave exp ${exp} and factor ${factor}.")
          factor = 0
        }

        xi.map(_ * factor) // For all j, the derivative with respect to wj of -ln(P(...))
    }).toList.fold(w.map(_ * lambda))((v1, v2) => v1.zip(v2).map({ case (a, b) => a + b }))

  /**
   * Runs the learning algorithm.
   */
  def run(): Unit = while (true) this.synchronized {
    for (batch <- batches) {
      t += batch.size
      w = w.zip(gradient(batch)).map({ case (wi, gi) => wi - eta / t * gi })
    }
  }

  /**
   * Merge a received model with the current model.
   *
   * @param received the received model
   */
  def merge(received: T): Unit = this.synchronized {
    compression.merge(t, w, received) match {
      case (tNew, wNew) =>
        w = wNew
        t = tNew
    }
  }

  /**
   * Compress the current model.
   *
   * @return the compressed model
   */
  def compress(): T = this.synchronized {
    compression.compress(t, w)
  }
}
