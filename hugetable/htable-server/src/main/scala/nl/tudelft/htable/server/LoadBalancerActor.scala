package nl.tudelft.htable.server

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, DispatcherSelector}
import akka.stream.Materializer
import akka.stream.typed.scaladsl.ActorSink
import akka.util.{ByteString, Timeout}
import nl.tudelft.htable.client.HTableInternalClient
import nl.tudelft.htable.client.impl.MetaHelpers
import nl.tudelft.htable.core.TabletState.TabletState
import nl.tudelft.htable.core._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * A load balancer rebalances the unassigned tablets to the active tablet servers.
 */
object LoadBalancerActor {

  /**
   * Commands that are accepted by the [LoadBalancer].
   */
  sealed trait Command

  /**
   * A message to start a load balancing cycle.
   */
  final case class Schedule(nodes: Set[Node], shouldInvalidate: Boolean = false) extends Command

  /**
   * Received when a node reports its tablets.
   */
  private final case class NodeReport(node: Node, tablets: Set[Tablet]) extends Command

  /**
   * Received when we fail to receive a response from a [NodeManager].
   */
  private final case class NodeFailure(node: Node, failure: Throwable) extends Command

  /**
   * Received when we receive a row from a node.
   */
  private case class NodeRow(node: Node, row: Row) extends Command

  /**
   * Received when we receive all rows from a node.
   */
  private case class NodeComplete(node: Node) extends Command

  /**
   * Received when the assignment phase failed.
   */
  private case class AssignFailed(failure: Throwable) extends Command

  /**
   * Received when the assignment phase succeeded.
   */
  private case object AssignSucceeded extends Command

