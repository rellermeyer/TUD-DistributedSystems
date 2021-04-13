package CRUSH.controller

import CRUSH.CBorSerializable
import CRUSH.OSD.OSDNode._
import CRUSH.utils.crushmap.CrushMap
import CRUSH.utils.crushmap.messaging.Status
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

sealed trait Phase {

  /**
   * Function implementing the transition to the next state of the Root Execution during experiments.
   *
   * @return Case class implementing the Transition.
   */
  def transition(): Phase
}

final case class StartupState() extends Phase {
  def transition(): Phase = InitializedState()
}

final case class InitializedState() extends Phase {
  def transition(): Phase = RunningState()
}

final case class RunningState() extends Phase {
  override def transition(): Phase = RunningState()
}

final case class RootState(expectedNumOSD: Int, var initialized: Phase = StartupState())

object Root {

  val RootServiceKey: ServiceKey[RootReceivable] = ServiceKey[RootReceivable]("Root")
  var OSDListing: Option[Set[ActorRef[OSDReceivable]]] = None
  var rootStoreUpdated = false

  sealed trait RootReceivable extends CBorSerializable

  /**
   * Case class representation to allow the Root node to be informed of new OSDs that want to be added to the cluster.
   *
   * @param listing Listing object representing the OSDs that are present in the system.
   */
  final case class RootDiscovery(listing: Receptionist.Listing) extends RootReceivable

  /**
   * Case class representing a heartbeat that an OSD actor sends to the root node.
   *
   * @param sender OSD that sent the heartbeat.
   * @param status Status object that provides additional information of the OSD.
   */
  final case class Heartbeat(sender: ActorRef[OSDReceivable], status: Status) extends RootReceivable

  /**
   * Case cass for registering a (series of) OSD devices. Allows for subscribing at the receptionist service.
   *
   * @param servers Collection of actorrefernces to OSDs that registered at the receptionist.
   */
  final case class RegisterOSD(servers: Seq[ActorRef[OSDReceivable]]) extends RootReceivable


  /**
   * Function to handle OSD heartbeats and to start experiments after the system has initialized.
   *
   * @param message Heartbeat message from an OSD.
   * @param sender  OSD reference that sent the heartbeat to the root actor.
   * @param state   State object of the root node.
   * @param context Actor context of the root node during the execution.
   * @return Root behavior.
   */
  def processOSDHeartbeat(message: Heartbeat, sender: ActorRef[OSDReceivable], state: RootState)
                         (implicit context: ActorContext[RootReceivable]): Behavior[RootReceivable] = {
    val Status(availableStorage, identity, initialized) = message.status

    rootStoreUpdated =
      Rootstore.addAlive(identity, sender.path.toString, availableStorage, initialized)(context.system.log)
    context.log.debug(
      s"""Test ${
        Rootstore.isInitialized(
          state.expectedNumOSD
        )
      } - ${state.expectedNumOSD} - ${Rootstore.statusMap.get(true)}
         | """.stripMargin)
    state.initialized match {
      // Setup the experiment...
      case StartupState() if Rootstore.isInitialized(state.expectedNumOSD) =>
        context.log.info("[ROOT] Transitioning to evaluation phase")
        // In this case we can transition to 'initialized state' and
        state.initialized = state.initialized.transition()
        val killedActorRef = OSDListing.get.take(Random.nextInt(OSDListing.size) + 1).toList.last
        val identifier = refToIdentity(killedActorRef)
        context.log.info(
          s"""System was correctly initialized, killing a random OSD.
             | Killing: ${killedActorRef}
             |""".stripMargin)
        killedActorRef ! TestKill(context.self)
        // Send the updated crushMap
        Rootstore.addAlive(identifier, killedActorRef.toString, 0, true, Some(0))(context.system.log)
        RootController.filterMap()
        context.system.log.info(
          s""" Sending the following map!
             |${Rootstore.map.toString}""".stripMargin)
        context.system.log.info(Rootstore.map.toString)
        OSDListing
          .getOrElse(List())
          .foreach(osd => {
            if (osd != killedActorRef)
              osd ! DeliverCrushMap(Rootstore.map, Rootstore.placementRule)
          })
        state.initialized = state.initialized.transition()
      case _ => None
    }
    Behaviors.same
  }

