package CRUSH.OSD

import CRUSH.OSD.OSDNode.OSDReceivable
import CRUSH.OSD.util.TestInitialization
import CRUSH.controller.Root.{Heartbeat, RootReceivable, RootServiceKey}
import CRUSH.utils.crushmap._
import CRUSH.utils.crushmap.messaging.Status
import CRUSH.{CBorSerializable, Crush}
import akka.actor.typed.receptionist.Receptionist.{Listing, Register, Subscribe}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.remote.transport.ActorTransportAdapter.AskTimeout

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.reflect.io.Path
import scala.util.{Failure, Random, Success}

class OSDState {
  // Set of Actor references to each and every other OSD. May be changed to the discussed behavior.
  var OSDListings: Option[Set[ActorRef[OSDReceivable]]] = None
  var OSDMapping: Option[Map[Int, ActorRef[OSDReceivable]]] = None
  var initialized: Boolean = false
  var crushMap: Option[CrushMap] = None
  var crushPlacementRule: Option[PlacementRule] = None
  var objects: List[CrushObject] = Nil
  // Expresses the mapping of files to different OSDS whenever we are shuffling.
  var objectsToMove: mutable.Map[CrushObject, ListBuffer[Int]] = collection.mutable.Map[CrushObject, ListBuffer[Int]]()
  // Expresses the mapping of OSDs to different Files whenever we are shuffling.
  var sentObjects: mutable.Map[Int, ListBuffer[CrushObject]] = mutable.Map[Int, ListBuffer[CrushObject]]()
}

object OSDNode {

  /**
   * Trait to extend for defining the Behavior of the OSDNode.
   */
  sealed trait OSDReceivable extends CBorSerializable

  /**
   * Case class to cast listing updates from the receptionist service.
   *
   * @param listing Receptionist listing, based on service key that was used to Subscribe.
   */
  case class OSDDiscovery(listing: Receptionist.Listing) extends OSDReceivable

  /**
   * Indicates that sending an object was successful by another actor.
   *
   * @param sender Sender reference to allow a message being sent back.
   * @param files  Files to be sent along, to allow simulated transfer of files.
   */
  case class ObjectsTransferred(sender: ActorRef[OSDReceivable], identifier: Int, files: List[CrushObject])
    extends OSDReceivable

  /**
   * Indicates that sending should be deferred. Either the actor was down, unresponsive or otherwise.
   *
   * @param sender Sender reference to allow a message being sent back.
   * @param files  Files to be sent along, to allow simulated transfer of files.
   */
  case class ObjectsDeferred(sender: ActorRef[OSDReceivable], identifier: Int, files: List[CrushObject])
    extends OSDReceivable

  /**
   * Behavior to allow for the delivery of CrushMaps.
   *
   * @param crushMap      CrushMap to be sent from the root node to the OSD.
   * @param placementRule Placement rule that accompanies the CrushMap, together they define how the OSD should calculate
   *                      the CRUSH placement of existing or new objects.
   */
  case class DeliverCrushMap(crushMap: CrushMap, placementRule: PlacementRule) extends OSDReceivable

  /**
   * Transfer reply based on interaction with another OSD, indicating how the TransferReply
   *
   * @param sender     Sender reference to allow a message being sent back.
   * @param identifier Integer number passed by the sender.
   * @param succeeded  Boolean to allow for different testing scenarios (unused)
   * @param files      List of files that were send by the sender.
   */
  case class TransferReply(sender: ActorRef[OSDReceivable],
                           identifier: Int,
                           succeeded: Boolean,
                           files: List[CrushObject]
                          ) extends CBorSerializable

  /**
   * Initiate object transfer. Note that this may be changed in the future.
   *
   * @param files  Files to be sent along, to allow simulated transfer of files.
   * @param sender Actor reference to which an ACK should be sent back.
   */
  case class InitiateObjectTransfer(files: List[CrushObject], sender: ActorRef[TransferReply]) extends OSDReceivable


