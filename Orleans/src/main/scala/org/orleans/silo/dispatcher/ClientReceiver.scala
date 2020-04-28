package org.orleans.silo.dispatcher
import java.net.{ServerSocket, Socket, SocketException}
import java.util
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.{Collections, Timer, TimerTask}

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.metrics.RegistryFactory
import org.orleans.silo.services.grain.Grain

import scala.collection.JavaConverters._
import scala.reflect._

/** Cleans client threads that haven't been running for a while. **/
class ClientCleanup(clientSockets: util.List[MessageReceiver])
    extends TimerTask
    with LazyLogging {
  val CLIENT_REMOVE_TIME_SEC: Int = 10
  override def run(): Unit = {
    val toRemove: util.List[MessageReceiver] =
      new util.ArrayList[MessageReceiver]()

    val iterator: util.Iterator[MessageReceiver] =
      new util.ArrayList(clientSockets).iterator()

    while (iterator.hasNext) {
      val client = iterator.next()
      if (!client.isRunning()) {
        toRemove.add(client)
      }

      val timeDiff = System.currentTimeMillis() - client.lastReceivedMessage
      if (TimeUnit.MILLISECONDS.toSeconds(timeDiff) >= CLIENT_REMOVE_TIME_SEC) {
        toRemove.add(client)
        client.stop()
      }
    }

    clientSockets.removeAll(toRemove)
    if (toRemove.size() > 0) {
      logger.info(s"Cleaned up ${toRemove
        .size()} message receiver threads.")
    }
  }
}
class ClientReceiver[T <: Grain: ClassTag](
    val mailboxIndex: ConcurrentHashMap[String, List[Mailbox]],
    port: Int, val registryFactory: Option[RegistryFactory])
    extends Runnable
    with LazyLogging {

  // Create the socket
  val requestSocket: ServerSocket = new ServerSocket(port)

  // Create the timer.
  val timer: Timer = new Timer(
    s"ClientCleaner - ${classTag[T].runtimeClass.getSimpleName}")

  //List of all clients sending/listening for this Grain type.
  val clientSockets: util.List[MessageReceiver] =
    Collections.synchronizedList(new util.ArrayList[MessageReceiver]())

  val SLEEP_TIME: Int = 5
  var running: Boolean = true

  /**
    * While true accept new clients and start their messages thread.
    */
  override def run(): Unit = {
    logger.info(s"Client-Receiver started for ${classTag[T]} on port $port")
    // Clean threads after a while.
    timer.scheduleAtFixedRate(new ClientCleanup(clientSockets), 0, 1000)

    while (running) {
      // Wait for request
      var clientSocket: Socket = null

      try {
        clientSocket = requestSocket.accept
      } catch {
        case socket: SocketException =>
          logger.warn(socket.getMessage)
          return //socket is probably closed, we can exit this method.
      }

      // Create new client when
      val messageReceiver = new MessageReceiver(mailboxIndex, registryFactory, clientSocket)
      val mRecvThread: Thread = new Thread(messageReceiver)
      logger.info(s"Message-Receiver started on ${clientSocket.getPort}.")
      mRecvThread.setName(
        s"Receiver for ${clientSocket.getInetAddress}:${clientSocket.getPort}.")
      mRecvThread.start()

      clientSockets.add(messageReceiver)
    }
  }

  def stop(): Unit = {
    logger.debug("Stopping client-receiver.")
    for (client: MessageReceiver <- clientSockets.asScala) {
      client.stop()
    }
    timer.cancel()
    requestSocket.close()
    this.running = false
  }
}