  /**
   * Construct the behavior for the load balancer.
   *
   * @param client The client to communicate with the other nodes.
   * @param policy The load balancing policy.
   */
  def apply(client: HTableInternalClient, policy: LoadBalancerPolicy): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting load balancer")
    idle(client, policy)
  }

  /**
   * Construct the behavior for an idle load balancer.
   *
   * @param client The client to communicate with the other nodes.
   * @param policy The load balancing policy.
   */
  private def idle(client: HTableInternalClient, policy: LoadBalancerPolicy): Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Schedule(nodes, shouldInvalidate) => running(client, policy, nodes, shouldInvalidate)
    }

  /**
   * Construct the behavior for a load balancing cycle.
   *
   * @param client The client to communicate with the other nodes.
   * @param policy The load balancing policy.
   * @param nodes The nodes to load reconstruct over.
   */
  private def running(client: HTableInternalClient,
                      policy: LoadBalancerPolicy,
                      nodes: Set[Node],
                      shouldInvalidate: Boolean): Behavior[Command] = Behaviors.setup { context =>
    // asking someone requires a timeout if the timeout hits without response
    // the ask is failed with a TimeoutException
    implicit val timeout: Timeout = 3.seconds
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.default())

    context.log.info(s"Starting load balancing cycle [nodes=$nodes, invalidate=$shouldInvalidate]")

    nodes.foreach { node =>
      context.pipeToSelf(client.report(node)) {
        case Success(value)     => NodeReport(node, value.toSet)
        case Failure(exception) => NodeFailure(node, exception)
      }
    }

    val responses = mutable.HashMap[Node, Set[Tablet]]()

    Behaviors.receiveMessagePartial {
      case NodeReport(node, tablets) =>
        context.log.debug(s"Received pong from $node [$tablets]")
        responses(node) = tablets

        if (responses.size == nodes.size)
          reconstruct(client, policy, nodes, responses.find(_._2.exists(Tablet.isRoot)).map(_._1), shouldInvalidate)
        else
          Behaviors.same
      case NodeFailure(node, _) =>
        responses(node) = Set.empty
        if (responses.size == nodes.size)
          reconstruct(client, policy, nodes, responses.find(_._2.exists(Tablet.isRoot)).map(_._1), shouldInvalidate)
        else
          Behaviors.same
      case Schedule(nodes, shouldInvalidate) => running(client, policy, nodes, shouldInvalidate)
    }
  }

  /**
   * Construct the behavior for reconstructing the metadata table over the nodes.
   *
   * @param client The client to communicate with the other nodes.
   * @param policy The load balancing policy.
   * @param nodes The set of nodes that may be included in the scheduling cycle.
   * @param shouldInvalidate A flag to indicate whether we should reassign all tablets.
   */
  private def reconstruct(client: HTableInternalClient,
                          policy: LoadBalancerPolicy,
                          nodes: Set[Node],
                          root: Option[Node],
                          shouldInvalidate: Boolean): Behavior[Command] = Behaviors.setup { context =>
    implicit val timeout: Timeout = 3.seconds
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.blocking())
    implicit val mat: Materializer = Materializer(context.system)

    context.log.debug("Scanning METADATA table")

    // Start load balancing cycle
    policy.startCycle(nodes)

    // Map all nodes to their unique identifiers
    val uidToNode = nodes.map(node => (node.uid, node)).toMap

    // If no one (active) is currently hosting the root METADATA tablet, make sure that we assign
    val rootNode = root match {
      case Some(node) =>
        node
      case None =>
        val selectedNode = policy.select(Tablet.root)
        context.log.debug(s"Assigning root METADATA tablet to $selectedNode")
        client.assign(selectedNode, Seq(AssignAction(Tablet.root, AssignType.Add)))
        selectedNode
    }

    val previousAssignments = mutable.TreeMap[Tablet, (TabletState, Option[Node])]()
    val newAssignments = mutable.TreeMap[Tablet, Node]()

    val queue = mutable.ArrayDeque[Tablet]()
    val queriedNodes = mutable.HashSet[Node]() // Nodes that have been queried

    // Query the root node
    query(rootNode)

    /**
     * Query the specified [Node] for the metadata table.
     */
    def query(node: Node): Unit = {
      context.log.debug(s"Querying $node for METADATA")
      queriedNodes += node
      val sink = ActorSink.actorRef[Command](context.self,
                                             onCompleteMessage = NodeComplete(node),
                                             onFailureMessage = NodeFailure(node, _))
      client
        .read(node, Scan("METADATA", RowRange.unbounded))
        .map(row => NodeRow(node, row))
        .runWith(sink)
    }

    /**
     * Process the specified queue by querying the relevant nodes for the meta tablet or assigning the tablets and
     * then performing a query.
     */
    def processQueue(): Unit = {
      while (queue.nonEmpty) {
        val tablet = queue.removeHead()
        if (Tablet.isRoot(tablet)) {
          // We handle the root METADATA tablet in a special manner: we need to assign the root node before we are able
          // to find its bounds.
          newAssignments(tablet) = rootNode

          context.log.trace(s"Skipping query on $tablet: already queried")
        } else if (Tablet.isMeta(tablet)) {
          // For other METADATA tablets, find out whether it is currently assigned (and if not assign it to some node)
          // and query its rows.
          val node = previousAssignments(tablet)._2 match {
            case Some(value) => value
            case None =>
              val node = policy.select(tablet)
              context.log.debug(s"Assigning $tablet to $node")
              client.assign(node, Seq(AssignAction(Tablet.root, AssignType.Add)))
              newAssignments(tablet) = node
              node
          }

          if (!queriedNodes.contains(node)) {
            query(node)
          } else {
            context.log.trace(s"Skipping query on $tablet: already queried")
          }
        } else {
          context.log.trace(s"Skipping query on $tablet: not METADATA")
        }
      }
    }

    Behaviors.receiveMessagePartial {
      case Schedule(nodes, shouldInvalidate) =>
        policy.endCycle()
        running(client, policy, nodes, shouldInvalidate)
      case NodeRow(_, row) =>
        context.log.trace(s"Received row $row")
        MetaHelpers.readRow(row) match {
          case Some((tablet, state, uid)) =>
            val node = uid.flatMap(uidToNode.get)

            context.log.debug(s"Discovered $tablet $state on $node")

            if (state != TabletState.Closed) {
              previousAssignments.put(tablet, (state, node))
              queue += tablet
            }
          case None =>
            context.log.error(s"Failed to parse meta row $row")
        }
        Behaviors.same
      case NodeComplete(node) =>
        if (queue.isEmpty && node == rootNode) {
          context.log.info(s"Discovered no tablets on $node: Initializing METADATA")
          // If the root node does not contain any rows, it means that the METADATA table is not populated. Therefore,
          // we initialize it here.
          updateMeta(client, rootNode, Tablet.root, rootNode, AssignType.Create)

          context.log.info("Scheduling cycle completed")
          policy.endCycle()
          idle(client, policy)
        } else {
          context.log.debug(s"Queued ${queue.size} METADATA rows")
          processQueue()

          if (queue.isEmpty) {
            assign(client, policy, previousAssignments, newAssignments, shouldInvalidate)
          } else {
            context.log.trace(s"Queue is not yet empty: wait")
            Behaviors.same
          }
        }
      case NodeFailure(_, ex) =>
        context.log.error("Load balancer failed", ex)
        policy.endCycle()
        idle(client, policy)
    }
  }

  /**
   * Construct the behavior for assigning the tablets to the nodes.
   *
   * @param client The client to communicate with the other nodes.
   * @param policy The load balancing policy.
   * @param previousAssignments The previous assignments used.
   * @param newAssignments The assignments that were already done for METADATA tablets.
   * @param shouldInvalidate A flag to indicate whether we should reassign all tablets.
   */
  private def assign(client: HTableInternalClient,
                     policy: LoadBalancerPolicy,
                     previousAssignments: mutable.TreeMap[Tablet, (TabletState, Option[Node])],
                     newAssignments: mutable.TreeMap[Tablet, Node],
                     shouldInvalidate: Boolean): Behavior[Command] = Behaviors.setup { context =>
    implicit val timeout: Timeout = 3.seconds
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.blocking())

    val n = previousAssignments.count(p => if (shouldInvalidate) true else p._2._2.isEmpty)
    context.log.debug(s"Assigning $n tablets (${newAssignments.size} already assigned)")
    context.log.trace(s"Found previous assignments: $previousAssignments")

    // Aggregate the tablets by the node they are currently assigned to and report this to the load balancing policy
    val previousNodeTablets = previousAssignments
      .flatMap { case (tablet, (_, node)) => node.map(node => (tablet, node)) }
      .groupMapReduce(_._2)(p => Set(p._1))(_ ++ _)
    policy.discover(previousNodeTablets)

    // Assign each tablet to a node
    for ((tablet, (_, nodeOpt)) <- previousAssignments if !newAssignments.contains(tablet)) {
      val node = nodeOpt.filter(_ => !shouldInvalidate).getOrElse(policy.select(tablet))
      newAssignments(tablet) = node
    }

    // Obtain the new node assignments and inform the nodes
    val futures = newAssignments
      .groupMapReduce(_._2)(p => Set(p._1))(_ ++ _)
      .map {
        case (node, tablets) =>
          val flushActions = previousAssignments.keySet.diff(tablets).toSeq.map(tablet => AssignAction(tablet, AssignType.Remove))
          val newActions = tablets.map { tablet =>
            val actionType = previousAssignments(tablet)._1 match {
              case TabletState.Created => AssignType.Create
              case TabletState.Served => AssignType.Add
              case TabletState.Unassigned => AssignType.Add
              case TabletState.Closed => AssignType.Remove
              case TabletState.Deleted => AssignType.Delete
            }
            AssignAction(tablet, actionType)
          }.toSeq
          val actions = flushActions ++ newActions

          actions.foreach { action =>
            context.log.info(s"Assign ${action.tablet} (${action.action}) to ${node}")
          }

          client.assign(node, actions).flatMap { _ =>
            val futures = newActions.map { action =>
              val tablet = action.tablet
              // Update metadata tablet to reflect the assignment
              val key = ByteString(tablet.table) ++ tablet.range.start
              val metaNode =
                if (Tablet.isRoot(tablet))
                  node
                else
                  newAssignments.rangeTo(Tablet("METADATA", RowRange.leftBounded(key))).last._2

              val prev = previousAssignments.get(tablet)
              // Only update meta if the node that is hosting the tablet has changed.
              if (!prev.exists(_._2.contains(node)) || prev.exists(_._1 != TabletState.Served)) {
                context.log.info(s"Updating METADATA for $tablet on $node")
                updateMeta(client, metaNode, tablet, node, action.action)
              } else {
                context.log.debug(s"Skipping ${tablet}: already assigned to $node")
                Future.successful(Done)
              }
            }
            Future.sequence(futures)
          }
      }
    context.pipeToSelf(Future.sequence(futures)) {
      case Failure(exception) => AssignFailed(exception)
      case Success(_)         => AssignSucceeded
    }

    Behaviors.receiveMessagePartial {
      case Schedule(nodes, shouldInvalidate) =>
        policy.endCycle()
        running(client, policy, nodes, shouldInvalidate)
      case AssignSucceeded =>
        context.log.info("Scheduling cycle completed")
        policy.endCycle()
        idle(client, policy)
      case AssignFailed(exception) =>
        context.log.error("Assignment phase failed", exception)
        policy.endCycle()
        idle(client, policy)
    }
  }

  /**
   * Update the METADATA table for the specified tablet.
   */
  private def updateMeta(client: HTableInternalClient,
                         metaNode: Node,
                         tablet: Tablet,
                         node: Node,
                         action: AssignType.Type): Future[Done] = {
    val mutation =
      action match {
        case AssignType.Add => MetaHelpers.writeExisting(tablet, TabletState.Served, Some(node))
        case AssignType.Create => MetaHelpers.writeNew(tablet, TabletState.Served, Some(node))
        case AssignType.Delete => RowMutation("METADATA", ByteString(tablet.table) ++ tablet.range.start).delete()
        case _ => throw new IllegalArgumentException()
      }

    // Assign the tablet to the chosen node
    client.mutate(metaNode, mutation)
  }
}
