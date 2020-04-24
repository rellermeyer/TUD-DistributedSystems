package org.orleans.silo.control

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.communication.ConnectionProtocol.SlaveInfo
import org.orleans.silo.dispatcher.Sender
import org.orleans.silo.metrics.LoadMonitor
import org.orleans.silo.services.grain.Grain.Receive
import org.orleans.silo.services.grain.{Grain, GrainRef}
import org.orleans.silo.storage.GrainDatabase
import org.orleans.silo.utils.{Circular, GrainState}
import org.orleans.silo.utils.GrainState.GrainState
import org.orleans.silo.{GrainInfo, Master}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}

class MasterGrain(_id: String, master: Master)
  extends Grain(_id)
    with LazyLogging {

  private var slaveRefs: Circular[(SlaveInfo, GrainRef)] = null
  private var slaveGrainRefs: ConcurrentHashMap[String, GrainRef] =
    new ConcurrentHashMap[String, GrainRef]()

  logger.info("Started master grain.")

  def startLoadMonitor() = {
    val loadMonitor = new LoadMonitor(master.grainMap, this)
    val loadMonitorThread: Thread = new Thread(loadMonitor)
    loadMonitorThread.setName(
      s"Master-LoadMonitor")
    loadMonitorThread.start()
  }
  logger.warn("Starting Load Monitor.")
  startLoadMonitor()

  def roundRobin(): (SlaveInfo, GrainRef) = {
    if (slaveRefs == null) {
      slaveRefs = new Circular(
        master.getSlaves().map(x => (x, GrainRef(x.uuid, x.host, x.tcpPort))))
    }

    val head = slaveRefs.next

    (head._1, head._2)
  }

  /**
   * Execution context of the master to run the futures
   */
  implicit val ec: ExecutionContext = master.executionContext

  override def receive: Receive = {
    case (request: SearchGrainRequest[_], sender: Sender) =>
      logger.debug("Master grain handling grain search request")
      processGrainSearch(request, sender)(request.grainClass, request.grainType)

    case (request: CreateGrainRequest[_], sender: Sender) =>
      logger.debug("Master grain handling create grain request")
      processCreateGrain(request, sender)(request.grainClass, request.grainType)

    case (request: DeleteGrainRequest, _) =>
      logger.debug("Master handling delete grain request")
      processDeleteGrain(request)

    case (request: UpdateGrainStateRequest, sender: Sender) =>
      logger.info("Master handling update grain state request")
      processUpdateState(request)

    //  For testing purposes
    case (request: ActiveGrainRequest[_], sender: Sender) =>
      logger.info("Master handling activate grain request")
      processActivateGrain(request, sender)(request.grainClass, request.grainType)
  }

  /**
    * Processes a request for searching a grain and responds to the sender
    *
    * @param request
    * @param sender
    */
  private def processGrainSearch[T <: Grain: ClassTag: TypeTag](
      request: SearchGrainRequest[T],
      sender: Sender): Unit = {
    def activateGrain() = {
      // Since the grain is not active anywhere but , activate it on the slave with the least load
      val slaveInfo: SlaveInfo = selectSlave()

      var slaveRef: GrainRef = null
      if (!slaveGrainRefs.containsKey(slaveInfo)) {
        slaveRef = GrainRef(slaveInfo.uuid, slaveInfo.host, slaveInfo.tcpPort)
        slaveGrainRefs.put(slaveInfo.uuid, slaveRef)
      } else {
        slaveRef = slaveGrainRefs.get(slaveInfo)
      }

      logger.debug(
        s"No active grain, so sending an ActivationRequest to $slaveRef")

      val result = slaveRef ? ActiveGrainRequest(request.id, request.grainClass, request.grainType)
      result.onComplete {
        case Success(response: ActiveGrainResponse) =>
          // Notify the sender of the GrainSearch where the grain is active now
          logger.debug(s"Grain with id ${request.id} now active on slave $slaveRef, sending this info back to $sender")
          slaveInfo.grainCount.getAndIncrement()
          val currentActivations: List[GrainInfo] = master.grainMap.getOrDefault(request.id, List())
          // Since master doesn't distinguish activations of the same grain on the same slave
          // we just want to keep 1 element in a map corresponding to that grain in that slave
          if (!currentActivations.exists(x => x.address.equals(response.address))) {
            master.grainMap.put(request.id, GrainInfo(slaveInfo.uuid, response.address, response.port,
              GrainState.InMemory, 0) :: currentActivations)
            if (!master.grainClassMap.containsKey(request.id)) {
              master.grainClassMap.put(request.id, GrainType(request.grainClass, request.grainType))
            }
          }

          sender ! SearchGrainResponse(response.address, response.port)

        case Failure(throwable: Throwable) =>
          //TODO either notify the sender that it has failed or try again (possibly on another slave)
          logger.error(throwable.toString)
      }
    }

    val id = request.id
    if (master.grainMap.containsKey(id)) {
      val activeGrains: List[GrainInfo] = master.grainMap
        .get(id)
        .filter(grain => GrainState.InMemory.equals(grain.state))
      logger.warn(activeGrains.size.toString)
      if (activeGrains.nonEmpty) {

        // Send the slave of the grain with the least load, to balance the load between grains
        val slaveInfo: GrainInfo =
          activeGrains.reduceLeft((x, y) => if (x.load < y.load) x else y)
        sender ! SearchGrainResponse(slaveInfo.address, slaveInfo.port)

        return
      } else {
        // If the grain is in the grainMap but not InMemory, activate it
        activateGrain()
        return
      }
    }

    // Check if the grain possibly still is defined in the database
    logger.debug(
      s"Check if the grain possibly still is defined in the database, type: $typeTag")
    val grain = GrainDatabase.instance
      .get[T](request.id)(request.grainClass, request.grainType)
    if (grain.isDefined) {
      // The grain does exist in the database so it can still be activated
      activateGrain()
    } else {
      //TODO See how we manage exceptions in this side!
      sender ! SearchGrainResponse(null, 0)
    }
  }

  /**
   * Choose one slave to run the new grain
   *
   * @param request
   * @param sender
   */
  private def processCreateGrain[T <: Grain: ClassTag: TypeTag](request: CreateGrainRequest[T],
                                 sender: Sender): Unit = {
    // Now get the least loaded slave
    val info: SlaveInfo = master.slaves.values.reduceLeft((x, y) =>

    var slaveRef: GrainRef = null
    if (!slaveGrainRefs.containsKey(info.uuid)) {
      slaveRef = GrainRef(info.uuid, info.host, info.tcpPort)
      slaveGrainRefs.put(info.uuid, slaveRef)
    } else {
      slaveRef = slaveGrainRefs.get(info.uuid)
    }

    val f: Future[Any] = slaveRef ? request
    f onComplete {
      case Success(resp: CreateGrainResponse) =>
        // Create the grain info and put it in the grainMap
        logger.debug(s"Received response from a client! $resp")
        info.grainCount.getAndIncrement()
        val grainInfo =
          GrainInfo(info.uuid, resp.address, resp.port, GrainState.InMemory, 0)
        if (!master.grainMap.containsKey(resp.id)) {
          master.grainMap.put(resp.id, List(grainInfo))
          if (!master.grainClassMap.containsKey(resp.id)) {
            master.grainClassMap.put(resp.id, GrainType(request.grainClass, request.grainType))
          }
        }
        // Answer to the user
        sender ! resp

      case Failure(exception) =>
        logger.error(
          s"Exeception occurred while processing create grain" +
            s" ${exception.printStackTrace()}")
    }
  }

  /**
   * Send the delete grain request to the appropriate Slave
   *
   * @param request
   */
  private def processDeleteGrain(request: DeleteGrainRequest): Unit = {
    val id = request.id
    // Look for the slave that has that request
    if (master.grainMap.containsKey(id)) {
      val grainInfo: GrainInfo = master.grainMap.get(id).head
      // Send deletion request
      var slaveRef: GrainRef = null

      if (!slaveGrainRefs.containsKey(grainInfo.slave)) {
        slaveRef = GrainRef(grainInfo.slave,
          grainInfo.address,
          master.slaves.get(grainInfo.slave).get.tcpPort)
        slaveGrainRefs.put(grainInfo.slave, slaveRef)
      } else {
        slaveRef = slaveGrainRefs.get(grainInfo.slave)
      }

      // Send request to the slave
      slaveRef ! request

      // Delete from grainMap
      master.grainMap.remove(id)
    } else {
      logger.error(s"Not existing ID in the grainMap $id")
    }

  }

  //TODO Decide when the salve deactivates the grain and send update state message
  private def processUpdateState(request: UpdateGrainStateRequest): Unit = {
    logger.debug(s"Updating state of the grain ${request.id}.")
    val newState: GrainState = GrainState.withNameWithDefault(request.state)
    val slave: String = request.source
    val port: Int = request.port
    if (master.grainMap.containsKey(request.id)) {
      val grainLocations: List[GrainInfo] = master.grainMap.get(request.id)
      // Replace the description of the updated grain
      grainLocations.foreach(grain => {
        if (grain.address.equals(slave) && grain.port.equals(port)) {
          grain.state = newState
        }
      })
    } else {
      logger.warn("Master notified about the grain it didn't know about!")
//      master.grainMap
//        .put(request.id, List(GrainInfo(slave, slave, port, newState, 0)))
    }
  }

  /**
   * Send the activate grain request to the appropriate Slave.
   * Method for testing manual grain activation.
   * @param request
   */
  private def processActivateGrain[T <: Grain : ClassTag : TypeTag](request: ActiveGrainRequest[T], sender: Sender): Unit = {
    // Since the grain is not active anywhere but , activate it on the slave with the least load
    val slaveInfo: SlaveInfo = selectSlave()
    logger.warn("Selected slave on port:" + slaveInfo.tcpPort + " to activate grain.")

    var slaveRef: GrainRef = null
    if (!slaveGrainRefs.containsKey(slaveInfo)) {
      slaveRef = GrainRef(slaveInfo.uuid, slaveInfo.host, slaveInfo.tcpPort)
      slaveGrainRefs.put(slaveInfo.uuid, slaveRef)
    } else {
      slaveRef = slaveGrainRefs.get(slaveInfo)
    }

    val result = slaveRef ? ActiveGrainRequest(request.id, request.grainClass, request.grainType)
    result.onComplete {
      case Success(response: ActiveGrainResponse) =>
        // Notify the sender of the GrainSearch where the grain is active now
        logger.warn(s"Grain with id ${request.id} now active on slave $slaveRef, sending this info back to $sender")
        slaveInfo.grainCount.getAndIncrement()
        val currentActivations: List[GrainInfo] = master.grainMap.getOrDefault(request.id, List())
        if (!currentActivations.exists(x => x.address.equals(response.address) && x.port.equals(response.port))) {
          master.grainMap.put(request.id, GrainInfo(slaveInfo.uuid, response.address, response.port,
            GrainState.InMemory, 0) :: currentActivations)
          if (!master.grainClassMap.containsKey(request.id)) {
            master.grainClassMap.put(request.id, GrainType(request.grainClass, request.grainType))
          }

        }
        sender ! ActiveGrainResponse(response.address, response.port)

      case Failure(throwable: Throwable) =>
        //TODO either notify the sender that it has failed or try again (possibly on another slave)
        logger.error(throwable.toString)
    }
  }

  /**
   * Replicates the grain.
   * LoadMonitor triggers this method when replication is needed.
   * @param grainId Id of the grain to replicate
   */
  def triggerGrainReplication(grainId: String): Unit = {
    val grainType = master.grainClassMap.get(grainId)
    typedTriggerGrainReplication(grainId, grainType)
  }


  //TODO Lots of code duplication for grain activation
  /**
   * Replicates the grain of type T.
   * @param grainId Id of the grain to replicate
   */
  private def typedTriggerGrainReplication[T <: Grain](grainId: String, grainType: GrainType[T]) : Unit = {
    logger.warn(s"Replication triggered for grain: ${grainId}")
    val classtag: ClassTag[T] = grainType.classTag
    val typetag: TypeTag[T] = grainType.typeTag

    val slaveInfo: SlaveInfo = selectSlave()
    logger.warn("Selected slave on port:" + slaveInfo.tcpPort + " to activate grain.")

    var slaveRef: GrainRef = null
    if (!slaveGrainRefs.containsKey(slaveInfo)) {
      slaveRef = GrainRef(slaveInfo.uuid, slaveInfo.host, slaveInfo.tcpPort)
      slaveGrainRefs.put(slaveInfo.uuid, slaveRef)
    } else {
      slaveRef = slaveGrainRefs.get(slaveInfo)
    }

    val result = slaveRef ? ActiveGrainRequest[T](grainId, classtag, typetag)
    result.onComplete {
      case Success(response: ActiveGrainResponse) =>
        // Notify the sender of the GrainSearch where the grain is active now
        logger.warn(s"Grain with id ${grainId} now active on slave $slaveRef.")

        val currentActivations: List[GrainInfo] = master.grainMap.getOrDefault(grainId, List())
        if (!currentActivations.exists(x => x.address.equals(response.address) && x.port.equals(response.port))) {
          master.grainMap.put(grainId, GrainInfo(slaveInfo.uuid, response.address, response.port,
            GrainState.InMemory, 0) :: currentActivations)
        }

      case Failure(throwable: Throwable) =>
        //TODO either notify the sender that it has failed or try again (possibly on another slave)
        logger.error(throwable.toString)
    }
  }

  private def selectSlave(): SlaveInfo = {
    master.slaves.values.reduceLeft((x, y) => if (x.grainCount.get() < y.grainCount.get()) x else y)
  }
}
