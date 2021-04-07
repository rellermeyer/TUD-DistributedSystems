package main.scala

import main.scala.compression.NoCompression

import scala.io.Source
import main.scala.network.{Address, Client, Server}
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair

import java.net.ConnectException
import java.util

/**
 * The application object.
 *
 * The application takes at least eight arguments: eta, lambda, the minibatch size to use, the label to classify,
 * the dataset to use (pendigits, har or spambase) the interval between sending models, the address of the node,
 * the number of neighbours and the adresses of the neighbours.
 */
object Manager extends App {
  if (args.length < 8) {
    println("Too few parameters")
    System.exit(1)
  }

  /**
   * set learner parameters
   */

  val eta = args(0).toDouble
  val lambda = args(1).toDouble
  val batchSize = args(2).toInt
  val label = args(3)

  val learner = new Learner[(Int, Iterable[Double])](makeBatches(getData("trainSet.csv")), getData("testSet.csv"), eta, lambda, new NoCompression)

  val collector = new Address(
    args(5)
  )

  /**
   * set node adress and neighbour adresses
   */
  val address = new Address(
    args(6)
  )

  val noNeighbours = args(7).toInt
  val neighbours = new Array[Address](noNeighbours)
  for (i <- 0 until noNeighbours) {
    Option(args(8+i)) match {
      case Some(address) => neighbours(i) = new Address(address)
      case None => throw new Error("Not all neighbours are defined")
    }
  }

  println("Own address: " + address.toString)
  println("Neighbours: " + neighbours.mkString(", "))

  val client = new Client[(Int, Iterable[Double])](neighbours)
  val server = new Server(address, (model: (Int, Iterable[Double])) => {
    // Callback to merge incoming model
    learner.merge(model)
    // Send results after merge to the collector
    val loss = learner.J()
    println("Received model: " + model.toString() + "\nWith loss: " + loss)
    sendResults(loss, learner.t)
  })

  /**
   * start learner in separate thread
   */
  val thread = new Thread{
    override def run(){
      learner.run()
    }
  }
  thread.start()

  /**
   * Set up timer for sending model
   */
  val interval = args(4).toLong
  while (true) {
    Thread.sleep(interval)
    client.pushModelToActiveNeighbour(learner.compress())
  }

  /**
   * Fetches the data set from data set training data
   *
   * @return The data set
   */
  def getData(dataset: String): Iterable[(Iterable[Double], Int)] = {
    val source = Source.fromFile(dataset)
    val examples = source.getLines().map(line => {
      val example = line.split(",").map(_.trim).toIterable
      (example.dropRight(1).map(_.toDouble), if (example.last == label) 1 else 0)
    }).to(Iterable)

    source.close

    examples
  }

  /**
   * Divides a collection into batches of size batchSize
   *
   * @param data the collection
   * @tparam T the data type in the collection
   * @return a collection of batches
   */
  def makeBatches[T](data: Iterable[T]): List[Iterable[T]] = {
    if (data.size <= batchSize) {
      data :: Nil
    } else {
      data.take(batchSize) :: makeBatches(data.drop(batchSize))
    }
  }

  /**
   * Send the loss results to the collector server
   *
   * @param loss - The loss value which will be send
   */
  def sendResults(loss: Double, iterations: Int): Unit = {
    val httpClient = HttpClients.createDefault()
    val post = new HttpPost("http://" + collector.address + "/collect")
    val params = new util.ArrayList[NameValuePair](2)
    params.add(new BasicNameValuePair("loss", loss.toString))
    params.add(new BasicNameValuePair("node", address.toString))
    params.add(new BasicNameValuePair("current-iteration", iterations.toString))
    post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))
    try {
      httpClient.execute(post)
    } catch {
      case e: ConnectException => println("Make sure the collector server is running!")
    }
  }
}