  /**
   * Message object to allow an OSD being killed by the root node.
   *
   * @param root Root reference in case message needs to be sent back/sender needs to be verified.
   */
  case class TestKill(root: ActorRef[RootReceivable]) extends OSDReceivable

  // Reference to the root, to be set to the root after receiving information from the Receptionist service
  var rootReference: Option[ActorRef[RootReceivable]] = None;


  val OSDServiceKey: ServiceKey[OSDReceivable] = ServiceKey[OSDReceivable](s"osdService");


  def askFileTransfer(
                       context: ActorContext[OSDReceivable],
                       osdActor: ActorRef[OSDReceivable],
                       objectList: List[CrushObject]
                     ): Unit = {
    implicit val timeout: FiniteDuration = 30.seconds
    context.ask(osdActor, toSendTo => InitiateObjectTransfer(objectList, toSendTo)) {
      // TODO: Implement behavior on ACK or Defer.
      // This must be done in OSD behavior.
      case Success(TransferReply(sender, identifier, succeeded, files)) =>
        if (succeeded) {
          ObjectsTransferred(sender, identifier, files)
        } else {
          ObjectsDeferred(sender, identifier, files)
        }
      case Failure(_) =>
        context.log.error(s"Failed to send a request to actor ${refToIdentity(osdActor)}, creating Defer message!")
        ObjectsDeferred(osdActor, refToIdentity(osdActor), objectList)
    }
  }

  /**
   * Function to handle new CrushMaps, as they might require documents to be reshuffled.
   *
   * @param context     Actor context of the executor.
   * @param message     Message content of the sender (root node).
   * @param newCrushMap The new crushMap to update with.
   * @param state       State object of the OSD.
   * @param status      Status of the OSD actor.
   */
  def initiateFileTransfer(
                            context: ActorContext[OSDReceivable],
                            message: OSDReceivable,
                            newCrushMap: CrushMap,
                            state: OSDState,
                            status: Status
                          ): Unit = {

    val (oldCrushMap, placementRule) = (state.crushMap, state.crushPlacementRule) match {
      case (Some(map), Some(rules)) => (map, rules)
      case _ => return
    }
    context.log.info(
      s"""
         | OldMap: ${oldCrushMap}
         | newMap: ${newCrushMap}""".stripMargin)

    val placementMap = collection.mutable.Map[Int, ListBuffer[CrushObject]]()

    // Check for every object whether they need to be moved.
    state.objects
      .foreach(crushObject => {
        val hash = crushObject.hash
        val oldPlacement = Crush.crush(hash, oldCrushMap, placementRule, crushObject.size)
        val newPlacement = Crush.osdCheckPlacement(hash, newCrushMap, placementRule, crushObject.size, oldPlacement)

        if (newPlacement._1) {
          newPlacement._2
            .map(osdNode => osdNode.asInstanceOf[OSD].id)
            .filter(_ != status.identifier)
            .foreach(osdID => {
              state.objectsToMove += crushObject -> state.objectsToMove
                .getOrElse(crushObject, ListBuffer())
                .addOne(osdID)
              placementMap += osdID -> placementMap.getOrElse(osdID, ListBuffer[CrushObject]()).addOne(crushObject)
            })
        }
      })
    context.log.info(s"New placement map ${placementMap}")
    context.log.info(s"New objectMove map ${state.objectsToMove}")

    // Ask for files to be transferred. Do is a randomized fashion, to prevent actors to send to the same actor.
    Random
      .shuffle(
        placementMap.toList
      )
      .foreach(osd => {
        val (actorID, objectList) = osd
        context.log.info(s"Ask actor ${osd} to receive ${objectList.length}")
        val osdActor = state.OSDMapping.get(actorID)
        askFileTransfer(context, osdActor, objectList.toList)
      })

    state.crushMap = Some(newCrushMap)
  }

