package hyperdex

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import hyperdex.API.AttributeMapping
import hyperdex.DataNode.AcceptedMessage
import hyperdex.MessageProtocol._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object GatewayNode {

  /** configuration messages **/
  sealed trait RuntimeMessage extends GatewayMessage
  // to discover receivers
  private final case class AllReceivers(receivers: Set[ActorRef[AcceptedMessage]]) extends RuntimeMessage
  private final case class CreateSuccess(tableName: String, newHyperspace: HyperSpace) extends RuntimeMessage

  def actorBehavior(numDataNodes: Int): Behavior[GatewayMessage] = {
    Behaviors.setup { ctx =>
      ctx.log.info("Subscribing to receptionist for receiver nodes...")
      ctx.system.receptionist ! Receptionist.subscribe(DataNode.receiverNodeKey, getReceiverAdapter(ctx))
      starting(ctx, numDataNodes, Set.empty)
    }
  }

  private def getReceiverAdapter(ctx: ActorContext[GatewayMessage]): ActorRef[Receptionist.Listing] = {
    ctx.messageAdapter[Receptionist.Listing] {
      case DataNode.receiverNodeKey.Listing(receivers) =>
        AllReceivers(receivers)
    }
  }

  /**
    * stage of resolving all data nodes
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def starting(
    ctx: ActorContext[GatewayMessage],
    requiredAmountDataNodes: Int,
    dataNodes: Set[ActorRef[DataNode.AcceptedMessage]]
  ): Behavior[GatewayMessage] = {

    Behaviors
      .receiveMessage {
        case AllReceivers(newReceivers) =>
          if (newReceivers.size < requiredAmountDataNodes) {
            ctx.log.info(s"Not enough receivers, we have ${newReceivers.size} out of $requiredAmountDataNodes.")
            starting(ctx, requiredAmountDataNodes, newReceivers)
          } else {
            ctx.log.info(s"We have ${newReceivers.size} receivers, so lets start running.")
            // datanodes receive random position in sequence, so random IDs from 0..requiredAmountDataNodes-1
            running(ctx, Map(), newReceivers.toSeq)
          }
        case _ =>
          ctx.log.info(s"The gateway first needs enough receivers.")
          Behaviors.same
      }
  }

  /**
    * the runtime behavior after all setup has completed
    * @param ctx
    * @param dataNodes
    * @return
    */
  private def running(
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  ): Behavior[GatewayMessage] = {

    implicit val system: ActorSystem[Nothing] = ctx.system
    implicit val timeout: Timeout = 5.seconds
    implicit val ec: ExecutionContext = ctx.executionContext

    Behaviors
      .receiveMessage {
        case create @ Create(from, table, attributes) =>
          ctx.log.info(s"Received Create table ${table} from ${from}")
          val newHyperspace = new HyperSpace(attributes, dataNodes.size, 2)
          handleValidCreate(create, ctx, newHyperspace, dataNodes)
          Behaviors.same
        case tableQuery: TableQuery =>
          handleTableQuery(tableQuery, ctx, hyperspaces, dataNodes)
          Behaviors.same
        case createSuccess: CreateSuccess =>
          val name = createSuccess.tableName
          val hyperspace = createSuccess.newHyperspace
          running(ctx, hyperspaces.+((name, hyperspace)), dataNodes)
        // shouldn't receive any except create result which is ignored
        case _: DataNodeResponse =>
          Behaviors.same
        // should not happen (if it does, system is broken)
        case _: AllReceivers =>
          Behaviors.same
      }
  }

  /**
    * process a put/get/search by routing to the correct datanodes
    * and potentially merging results
    * @param query
    * @param ctx
    * @param hyperspaces
    * @param dataNodes
    */
  private def handleTableQuery(
    query: TableQuery,
    ctx: ActorContext[GatewayMessage],
    hyperspaces: Map[String, HyperSpace],
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {
    // implicits needed for ask pattern

    query match {
      case lookup @ Lookup(from, table, key) =>
        ctx.log.info(s"Received Get key ${key} in table${table} from ${from}")
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            handleValidLookup(lookup, ctx, hyperspace, dataNodes)
          case None =>
            from ! LookupResult(Left(TableNotExistError))
        }
      case search @ Search(from, table, mapping) =>
        ctx.log.info(s"Received Search table ${table} from ${from}")
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            // if mapping contains invalid attributes
            if (!mapping.keys.map(x => hyperspace.attributes.contains(x)).forall(x => x))
              from ! SearchResult(Left(InvalidAttributeError(mapping.keys.toSet.diff(hyperspace.attributes.toSet))))
            handleValidSearch(search, ctx, hyperspace, dataNodes)

          case None =>
            from ! SearchResult(Left(TableNotExistError))
        }
      case put @ Put(from, table, key, mapping) =>
        ctx.log.info(s"Received Get key ${key} in table${table} from ${from}")
        hyperspaces.get(table) match {
          case Some(hyperspace) =>
            // if mapping contains invalid attributes
            if (hyperspace.attributes.toSet != mapping.keys.toSet) {
              if (hyperspace.attributes.toSet.size <= mapping.keys.toSet.size)
                from ! PutResult(Left(InvalidAttributeError(mapping.keys.toSet.diff(hyperspace.attributes.toSet))))
              else if (hyperspace.attributes.toSet.size > mapping.keys.toSet.size)
                from ! PutResult(Left(IncompleteAttributesError(hyperspace.attributes.toSet.diff(mapping.keys.toSet))))
            } else
              handleValidPut(put, ctx, hyperspace, dataNodes)
          case None =>
            from ! PutResult(Left(TableNotExistError))
        }
    }
  }

  def handleValidCreate(
    create: Create,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[CreateResult]] = dataNodes
      .map(dn => { dn ? [CreateResult](ref => Create(ref, create.table, create.attributes)) })
    val answersSingleSuccessFuture: Future[Seq[Try[CreateResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover({ case x: Throwable => Failure(x) }))
    )

    val processedFuture: Future[CreateResult] = answersSingleSuccessFuture.map(seq => {
      val allCreateSuccessful = seq
        .map {
          case Failure(exception) => {
            ctx.log.error(s"encountered exception when waiting for create response: ${exception.getMessage}")
            false
          }
          case Success(value) =>
            value.result match {
              // never happens
              case Left(value) => false
              // should always be true as put should never fail
              case Right(succeeded) => succeeded
            }
        }
        .forall(succeeded => succeeded == true)

      if (allCreateSuccessful)
        CreateResult(Right(true))
      else
        CreateResult(Left(TimeoutError))
    })

    processedFuture.foreach(pr => {
      // send processed result back to gateway server
      create.from ! pr
      // send create success to self if it succeeded
      pr.result match {
        case Left(error) =>
          ctx.log.error("didn't receive create responses from all data ndoes")
        case Right(_) =>
          ctx.self ! CreateSuccess(create.table, hyperspace)
      }
    })
  }

  def handleValidLookup(
    lookup: Lookup,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val responsibleNode = dataNodes(hyperspace.getResponsibleNodeId(lookup.key))
    // let the single responsible datanode reply to frontend
    responsibleNode ! lookup
  }

  def handleValidPut(
    put: Put,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[PutResult]] = hyperspace
      .getResponsibleNodeIds(put.key, put.mapping)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [PutResult](ref => Put(ref, put.table, put.key, put.mapping))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[PutResult]]] = Future.sequence(
      answers.map(f => f.map(Success(_)).recover({ case x: Throwable => Failure(x) }))
    )

    val processedFuture: Future[PutResult] = answersSingleSuccessFuture.map(seq => {
      val allPutSuccessful = seq
        .map {
          case Failure(exception) => {
            ctx.log.error(s"encountered exception when waiting for put response: ${exception.getMessage}")
            false
          }
          case Success(value) =>
            value.result match {
              // never happens
              case Left(value) => false
              // should always be true as put should never fail
              case Right(succeeded) => succeeded
            }

        }
        .forall(succeeded => succeeded == true)

      if (allPutSuccessful)
        PutResult(Right(true))
      else
        PutResult(Left(TimeoutError))
    })

    // send processed result back to gateway server (on completion of future)
    processedFuture.foreach(pr => put.from ! pr)
  }

  def handleValidSearch(
    search: Search,
    ctx: ActorContext[GatewayMessage],
    hyperspace: HyperSpace,
    dataNodes: Seq[ActorRef[DataNode.AcceptedMessage]]
  )(implicit as: ActorSystem[Nothing], timeout: Timeout, ec: ExecutionContext): Unit = {

    val answers: Seq[Future[SearchResult]] = hyperspace
      .getResponsibleNodeIds(search.mapping)
      .map(dataNodes(_))
      .map(dn => {
        dn ? [SearchResult](ref => Search(ref, search.table, search.mapping))
      })
      .toSeq

    val answersSingleSuccessFuture: Future[Seq[Try[SearchResult]]] = Future.sequence(
      answers.map(f => {
        f.map(Success(_)).recover({ case x: Throwable => Failure(x) })
      })
    )

    val processedFuture: Future[SearchResult] = answersSingleSuccessFuture.map(seq => {
      var nonExceptionSearchResults = mutable.Set.empty[Map[String, AttributeMapping]]
      var exception: SearchError = null
      for (tried <- seq) {
        tried match {
          case Success(value) =>
            value.result match {
              case Left(value)  => exception = value
              case Right(value) => nonExceptionSearchResults.add(value)
            }
          case Failure(exception) =>
            ctx.log.error(s"exception occurred when asking for lookup result: ${exception.getMessage}")
        }
      }
      if (exception == null) {
        val mergedMatches = nonExceptionSearchResults.toSet
          .map((sr: Map[String, AttributeMapping]) => sr.toSet[(String, AttributeMapping)])
          .fold(Set.empty)(_.union(_))
          .toMap
        SearchResult(Right(mergedMatches))
      } else {

        SearchResult(Left(exception))
      }
    })
    // send processed result back to gateway server (on completion of future)
    processedFuture.onComplete(sr => search.from ! sr.get)
  }

}
