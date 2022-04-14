import logicalclocks.LCTimestamp

import ChildActor.{BeginMessage, ControlMessage}
import ParentActor.SpawnActors
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.util.Random

/**
 * Some global settings.
 * - doWork: adds a delay to each ChildActor when it is active, to create a more realistic simulation if needed.
 * - maxWorkTime: Said delay is of maxWorkTime milliseconds.
 * - debug: akka sometimes ignores the loglevel config for some reason so this variables controls the debug logging from
 * here
 */
object Config {
    val doWork: Boolean = false
    val maxWorkTime: Int = 20
    val debug: Boolean = false
}

/**
 * The ChildActor is our main model of a (worker) process in a distributed system.
 */
object ChildActor {
    sealed trait Message

    /**
     * Message that is received from the ParentActor and initializes the ChildActor.
     * @param printBitsizes controls whether the ChildActor will print the sizes of its clocks.
     * @param maxMessagesPerChild after receiving that many messages, the ChildActor will terminate
     * @param peers a list of the other ChildActors
     * @param parentActor reference to the ParentActor
     * @param childIndex a number specifying the index of the ChildActor
     * @param selectedClocks the clocks that will be active and kept track of
     */
    final case class ControlMessage(printBitsizes: Boolean,
                                    maxMessagesPerChild: Int,
                                    peers: List[ActorRef[ChildActor.Message]],
                                    parentActor: ActorRef[ParentActor.Message],
                                    childIndex: Int,
                                    selectedClocks: List[String]
                                   ) extends Message

    /**
     * The main message used for inter-ChildActor communication
     * @param content The content of the message (irrelevant for the simulation)
     * @param timestamps The timestamps of all the clocks that are being kept track of
     */
    final case class PeerMessage(content: String, timestamps: List[LCTimestamp]) extends Message

    /**
     * Special message from the ParentActor that signals the start of the execution. It is only sent after all children
     * have been spawned and initialized.
     */
    final case class BeginMessage() extends Message

    var allPeers: List[ActorRef[Message]] = List()

    /**
     * Broadcast message. It is currently not used but was added for completion.
     * @param peerMessage the message to be sent
     * @param context actor context
     */
    def broadcast(peerMessage: PeerMessage, context: ActorContext[Message]): Unit = {
        // Send to all except self
        for (i <- allPeers) {
            if (i.path.name != context.self.path.name)
                i ! peerMessage
        }
    }

    /**
     * Simulate work by waiting a random amount of milliseconds (no more than maxWorkTime) if doWork is set in Config
     */
    def doWork(): Unit = {
        Thread.sleep(Random.between(1, Config.maxWorkTime))
    }

    def apply(): Behavior[Message] = Behaviors.setup { context =>
        var myIndex: Int = -1
        var messageCounter: Int = 0
        var printBits = false
        var maxMessages = 1
        var parentActor: ActorRef[ParentActor.Message] = null

        var clocks: ClocksWrapper = null
        var selectedClocksList: List[String] = null

        context.log.debug(s"ChildActor ${context.self.path.name} up")

        Behaviors.receive { (context, message) =>
            message match {
                // Upon receiving a ControlMessage from the ParentActor
                case ControlMessage(printBitsizes, maxMessagesPerChild, peers, parent, childIndex, selectedClocks) =>
                    // Store the received configs
                    printBits = printBitsizes
                    allPeers = peers
                    maxMessages = maxMessagesPerChild
                    parentActor = parent
                    myIndex = childIndex
                    selectedClocksList = selectedClocks

                    // Initialize all specified clocks
                    clocks = new ClocksWrapper(myIndex, peers.length, selectedClocksList)

                    context.log.debug(s"${context.self.path.name} received peers $peers")

                // Upon receiving a BeginMessage from the ParentActor
                case BeginMessage() =>
                    // Tick and send an initial message to the first ChildActor
                    clocks.tick()
                    allPeers(1) ! PeerMessage("init msg", clocks.getTimestamps(1))

                // Upon receiving a PeerMessage from another ChildActor
                case PeerMessage(content, timestamps) =>
                    if (Config.debug) {
                        context.log.debug(s"${context.self.path.name} received '$content' with timestamps: ${selectedClocksList.zip(timestamps)}")
                        context.log.debug(s"${selectedClocksList.zip(clocks.getMemSizes)}")
                    }

                    // Merge the timestamps into the clocks and tick them
                    clocks.merge(timestamps)
                    clocks.tick()

                    // If doWork is set, simulate work
                    if (Config.doWork)
                        doWork()

                    // Check that all clocks are consistent
                    if (!clocks.allConsistent(timestamps)) {
                        println("clocks inconsistent")
                        sys.exit(1)
                    }

                    // Tick the clocks and send to a randomly picked ChildActor a new timestamped message
                    clocks.tick()
                    val receivingPeer = Random.between(0, allPeers.length)
                    allPeers(receivingPeer) ! PeerMessage("msg", clocks.getTimestamps(receivingPeer))

                    messageCounter += 1

                    // Log the number of bits of the underlying data-structures. Only the first ChildActor logs this
                    // information to avoid clutter
                    if (printBits && myIndex == 1)
                        println(s"eventnum/bitsize: $messageCounter ${clocks.getMemSizes.sum}")

                    // If the maximum number of messages is reached, send a ChildDone message to the parent.
                    if (messageCounter == maxMessages) {
                        parentActor ! ParentActor.ChildDone()
                    }
            }

            Behaviors.same
        }
    }
}