  /**
   * Method to update OSD state after a CRUSH map has been received from the root node. This indicates one of two
   * scenarios.
   *  1. The root note has processed the addition of new OSDs **and** we already have some data. This means we have to
   *     perform a shuffle action.
   *
   * 2. The root node has processed **this node** as one of the new OSDs, which means that we should initialize, start
   * sending heartbeats and prepare for other devices to contact us.
   *
   * @param context  Actor context of the executor.
   * @param message  Message content of the sender.
   * @param crushMap CrushMap that was sent by the sender.
   * @return Behavior (to indicate handled behavior).
   */
  def updateOSDState(
                      context: ActorContext[OSDReceivable],
                      message: OSDReceivable,
                      crushMap: CrushMap,
                      OSDState: OSDState,
                      status: Status
                    ): Behavior[OSDReceivable] = {
    OSDState.crushMap match {
      case Some(_) =>
        context.log.info(s"[CSH-MP] [OSD-${status.identifier}] Received CRUSH map update!")
        initiateFileTransfer(context, message, crushMap, OSDState, status)
      case None =>
        context.system.log.info("[CSH-MP] Initialized CRUSH map!")
        OSDState.crushMap = Some(crushMap)
    }
    Behaviors.same
  }

  /**
   * Function to handle an file transfer request. We simulate that files are transferred at the speed of thought.
   *
   * @param context Actor context of the caller (actor).
   * @param message Message object passed by the caller.
   * @param files   Files that were sent to this actor.
   * @param sender  Actor reference to which should be sent.
   * @param state   State object of the OSD.
   * @param status  Status of the OSD actor.
   * @return Behavior (to indicate handled behavior).
   */
  def handleFileTransferRequest(
                                 context: ActorContext[OSDReceivable],
                                 message: OSDReceivable,
                                 files: List[CrushObject],
                                 sender: ActorRef[TransferReply],
                                 status: Status,
                                 state: OSDState
                               ): Behavior[OSDReceivable] = {

    val ownObjects = state.objects.toSet
    val newObjects = files.filter(file => !ownObjects.contains(file))
    val timout = newObjects.map(Random.nextFloat() * _.size).sum
    context.system.log.debug(s"${sender} sent number of files: ${files.length}")
    context.system.log.debug(s"Sleeping $timout milliseconds")
    Thread.sleep(timout.round)
    // Update the available space
    status.availableSpace -= newObjects.foldLeft(0)((accum: Int, file: CrushObject) => accum + file.size)

    // Insert the new objects
    state.objects = state.objects.concat(newObjects)
    // Reply that we were successful in updating
    sender ! TransferReply(context.self, status.identifier, true, files)
    Behaviors.same
  }

  /**
   * Function to handle a response from an actor.
   *
   * @param context ActorContext of the called actor.
   * @param message Message object received from the caller.
   * @param state   State object of the OSD.
   * @param status  Status of the OSD actor.
   * @return Behavior (to indicate handled behavior).
   */
  def handleObjectsTransferredMessage(
                                       context: ActorContext[OSDReceivable],
                                       message: OSDReceivable,
                                       state: OSDState,
                                       status: Status
                                     ): Unit = message match {
    case ObjectsDeferred(sender, identifier, objectList) =>
      context.log.info("Deferred sent objects")
      askFileTransfer(context, sender, objectList)
      Behaviors.same
    case ObjectsTransferred(sender, identifier, objectList) =>
      context.log.info(s"""Sent succesfully ${objectList} to ${identifier}""".stripMargin)
      // Add the objects to the sendObjectsMap
      state.sentObjects += identifier -> state.sentObjects.getOrElse(identifier, ListBuffer()).addAll(objectList)
      // Filter the successfully transferred files from the sending OSDs list
      state.objects = state.objects
        .map((obj: CrushObject) => {
          // Check whether we have sent the object to all the different OSDs according to new mapping
          if (state.objectsToMove.contains(obj)) {
            // In this case we still have to move the file possibly.
            val checkObjectPurgable = state
              .objectsToMove(obj)
              .forall(osd => state.sentObjects.getOrElse(osd, List()).contains(obj))
            (checkObjectPurgable, obj)
          } else {
            // Otherwise we MUST keep the object, as we shouldn't
            (false, obj)
          }

        })
        .filter(tuple => {
          // Bit of a hack, but remove the files from the objects to remove
          if (tuple._1) {
            context.log.info(s"Removing: ${tuple._2}")
            // Removes the files from the objects that we need to move.
            state.objectsToMove.remove(tuple._2)
            // Update the filter object.
            state.sentObjects = state.sentObjects
              .map(osd => (osd._1, osd._2.filterNot(crushObject => crushObject.equals(crushObject))))
            // Check purgable (from previous mapping) already indicated whether the file was theoretically
            // safe to be removed.
            Crush
              .crush(status.identifier, state.crushMap.get, state.crushPlacementRule.get)
              .exists(_.asInstanceOf[OSD].id == status.identifier)
          } else {
            // Cannot yet or at all remove the object, because its not yet received by all recipients
            true
          }
        })
        .map(_._2)
      if (state.objectsToMove.isEmpty) {
        context.log.info(s"Ended with: ${state.objects}")
      }
      Behaviors.same
  }

