package Messaging

import Messaging.GuaranteeType.GuaranteeType

trait MessageType

@SerialVersionUID(1L)
case class Subscribe(subscription: Subscription, guarantee: GuaranteeType) extends Serializable with MessageType
@SerialVersionUID(1L)
case class Unsubscribe(subscription: Subscription, guarantee: GuaranteeType) extends Serializable with MessageType

@SerialVersionUID(1L)
case class Advertise(advertisement: Advertisement, guarantee: GuaranteeType) extends Serializable with MessageType
@SerialVersionUID(1L)
case class Unadvertise(advertisement: Advertisement, guarantee: GuaranteeType) extends Serializable with MessageType

@SerialVersionUID(1L)
case class AckResponse(messageType: String, ID: (Int, Int), var timeout: Boolean = false) extends Serializable with MessageType

@SerialVersionUID(1L)
case class Publish(publication: Publication, guarantee: GuaranteeType) extends Serializable with MessageType