package l.tudelft.distribted.ec.protocols

import io.vertx.core.{AsyncResult, Handler}
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.eventbus.{DeliveryOptions, EventBus, Message}
import l.tudelft.distribted.ec.protocols.NetworkState.{NetworkState, READY}

import scala.collection.mutable

case class RequestNetwork(sender: String, state: NetworkState, `type`: String = "request.network") extends ProtocolMessage


/**
 * Creates a networking handler.
 *
 * The Networking Handler is responsible for keeping track of cohorts that exist in the network
 * and also sending messages to other cohorts.
 *
 * @param vertx the vertX instance for event bus creation
 * @param address the address of this verticle
 * @param listenOnly whether we should remain silent and not broadcast ourselves
 */
class NetworkingHandler(val vertx: Vertx, val address: String, val listenOnly : Boolean = false) {
  private val deliveryOptions: DeliveryOptions = DeliveryOptions().setSendTimeout(5000)
  val network: mutable.Map[String, NetworkState] = new mutable.HashMap[String, NetworkState]()


  /**
   * Computes the size of the network
   *
   * @return the size of the network
   */
  def size(): Int = network.size

  /**
   * Starts listening for network changes and keeps tracking of other cohorts.
   *
   * If listenOnly is false then we will also broadcast this verticle to the cohorts.
   */
  def listen(): Unit = {
    network.put(address, READY)

    if (!listenOnly) {
      vertx.setPeriodic(1000L, _ => {
        vertx.eventBus().publish("network-protocol", Json.encodeToBuffer(RequestNetwork(address, network(address))))
      })
    }

    vertx.eventBus().consumer("network-protocol").handler((message: Message[Buffer]) => {
      message.body().toJsonObject.mapTo(classOf[ProtocolMessage]) match {
        case RequestNetwork(sender, state, _) =>
          network.put(sender, state)
      }
    })

  }

  /**
   * Creates the list of cohorts that we can send messages to, excludes ourselves.
   *
   * @return the list of cohorts excluding this network handler.
   */
  def cohort(): collection.Set[String] = {
    network.keySet.filter(cohort => cohort != address)
  }

  /**
   * Sends a message to all cohorts, expecting a reply back or failing the message if a time-out is reached.
   *
   * Cohorts should reply with message.reply
   *
   * @param messageToSend the message to send, must be an instance of `ProtocolMessage`
   * @param handler the handler for replies
   * @tparam T the type of the message to reply
   */
  def sendToCohortExpectingReply[T](messageToSend: ProtocolMessage, handler: Handler[AsyncResult[Message[Buffer]]]): Unit = {
    cohort().foreach(cohort => {
      vertx.eventBus().send(cohort, Json.encodeToBuffer(messageToSend), deliveryOptions, handler)
    })
  }

  /**
   * Sends a message to all cohorts without the possibility for replies.
   *
   *
   * @param messageToSend the message to send, must be an instance of `ProtocolMessage`
   */
  def sendToCohort(messageToSend: ProtocolMessage): Unit = {
    cohort.foreach(cohort => {
      vertx.eventBus().send(cohort, Json.encodeToBuffer(messageToSend), deliveryOptions)
    })
  }
}
