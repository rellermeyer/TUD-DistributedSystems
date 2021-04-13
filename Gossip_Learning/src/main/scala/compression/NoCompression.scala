package main.scala.compression

/**
 * Performs no compression.
 */
class NoCompression extends Compression[(Int, Iterable[Double])] {
  /**
   * Returns the local iteration count and model.
   *
   * @param t the iteration count of the model
   * @param w the model
   *  @return the compressed model
   */
  override def compress(t: Int, w: Iterable[Double]): (Int, Iterable[Double]) = (t, w)

  /**
   * Takes an average of the received and local models, weighted by their iteration counts, and takes the maximum of the iteration counts
   *
   * @param t the iteration count of the local model
   * @param w the local model
   * @param received the received model
   *  @return the merged model and the merged iteration count
   */
  override def merge(t: Int, w: Iterable[Double], received: (Int, Iterable[Double])): (Int, Iterable[Double]) = received match {
    case (tr, wr) =>
      val a = tr.toDouble / (t + tr)
      (
        math.max(t, tr),
        w.zip(wr).map({ case (x, y) => (1 - a) * x + a * y })
      )
  }
}