  def refToIdentity[T](ref: ActorRef[T]): Int = {
    ref.path.toString.split("-").toList.last.split("#").toList.head.toInt

  }

  /**
   * Handler function for the OSD functions, as well as the discovery of different nodes (for now both OSD and Root
   * nodes).
   *
   * @param state  State object of the OSD.
   * @param status Status of the OSD actor.
   * @return Behavior (to indicate handled behavior).
   */
  def OSDBehavior(status: Status, state: OSDState): Behavior[OSDReceivable] = Behaviors.receive { (context, message) => {
    implicit val actorContext: ActorContext[OSDReceivable] = context
    message match {
      case _: ObjectsDeferred =>
        handleObjectsTransferredMessage(context, message, state, status)
        Behaviors.same
      case _: ObjectsTransferred =>
        context.log.info(s"Transferred sent objects")
        handleObjectsTransferredMessage(context, message, state, status)
        Behaviors.same
      // Use OSD ServiceKey Listing adapter to properly map the content to Actor references:
      // https://doc.akka.io/docs/akka/current/typed/actor-discovery.html#receptionist
      case OSDDiscovery(OSDServiceKey.Listing(listing)) =>
        context.system.log.info(s"Received OSD listing update: ${listing}")
        state.OSDListings = Some(listing)
        state.OSDMapping = Some(
          listing
            .map(ref => refToIdentity(ref) -> ref)
            .toMap
        )
        Behaviors.same
      // Use RootServiceKey Listing adapter to properly map the content to Actor references:
      // https://doc.akka.io/docs/akka/current/typed/actor-discovery.html#receptionist
      case OSDDiscovery(RootServiceKey.Listing(listing)) =>
        context.system.log.info("Received ROOT listing update")
        if (listing.nonEmpty) {
          // For now take head of the list. Assumption is that only a single ROOT node will be spun up.
          this.rootReference = Some(listing.head)
        }
        Behaviors.same
      case DeliverCrushMap(crushMap, placementRule) =>
        context.system.log.info("[CSH-MP] Received CRUSH map from ROOT")
        updateOSDState(context, message, crushMap, state, status)
        if (!status.initialized && state.crushPlacementRule.isEmpty) {
          context.system.log.info("Populating the CrushMap")
          state.crushPlacementRule = Some(placementRule)
          state.crushMap = Some(crushMap)
          populateOSD("client.json", state, status)
          context.system.log.debug(state.objects.toString)
          context.system.log.info(s"[OSD-${status.identifier}] now has ${state.objects.size} objects.")
        }
        Behaviors.same
      case InitiateObjectTransfer(files, sender) =>
        handleFileTransferRequest(context, message, files, sender, status, state)
      case TestKill(root) =>
        context.system.log.info(s"Killing node by ${root}")
        System.exit(0)
        Behaviors.stopped(() => println("Whelp, now what"))
      case otherwise =>
        context.log.error(s"Received ${otherwise}, should not occur.")
        Behaviors.unhandled
    }
  }
  }