/**
 * The ParentActor acts as a supervisor. It spawns ChildActors and waits to receive from each of them a
 * message that they are done, and prints the total time this took.
 */
object ParentActor {
    sealed trait Message

    /**
     * Special message that signals the ParentActor to begin spawning the ChildActors
     * @param printBitsizes if true, the sizes of the clocks are printed
     * @param maxMessagesPerChild maximum messages that a child will receive, after this amount it will exit
     * @param nActors number of children in the system
     * @param selectedClocks what types of clocks will each ChildActor keep track of
     */
    final case class SpawnActors(printBitsizes: Boolean,
                                 maxMessagesPerChild: Int,
                                 nActors: Int,
                                 selectedClocks: List[String]) extends Message

    /**
     * Message that a ChildActor sends to the ParentActor to signal that it finished
     */
    final case class ChildDone() extends Message

    def apply(): Behavior[Message] = Behaviors.setup { context =>
        var numberOfChildren = 0
        var childrenDone = 0
        var processList: List[ActorRef[ChildActor.Message]] = null
        val startTime = System.nanoTime

        Behaviors.receive { (context, message) =>
            message match {
                // Upon receiving a message to spawn the ChildActors...
                case SpawnActors(printBitsizes, maxMessagesPerChild, nActors, selectedClocks) =>
                    numberOfChildren = nActors

                    // ...spawn the ChildActors
                    processList = (0 until numberOfChildren)
                      .map(childIndex => context.spawn(ChildActor(), "Process-" + childIndex)).toList

                    // Send relevant information to each ChildActor via a special ControlMessage
                    processList.zip(0 until numberOfChildren).foreach{case (child, childIndex) =>
                        child ! ControlMessage(printBitsizes, maxMessagesPerChild, processList, context.self,
                            childIndex, selectedClocks)}

                    // Send a BeginMessage to the first few children to trigger the message deliveries
                    for (i <- 0 until Math.min(numberOfChildren, 5)) {
                        processList(i) ! BeginMessage()
                    }
                    

                case ChildDone() =>
                    // Keep track of the finished ChildActors
                    // When all ChildActors finish, print total time elapsed and exit.
                    childrenDone += 1
                    if (childrenDone == numberOfChildren) {
                        for (process <- processList) {
                            context.stop(process)
                        }
                        println(s"duration: ${(System.nanoTime - startTime) / 1e9d} seconds")
                        sys.exit(0)
                    }
            }
            Behaviors.same
        }
    }
}

object Main extends App {
    // Random.setSeed(1)

    // Parse command-line arguments
    if (args.length < 4) {
        println("Run with arguments: <print bitsizes: true/false> <number of messages per child: int> <number of actors: int> <clocks separated with commas: str>")
        sys.exit(1)
    }
    val printBitsizes: Boolean = args(0).toBoolean
    val maxMessagesPerChild: Int = args(1).toInt
    val nActors: Int = args(2).toInt
    val selectedClocks: List[String] = args(3).split(",").toList.sorted
    println(s"Starting execution with: $printBitsizes bitsize output, $maxMessagesPerChild messages, $nActors actors and $selectedClocks clocks")

    // Start the actor system
    val parentActor: ActorSystem[ParentActor.SpawnActors] = ActorSystem(ParentActor(), "Main")

    // Send a "SpawnActors" message to ParentActor to spawn children
    parentActor ! SpawnActors(printBitsizes, maxMessagesPerChild, nActors, selectedClocks)
}
