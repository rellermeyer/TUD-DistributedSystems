package org.orleans.silo.communication
import org.orleans.silo.communication.ConnectionProtocol.Packet

/**
  * Listener to process packets.
  */
trait PacketListener {

  /** This event is triggered when a Packet is received. */
  def onReceive(packet: Packet, host: String, port: Int)
}