  /**
   * Populate the OSD with files according to the placement rule and configuration file that was provided. This is done
   * according the file `client.json` in the resource folder. All OSDs execute the Crush algorithm upon the receiving
   * of the first CrushMap to initialize the system to a state that is representative of a warmed up system.
   *
   * @param configFile Path (string) to the configuration file, indicating the different files that should be generated
   *                   according to a psuedo-random configuration.
   * @param state      State object of the OSD, will be updated with the the generated file.
   * @param status     Status of the OSD actor, this should be generated by the caller, to allow for sharing state with
   *                   the scheduled process as as well as message passing.
   */
  def populateOSD(configFile: String, state: OSDState, status: Status)(implicit
                                                                       context: ActorContext[OSDReceivable]
  ): Unit = {
    val initializer = new TestInitialization(Path(configFile))
    val (_, files) = initializer.generateFiles()
    context.log.debug(
      s"""Placing number of objects: ${files.length}
         | ${state.crushMap}
         | ${state.crushPlacementRule}
         |""".stripMargin)
    val objectList = files
      .foldLeft(List[CrushObject]())((objectList, crushObject) => {
        val nodeList = Crush.crush(crushObject.hash, state.crushMap.get, state.crushPlacementRule.get)
        nodeList
          .foreach(osd => state.crushMap.get.root.get.changeSpace(osd.asInstanceOf[OSD].id, crushObject.size))
        nodeList.find(osd => osd.asInstanceOf[OSD].id == status.identifier) match {
          case Some(_) => crushObject :: objectList
          case None => objectList
        }
      })
    state.objects = objectList
    status.initialized = true
    status.availableSpace -= state.objects.foldLeft(0)((a, b) => a + b.size)
    context.log.info(
      s"""OSD ${status.identifier} available space:
         | ${status.availableSpace}
         | ${state.objects.length}""".stripMargin)

  }

  /**
   * Akka context function to handle OSD receivables (which are in effectively requests made by other devices. After
   * completion starts the OSD behaviors
   *
   * @param identifier   OSD identifier (int), should be generated by the experiment generator and provided
   *                     by docker compose.
   * @param totalStorage Total available storage of the system, needed to keep track of the maximum number of files that
   *                     can reside on the actor.
   * @return OSDReceivable behavior, implementing the OSD interactions of the actor.
   */
  def apply(identifier: Int, totalStorage: Int): Behavior[OSDReceivable] = Behaviors.setup[OSDReceivable] { context => {
    val status = Status(totalStorage, identifier, initialized = false)
    context.log.info(s"Initialized OSD with status ${status}")
    val receptionist = context.system.receptionist
    implicit val executionContext: ExecutionContextExecutor = context.system.executionContext

    context.log.info("Register at receptionist as OSD capable machine")
    receptionist ! Register(OSDServiceKey, context.self)

    // Create listing adapters to handle the requests from the OSD and Roots. A message object could be re-used
    context.log.info("Subscribe at receptionist for the Root service key.")
    val rootListingResponseAdapter = context.messageAdapter[Listing](OSDDiscovery)
    receptionist ! Subscribe(RootServiceKey, rootListingResponseAdapter)

    context.log.info("Subscribe at receptionist for the OSD service key.")
    val OSDListingResponseAdapter = context.messageAdapter[Receptionist.Listing](OSDDiscovery)
    receptionist ! Subscribe(OSDServiceKey, OSDListingResponseAdapter)

    context.system.scheduler.scheduleAtFixedRate(0 seconds, 5 seconds)(() => {
      this.rootReference match {
        case Some(ref) =>
          ref ! Heartbeat(context.self, status)
        case None =>
      }
    })
    // Handle the reception of Messages from other actors
    OSDBehavior(status, new OSDState())
  }
  }
}
