package CRUSH.utils.crushmap.messaging

import CRUSH.CBorSerializable

/**
 * Simple Status class to allow extension of sending heartbeat without having to change the function signatures.
 *
 * @param availableSpace Integer representation of the available space of the cluster.
 */
case class Status(var availableSpace: Int, identifier: Int, var initialized: Boolean) extends CBorSerializable
