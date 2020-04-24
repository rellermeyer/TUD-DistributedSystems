package nl.tudelft.fruitarian.observers

import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.models.{DCnet, NetworkInfo}
import nl.tudelft.fruitarian.p2p.messages._
import nl.tudelft.fruitarian.p2p.{Address, TCPHandler}
import nl.tudelft.fruitarian.patterns.Observer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.Breaks._
import scala.util.{Random, Try}

/**
 * This class handles the Transmission message phase. This means that for each
 * 'round' it should reply with a random message in case it doesn't want to
 * send a message and it should reply with a it's message in encrypted form if
 * a message has been queued.
 */
class TransmissionObserver(handler: TCPHandler, networkInfo: NetworkInfo) extends Observer[FruitarianMessage] {
  protected implicit val context: ExecutionContextExecutor =
    ExecutionContext.global
  val MESSAGE_ROUND_TIMEOUT = 5000
  val BACKOFF_RANGE = 10
  var messageQueue = new mutable.Queue[String]()
  var messageSent: String = ""
  var backoff = 0
  var roundId = 0

  /**
   * Add a message to the message queue.
   * The message will be sent in the next round if the backoff is not set.
   *
   * @param message    The message to be sent.
   * @param prioritise Whether to prioritise this message (aka send it as
   *                   soon as possible instead of adding it in the back of
   *                   the queue).
   */
  def queueMessage(message: String, prioritise: Boolean = false): Unit = {
    val processedMessage = message.stripTrailing()
    if (processedMessage.length > DCnet.MESSAGE_SIZE) {
      throw new Exception("Message too long.")
    }
    if (prioritise) {
      messageQueue = mutable.Queue[String](processedMessage) ++ messageQueue
    } else {
      messageQueue.enqueue(processedMessage)
    }
  }

  /**
   * Starts a message round if the current node is the center node.
   *
   * The message round asks each node in the clique (including itself) to send
   * message using the TransmitRequest message. The amount of requests sent is
   * noted in the DCNet, however this is a soft verification as the protocol
   * could break if one or more of the nodes have an outdated cliquePeers list.
   * Resulting in nonsense messages.
   *
   * Upon receiving a TransmitRequest all nodes will return a TransmitMessage
   * with their message or a random value based on the random seeds between
   * the peers. If exactly one of the nodes sent a message instead of a random
   * value this message can be decrypted on the centre node without knowing
   * which node sent it (see DCNet code).
   */
  def startMessageRound(): Unit = {
    if (networkInfo.chatMode) Thread.sleep(1000)
    // Clear possible remaining responses.
    DCnet.clearResponses()
    roundId += 1

    // Send a TransmitRequest to all peers and itself (as this node is also part of the clique).
    Await.ready(sendMessageToClique((address: Address) => TransmitRequest
    (networkInfo.ownAddress, address, roundId)), 100.millis)

    // Set the amount of requests sent.
    DCnet.transmitRequestsSent = networkInfo.cliquePeers.length + 1

    Logger.log(s"[S] [${networkInfo.nodeId}] Started Message round " +
      s"[R$roundId] for ${DCnet.transmitRequestsSent} node(s).", Logger.Level.DEBUG)

    val timeoutFuture = Future[Boolean] {
      // Save the roundId in which this timeout future started.
      val startRound = s"$roundId".toInt;
      breakable {
        // Break the sleep in to steps of 10ms.
        for (_ <- 1 to MESSAGE_ROUND_TIMEOUT/10) {
          Thread.sleep(10)
          // Each step compare the current roundId and break early in case
          // the roundId has already gone up.
          if (this.synchronized { roundId } > startRound) {
            break
          }
        }
      }
      // After the timeout, return true if the startRound is equal to the
      // current round.
      startRound == this.synchronized { roundId }
    }
    timeoutFuture.onComplete((r: Try[Boolean]) => breakable {
      // If the timeout was completed with false it means the round was
      // completed successfully. If it completed with true it means we are
      // stuck in the current round, thus trigger the timeout code below.
      if (!r.get) {
        break;
      }
      Logger.log(s"[S] Message round [$roundId] timed out, retrying...", Logger.Level.ERROR)
      // Send a "TIMEOUT" Text message to all peers to let them know the
      // round failed and trigger the message requeue behaviour if one of
      // them actually sent a message this round.
      Await.ready(sendMessageToClique((address: Address) => ResultMessage
      (networkInfo.ownAddress, address, "TIMEOUT")), 100.millis)
      // Give a little time for processing on the clients before starting the
      // next round.
      Thread.sleep(5)
      startMessageRound()
    })
  }

