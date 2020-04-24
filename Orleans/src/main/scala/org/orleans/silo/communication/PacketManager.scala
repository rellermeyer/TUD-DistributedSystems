package org.orleans.silo.communication

import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress, SocketException}
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.communication.ConnectionProtocol.Packet
import org.orleans.silo.communication.{ConnectionProtocol => protocol}

/**
  * This class takes care of both receiving and sending UDP packets.
  * Packets are received on event-driven.
  */
class PacketManager(listener: PacketListener, port: Int)
    extends Runnable
    with LazyLogging {

  // Socket on which packets are received/send.
  val socket: DatagramSocket = new DatagramSocket(port)

  @volatile
  var running: Boolean = true
  val SLEEP_TIME: Int = 100

  /**
    * Initializes packetmanager, by starting it on its own thread.
    * @param id the (short)-id of its master/slave. This is easy for debugging.
    */
  def init(id: String = UUID.randomUUID().toString.split("-")(0)) = {
    val packetThread = new Thread(this)
    packetThread.setName(s"packetmgr-$id")
    packetThread.start()
  }

  /**
    * Runs the packetmanager loop which receives all packets and forwards it to a listener.
    */
  def run(): Unit = {
    while (running) {
      // The UDP packet we are expecting.
      val bytes = new Array[Byte](protocol.maxPacketBuffer)
      val packet: DatagramPacket = new DatagramPacket(bytes, bytes.length)

      // Try to receive it.
      // Socket might be closed (due to a master/slave shutting down), this will throw the SocketException.
      try {
        socket.receive(packet)
      } catch {
        case e: SocketException => {
          if (running) { // The socket was closed, while we didn't stop the manager. Otherwise we expect this exception.
            logger.error(
              "Socket was closed while PacketManager was still running.")
          }
          return
        }
      }

      // If we receive a packet, we parse it. Otherwise we will log an error.
      val parsedPacket = protocol
        .toPacket(packet.getData)
        .getOrElse({
          logger.error(
            s"Received a packet from ${packet.getAddress.getHostName}/${packet.getPort} but couldn't parse it.")
          return
        })

      // When a packet is received, we trigger the correct event.
      listener.onReceive(parsedPacket,
                         packet.getAddress.getHostAddress,
                         packet.getPort)

      // Now we sleep for a bit.
      Thread.sleep(SLEEP_TIME)
    }
  }

  /**
    * Send an UDP packet.
    * @param packet the packet to send.
    * @param host the host to send to.
    * @param port the port to send to.
    */
  def send(packet: Packet, host: String, port: Int): Unit = {
    if (socket.isClosed) { // If the socket is closed, we can't send any more packets.
      return
    }

    val packetBytes: Array[Byte] =
      protocol.fromPacket(packet).getBytes(StandardCharsets.UTF_8)

    socket.send(
      new DatagramPacket(packetBytes,
                         packetBytes.length,
                         new InetSocketAddress(host, port)))
  }

  /**
    * Will stop the control loop and close the DatagramSocket.
    * Returns if it isn't running.
    */
  def cancel(): Unit = {
    if (!this.running) return
    this.running = false
    socket.close()
  }

}
