package main.scala.compression

import scala.util.Random

/**
 * Performs compression by only sending a fraction of the data.
 *
 * @param probability the probability for any model parameter to be included in the compressed data
 */
class SubsamplingCompression(probability: Double) extends Compression[(Int, Int, Iterable[Double])] {
  /**
   * The generator for the random seeds that determine which model parameters are sent.
   */
  val seedGenerator = new Random()

  /**
   * Returns the local iteration count and a fraction of the model parameters.
   *
   * @param t the iteration count of the model
   * @param w the model
   * @return the compressed model
   */
  override def compress(t: Int, w: Iterable[Double]): (Int, Int, Iterable[Double]) = {
    val seed = seedGenerator.nextInt()
    val filterGenerator = new Random(seed)
    (t, seed, w.filter(_ => filterGenerator.nextDouble() < probability))
  }

  /**
   * Merges the parameters included in the received model with the current model, takes the maximum of the received and local iteration counts
   *
   * @param t        the iteration count of the local model
   * @param w        the local model
   * @param received the received model
   * @return the merged model and the merged iteration count
   */
  override def merge(t: Int, w: Iterable[Double], received: (Int, Int, Iterable[Double])): (Int, Iterable[Double]) = received match {
    case (tr, seed, wr) => (
      math.max(t, tr),
      merge(new Random(seed), tr.toDouble / (t + tr), w, wr)
  )
  }

  /**
   * Merges the received model with the current model.
   *
   * @param filterGenerator the random generator which determines for each parameter whether it is included in the received model or not
   * @param a the weight to use for the received model parameters
   * @param w the local model
   * @param wr the received model parameters
   * @return the merged model
   */
  def merge(filterGenerator: Random, a: Double, w: Iterable[Double], wr: Iterable[Double]): List[Double] = {
    if (wr.isEmpty) {
      w.toList
    } else if (filterGenerator.nextDouble() < probability) {
      ((1 - a) * w.head + a * wr.head) +: merge(filterGenerator, a, w.drop(1), wr.drop(1))
    } else {
      w.head +: merge(filterGenerator, a, w.drop(1), wr)
    }
  }
}
