package main.scala.compression

/**
 * The interface for compression algorithms.
 *
 * @tparam T the type of the compressed data
 */
trait Compression[T] {
  /**
   * Compresses a model.
   *
   * @param t the iteration count of the model
   * @param w the model
   * @return the compressed model
   */
  def compress(t: Int, w: Iterable[Double]): T

  /**
   * Merges a local model with a received compressed model.
   *
   * @param t the iteration count of the local model
   * @param w the local model
   * @param received the received model
   * @return the merged model and the merged iteration count
   */
  def merge(t: Int, w: Iterable[Double], received: T): (Int, Iterable[Double])
}