  /**
   * Function to handle OSD subscription after getting notified by the Receptionist service. Note, however, that this
   * is mainly for testing purposes.
   *
   * @param message Message that was adapted from the Receptionist.
   * @param context Root actor actor context.
   * @param servers Sequence of actors that were returned by the Receptionist listings.
   * @return Root behavior.
   */
  def processOSDRegistration(message: RootReceivable,
                             context: ActorContext[RootReceivable],
                             servers: Seq[ActorRef[OSDReceivable]]
                            ): Behavior[RootReceivable] = {
    context.log.info(s"[ROOT] [RGSTR] Received registration requests")
    // This method was left empty intentionally.


    Behaviors.same
  }

  /**
   * Function implementing the normal behavior of the Root actor. Whenever the number of HeartBeats received by this
   * actor that indicate being ready, the state object will be updated to ensure that the 'new' CrushMap gets send.
   *
   * @param state Shared root state object between the 'behavior'
   * @return
   */
  def rootBehavior(state: RootState): Behavior[RootReceivable] =
    Behaviors.receive { (context, message) =>
      message match {
        case RootDiscovery(OSDServiceKey.Listing(listing)) =>
          context.system.log.info(
            s"""
               | Root received OSD listing update. Currently: ${listing.size} OSDs of ${state.expectedNumOSD}
                            """.stripMargin)
          if (listing.nonEmpty) {
            this.OSDListing = Some(listing)
            // Update the initialized state of the system, to continue the
          }
          Behaviors.same
        case message@Heartbeat(sender, _) =>
          context.log.info(s"[ROOT] [HRT-BT] Received heartbeat from: ${sender.path}, ${message.status.availableSpace}")
          processOSDHeartbeat(message, sender, state)(context)
        case RegisterOSD(servers) => processOSDRegistration(message, context, servers)
      }
    }

  /**
   * Function to startup the root actor.
   *
   * @return Root behavior to be used during the execution of the actor.
   */
  def apply(expectedNumOSD: Int): Behavior[RootReceivable] = Behaviors.setup[RootReceivable] { context => {
    val rootState = RootState(expectedNumOSD = expectedNumOSD)
    context.system.log.info(s"Registering ROOT node as root: ${context.self}")
    context.system.receptionist ! Receptionist.Register(RootServiceKey, context.self)

    // Register at the receptionist to retrieve a reference to the Root node in the cluster
    context.system.log.info(s"Subscribing to OSD nodes node as root: ${context.self}")
    val rootResponseAdapter = context.messageAdapter[Receptionist.Listing](RootDiscovery)
    context.system.receptionist ! Receptionist.Subscribe(OSDServiceKey, rootResponseAdapter)

    // Create implicit value of context to implicitly pass scheduler.
    implicit val executionContext: ExecutionContextExecutor = context.executionContext

    // Every 30 seconds we send a message with the current crush map whenever the nodes were updated.
    context.system.scheduler.scheduleAtFixedRate(30 seconds, 30 seconds)(() => {
      RootController.filterMap()
      if (this.rootStoreUpdated) {
        OSDListing
          .getOrElse(List())
          .foreach(
            /**
             * Function to send the CrushMap to a CrushMap. Sends the CrushMap of the system
             */
            (actorRef) => {
              rootState.initialized match {
                // If not yet initialized (i.e. not yet the correct number of OSDs have indicated to have started),
                // we send the configuration map as not to confuse them.
                case StartupState() =>
                  actorRef ! DeliverCrushMap(Rootstore.configMap, Rootstore.placementRule)
                // When the system has been updated we can send the current crushMap of the system. As such,
                // a simulated IN/OUT node will be handled implicitly by the system.
                case _ =>
                  None
              }
            }
          )
      }
    })
    rootBehavior(rootState)
  }
  }

}
