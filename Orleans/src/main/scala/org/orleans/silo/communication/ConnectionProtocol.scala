package org.orleans.silo.communication
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
  * Stores all (meta-)data for the communication protocol.
  */
object ConnectionProtocol {

  // A set of packet types.
  object PacketType {
    val HEARTBEAT = "heartbeat"
    val HANDSHAKE = "handshake"
    val WELCOME = "welcome"
    val SHUTDOWN = "shutdown"
    val SLAVE_CONNECT = "slave_connect"
    val SLAVE_DISCONNECT = "slave_disconnect"
    val METRICS = "metrics"
  }

  // Data we keep track of in our master and slaves.
  case class MasterInfo(uuid: String, lastHeartbeat: Long)
  case class SlaveInfo(uuid: String,
                       host: String,
                       udpPort: Int,
                       tcpPort: Int,
                       lastHeartbeat: Long = -1,
                       var totalLoad: Int = 0,
                       var grainCount:AtomicInteger = new AtomicInteger(0),
                      )

  // The interval for which heart beats are sent.
  val heartbeatInterval: Long = 1000

  // The interval for which slave reports load of its grains.
  val metricsInterval: Long = 1000

  // Time before we consider a silo dead.
  val deathTime: Long = 5 * heartbeatInterval

  // Handshake connection attempts
  val connectionAttempts: Int = 5

  // Handshake connection delay (i.e. after how many ms do we retry the handshake)
  val connectionDelay: Long = 1000

  // Separator for packet fields.
  val packetSeparator: Char = ','

  // Max byte buffer of a packet.
  val maxPacketBuffer: Int = 1024

  // Simple structure to keep packet data.
  case class Packet(packetType: String,
                    uuid: String,
                    timestamp: Long,
                    data: List[String] = List())

  /**
    * Parses packet from String to Packet.
    * @param packet the packet to parse.
    * @return Option[Packet] otherwise None if it can't be parsed.
    */
  def toPacket(packet: String): Option[Packet] =
    packet.split(packetSeparator).toList match {
      case ty :: uuid :: timestamp :: tail
          if parseLong(timestamp.trim()) != None =>
        Some(
          Packet(ty.trim(),
                 uuid.trim(),
                 parseLong(timestamp.trim()).get,
                 tail.map(_.trim)))
      case _ => None // Couldn't parse this packet
    }

  /**
    * Parses packet from byte array to Packet.
    * @param packet the packet to parse.
    * @return Option[Packet] otherwise None if it can't be parsed.
    */
  def toPacket(packet: Array[Byte]): Option[Packet] =
    toPacket(new String(packet, StandardCharsets.UTF_8))

  /**
    * Transforms packet into a String using the packetSeparator.
    * @param packet the packet to stringify.
    * @return a stringified packet.
    */
  def fromPacket(packet: Packet): String =
    packet.packetType + packetSeparator + packet.uuid + packetSeparator + packet.timestamp + packetSeparator + packet.data
      .mkString(packetSeparator.toString)

  /** Helper function to parse a Long. **/
  def parseLong(s: String) = try { Some(s.toLong) } catch { case _ => None }

  /** Retrieves the first part of a UUID. **/
  def shortUUID(uuid: String) = uuid.split("-")(0)
}