  /**
   * Helper function to send a message to all clique peers and itself.
   */
  def sendMessageToClique(msg: (Address) => FruitarianMessage): Future[Unit] = Future {
    networkInfo.cliquePeers.foreach(p => handler.sendMessage(msg(p.address)))
    handler.sendMessage(msg(networkInfo.ownAddress))
  }

  def startNextRound(roundId: Int): Unit = {
    val nextCenter = networkInfo.getNextPeer
    nextCenter match {
      case Some(p) => handler.sendMessage(NextRoundMessage(networkInfo.ownAddress, p.address, roundId))
      case None => startMessageRound()
    }
  }


  override def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case TransmitRequest(from, to, reqRoundId) => this.synchronized {
      if (messageQueue.nonEmpty && messageSent.isEmpty && backoff == 0) {
        // If we have a message to send and are not waiting for confirmation
        // of a previous message, send the next message. If we failed to send
        // a message and have a backoff we have to wait this cycle.
        // TODO: This 'just-send-it' behaviour can cause collisions, as
        //  multiple nodes could send a message at the same time. It wil also
        //  produce nonsense messages in case no one sends an actual encrypted
        //  message.
        messageSent = messageQueue.dequeue()
        Logger.log(s"[C][R$reqRoundId] Sent my message: '$messageSent'", Logger.Level.DEBUG)
        handler.sendMessage(TransmitMessage(to, from, (reqRoundId, DCnet
          .encryptMessage(messageSent, networkInfo.cliquePeers.toList,
            reqRoundId))))
      } else {
        // Else send a random message.
        handler.sendMessage(TransmitMessage(to, from, (reqRoundId, DCnet.getRandom
        (networkInfo.cliquePeers.toList, reqRoundId))))
      }
      // Decrease the backoff by one until 0.
      backoff = math.max(0, backoff - 1)
    }

    case TransmitMessage(_, _, message) =>
      this.synchronized {
        if (message._1 == roundId) {
          // Only add the message if it matches the round id.
          DCnet.appendResponse(message._2)
        }
      }
      if (this.synchronized { DCnet.canDecrypt }) {
        val decryptedMessage = DCnet.decryptReceivedMessages()
          // Send the decrypted message to the clique.
        Await.ready(sendMessageToClique((address: Address) => ResultMessage
        (networkInfo.ownAddress, address, decryptedMessage)), 100.millis)

        // This sleep is required before starting the next round, likely for
        // different threads to start. It gives clients the time to handle
        // the result message.
        Thread.sleep(5)
        startNextRound(roundId)
      }

    case ResultMessage(_, _, msg) if !messageSent.isEmpty =>
      // If we recently sent a message, the next TextMessage received should be
      // this message. If not we need to resend the message.
      if (msg != messageSent) {
        // If the message is not as sent, queue the message for sending again.
        // We apply a random backoff in amount of cycles to hopefully prevent
        // another collision.
        queueMessage(messageSent, true)
        backoff = new Random().nextInt(BACKOFF_RANGE)
        Logger.log(s"[C] Message not sent correctly, enqueued again in $backoff cycles.", Logger.Level.ERROR)
      }
      // Unblock the message sending process to allow the next message or a resend.
      messageSent = ""

    case NextRoundMessage(_, _, r) =>
      roundId = r
      startMessageRound()
    case _ =>
  }
}
