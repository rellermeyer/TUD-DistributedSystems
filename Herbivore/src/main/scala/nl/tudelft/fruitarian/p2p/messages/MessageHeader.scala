package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.Address

case class MessageHeader(messageType: String, var from: Address, to: Address)
