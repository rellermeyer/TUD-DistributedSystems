package nl.tudelft.globule

import akka.actor.{Actor, ActorRef, ActorSelection, FSM, Props}
import nl.tudelft.globule.ReplicationManager.ReplicaNegotiated

case class Resources(
                      storage: Int,
                      bandwidth: Int,
                      duration: Int
                    )

sealed trait NegotiationState

case object Start extends NegotiationState

case object ResourceMaster extends NegotiationState

case object ResourceSlave extends NegotiationState

case object Price extends NegotiationState

case object Abort extends NegotiationState

case object Finish extends NegotiationState

case object EndGame extends NegotiationState

sealed trait NegotiationData

case object UninitializedData extends NegotiationData

final case class NegotiateWithTargets(targets: List[ActorSelection], resources: Resources) extends NegotiationData

final case class SendRequestResources(spec: Resources) extends NegotiationData

final case class RequestResources(spec: Resources) extends NegotiationData

final case class ResourcePrice(spec: Resources, price: Double) extends NegotiationData

final case class Accept(serverName: String) extends NegotiationData

final case class StartNegotiation(reference: ActorRef) extends NegotiationData

final case class Terminate() extends NegotiationData

final case class Unhandled() extends NegotiationData

final case class SendResourceManager(resourceManagerRef: ActorRef, slaveLatlong: Location, slaveWebserverRemote: NetworkManager.RemoteAddress, servername: String) extends NegotiationData

final case class AllocateResources(serverName: String) extends NegotiationData

final case class SelectServer() extends NegotiationData

final case class Abort() extends NegotiationData

final case class SetQuantifiers(s: Double, b: Double, d: Double) extends NegotiationData


class Negotiator(latlong: Location, webserverRemote: NetworkManager.RemoteAddress, servername: String, resourceManagerAPI: ActorRef) extends Actor with FSM[NegotiationState, NegotiationData] {
  var refs: List[ActorSelection] = _
  var resourcePrice: Int = 0
  var neededResponses = 0
  var lowestPrice: Double = Double.PositiveInfinity
  var selectedServer: ActorSelection = _
  var specs: Resources = _
  var requestingReplicationManager: ActorRef = _
  var storageQuantifier: Double = 1
  var bandwidthQuantifier: Double = 1
  var durationQuantifier: Double = 1

  def gotAllResponses: Boolean = {
    neededResponses <= 0
  }

  def calculateResourcePrice(spec: Resources): Double = {
    ((spec.storage * storageQuantifier) + (spec.bandwidth * bandwidthQuantifier)) * (spec.duration * durationQuantifier)
  }

  def addPrice(price: Double, ref: ActorSelection): Unit = {
    if (price < lowestPrice) {
      lowestPrice = price
      selectedServer = ref
    }
  }

  //  def lowerResponses
  def multicast(references: List[ActorSelection], message: Any): Unit = {
    log.info(s"Multicasting $message to references $references")
    references.foreach(_ ! message)
  }

  startWith(Start, UninitializedData)

  when(Start) {
    case Event(NegotiateWithTargets(targets, spec), UninitializedData) =>

      requestingReplicationManager = sender()

      refs = targets
      neededResponses = refs.size

      specs = spec
      // put slaves in negotiation state
      multicast(refs, StartNegotiation)
      // request price from slaves by multicasting the resource requirements
      multicast(refs, RequestResources(spec))
      log.info(s"Negotiation manager Master changing state to ResourceMaster")

      goto(ResourceMaster)
    case Event(SetQuantifiers(s, b, d), _) =>
      storageQuantifier = s
      bandwidthQuantifier = b
      durationQuantifier = d
      stay
    case Event(StartNegotiation, _) =>
      log.info(s"Negotiation manager Slave starting negotiation")
      goto(ResourceSlave)
    case Event(Unhandled, _) =>
      stay
  }

  when(ResourceMaster) {
    case Event(ResourcePrice(spec, price), _) =>
      log.info(s"Negotiation manager Master Got resource price")
      addPrice(price, ActorSelection(sender, ""))
      neededResponses -= 1
      if (gotAllResponses) {
        println(s"The lowest price is $lowestPrice and comes form the server at $selectedServer")
        goto(EndGame) using SelectServer()
      }
      else
        stay

    case Event(Unhandled, _) =>
      neededResponses -= 1

      log.info(s"Got unhandled event from slave")

      if (gotAllResponses) {
        if (selectedServer == null) {
          log.info(s"No selected server, go to start")
          goto(Start)
        } else {
          println(s"The lowest price is $lowestPrice and comes form the server at $selectedServer")
          goto(EndGame) using SelectServer()
        }
      }
      else
        stay
  }

  when(ResourceSlave) {
    case Event(RequestResources(spec), _) =>
      log.info(s"Negotiation manager Slave => master requested resource price")
      specs = spec
      sender ! ResourcePrice(spec, calculateResourcePrice(spec))
      stay
    case Event(Accept(serverName), _) =>
      log.info(s"Negotiation manager Slave  => I got accepted as the replica server")

      goto(EndGame) using AllocateResources(serverName)
    case Event(Abort, _) =>
      println(s"${self.path.name} I got an abort")
      goto(Start)
    case Event(Unhandled, _) =>
      stay
  }

  when(EndGame) {
    case Event(SendResourceManager(ref: ActorRef, slaveLatlong: Location, slaveWebserverRemote: NetworkManager.RemoteAddress, slaveServername: String), _) =>

      val negotiatedMessage = ReplicaNegotiated(new FileServer(slaveLatlong, ref, slaveWebserverRemote, false, slaveServername), specs)
      requestingReplicationManager ! negotiatedMessage

      println(s"${self.path.name} got a reference to a resource manager. This node will got init")
      sender ! Terminate()
      goto(Start)
    case Event(Terminate(), _) =>
      println(s"${self.path.name} will got init")
      goto(Start)
    case Event(Unhandled, _) =>
      stay

  }

  onTransition {
    case _ -> EndGame =>
      nextStateData match {
        case SelectServer() =>
          log.info(s"Negotiation manager Master selecting the final server")
          // Message Selected Server to accept
          selectedServer ! Accept(servername)
          // Message all other servers to abort
          log.info(s"Negotiation manager Master aborting the rest of the server")
          multicast(refs.filter(_ != selectedServer), Abort)

        case AllocateResources(sendingServerName: String) =>

          // Add servername to resourceManager
          resourceManagerAPI ! AddRemoteAddress(sendingServerName)
          sender ! SendResourceManager(resourceManagerAPI, latlong, webserverRemote, servername)
        case _ =>
      }
  }

  whenUnhandled {
    case Event(x, data) =>
      log.warning("Received unhandled event: " + x + " with data " + data)
      // check if we are slave
      if (requestingReplicationManager == null) {
        sender ! Unhandled()
      }
      stay
  }
}

object Negotiator {
  def props(latlong: Location, webserverRemote: NetworkManager.RemoteAddress, servername: String, resourceManager: ActorRef):
  Props = Props(new Negotiator(latlong, webserverRemote, servername, resourceManager))
}