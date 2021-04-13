package main.scala.network

import scala.util.Random

/**
 * The Client part of the network module -
 * Here the connections with the neighbours are contained
 *
 * @param neighbours - The addresses of the neighbours in Array form
 */
class Client[T](val neighbours: Array[Address]) {
//  Init the pool with connections
  val connectionPool = neighbours.map(new Connection[T](_))

  /**
   * Push a model to a random neighbour
   * @param model - The model which will be pushed to a random neighbour
   */
  def pushModelToActiveNeighbour(model: T): Unit = {
    val neighbourConnection = getRandomActiveConnection();
    if (neighbourConnection.nonEmpty) {
      neighbourConnection.get.pushModel(model)
    }
  }

  /**
   * Get a random active connection from the connection pool
   * @return - A random connection if available otherwise null
   */
  private def getRandomActiveConnection(): Option[Connection[T]] = {
    val activeConnections = connectionPool.filter(e => e.connected)
    if (activeConnections.length > 0) {
      Some(activeConnections(Random.nextInt(activeConnections.length)))
    } else {
      None
    }
  }
}