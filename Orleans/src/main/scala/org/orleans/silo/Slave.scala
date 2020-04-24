package org.orleans.silo

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.communication.ConnectionProtocol._
import org.orleans.silo.communication.{PacketListener, PacketManager, ConnectionProtocol => protocol}
import org.orleans.silo.control.SlaveGrain
import org.orleans.silo.dispatcher.Dispatcher
import org.orleans.silo.metrics.RegistryFactory
import org.orleans.silo.services.grain.Grain
import org.orleans.silo.utils.ServerConfig

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import scala.reflect.{ClassTag, classTag}

object Slave {
  def apply(): SlaveBuilder = new SlaveBuilder()

}

class SlaveBuilder extends LazyLogging {

  private var serverConfig: ServerConfig = ServerConfig("", 0, 0)
  private var masterConfig: ServerConfig = ServerConfig("", 0, 0)

  private var executionContext: ExecutionContext = null
  private var grains
    : mutable.MutableList[(ClassTag[_ <: Grain], TypeTag[_ <: Grain])] =
    mutable.MutableList()

  def setHost(hostt: String): SlaveBuilder = {
    this.serverConfig = serverConfig.copy(host = hostt)
    this
  }

  def setUDPPort(udp: Int): SlaveBuilder = {
    this.serverConfig = serverConfig.copy(udpPort = udp)
    this
  }

  def setTCPPort(tcp: Int): SlaveBuilder = {
    this.serverConfig = serverConfig.copy(tcpPort = tcp)
    this
  }

  def setMasterHost(hostt: String): SlaveBuilder = {
    this.masterConfig = masterConfig.copy(host = hostt)
    this
  }

  def setMasterUDPPort(udp: Int): SlaveBuilder = {
    this.masterConfig = masterConfig.copy(udpPort = udp)
    this
  }

  def setMasterTCPPort(tcp: Int): SlaveBuilder = {
    this.masterConfig = masterConfig.copy(tcpPort = tcp)
    this
  }

  def setServerConfig(serverConfig: ServerConfig): SlaveBuilder = {
    this.serverConfig = serverConfig
    this
  }

  def setExecutionContext(executionContext: ExecutionContext): SlaveBuilder = {
    this.executionContext = executionContext
    this
  }

  def setGrainPorts(ports: Set[Int]): SlaveBuilder = {
    this.serverConfig = serverConfig.copy(grainPorts = ports)
    this
  }

  def registerGrain[T <: Grain: ClassTag: TypeTag] = {
    val classtag = classTag[T]
    val typetag = typeTag[T]

    if (this.grains.contains(classtag)) {
      logger.warn(
        s"${classtag.runtimeClass.getName} already registered in slave.")
    }

    this.grains += Tuple2(classtag, typetag)
    this
  }

  def build(): Slave = {
    if (executionContext == null) {
      logger.warn(
        "Master has no execution context set. Will use the global one.")
      this.executionContext = ExecutionContext.global
    }

    new Slave(serverConfig, masterConfig, executionContext, grains.toList)
  }
}

/**
  * Slave silo, handles request from the master
  * @param slaveConfig Server config for the slave
  * @param masterConfig Config of the master server
  * @param executionContext Execution context for the RPC services
  */
class Slave(
    val slaveConfig: ServerConfig,
    masterConfig: ServerConfig,
    executionContext: ExecutionContext,
    val registeredGrains: List[(ClassTag[_ <: Grain], TypeTag[_ <: Grain])] =
      List())
    extends LazyLogging
    with Runnable
    with PacketListener {

  // Hashmap that identifies each grainID with its type so
  // we can check which dispatcher is in charge of that ID
  val grainMap: ConcurrentHashMap[String, ClassTag[_ <: Grain]] =
    new ConcurrentHashMap[String, ClassTag[_ <: Grain]]()

  val metricsRegistryFactory: RegistryFactory = new RegistryFactory()

  // Metadata for the slave.
  val uuid: String = UUID.randomUUID().toString
  val shortId: String = protocol.shortUUID(uuid)

  @volatile
  var running: Boolean = false
  val SLEEP_TIME: Int = 100

  // Master information.
  @volatile
  var connectedToMaster: Boolean = false

  @volatile
  var masterInfo: MasterInfo = MasterInfo("", 0)

  // Packetmanager which send packets and receives packets (event-driven).
  val packetManager: PacketManager =
    new PacketManager(this, slaveConfig.udpPort)

  // Hash table of other slaves. This is threadsafe.
  val slaves = scala.collection.mutable.HashMap[String, SlaveInfo]()

  @volatile
  var dispatchers: List[Dispatcher[_ <: Grain]] = List()

  private var portsUsed: Set[Int] = Set()

  /**
    * Starts the slave.
    * - Creates a main control loop to send information to the master.
    * - Creates a packet-manager which handles incoming and outgoing packets.
    */
  def start() = {
    logger.info(
      f"Now starting slave with id: ${protocol.shortUUID(uuid)} on ${slaveConfig.host}.")
    this.running = true

    // Starting a packet manager which listens for incoming packets.
    packetManager.init(shortId)

    // Creating slave thread and starting it.
    val slaveThread = new Thread(this)
    slaveThread.setName(f"slave-$shortId")
    slaveThread.start()

    // Starting the dispatchers
    logger.debug("Starting dispatchers.")
    startMainDispatcher()
    startGrainDispatchers()
  }

  def startMainDispatcher() = {
    // Start dispatcher for the general grain
    val mainDispatcher = new Dispatcher[SlaveGrain](this.slaveConfig.tcpPort, Option(metricsRegistryFactory))
    val slaveGrainID = mainDispatcher.addSlaveGrain(this)
    //grainMap.put(slaveGrainID,classTag[SlaveGrain])
    dispatchers = mainDispatcher :: dispatchers

    // Create the new thread to run the dispatcher and start it
    val mainDispatcherThread: Thread = new Thread(mainDispatcher)
    mainDispatcherThread.setName(
      s"Slave-${this.slaveConfig.host}-MainDispatcher")
    mainDispatcherThread.start()
  }

  def startGrainDispatchers() = {
    registeredGrains.foreach { x =>
      implicit val classtag: ClassTag[_ <: Grain] = x._1
      implicit val typetag: TypeTag[_ <: Grain] = x._2

      // Create a new dispatcher and run it in a new thread
      val d = new Dispatcher(getFreePort, Option(metricsRegistryFactory))
      val dThread: Thread = new Thread(d)
      dThread.setName(s"Dispatcher-${d.port}-${classtag.runtimeClass.getName}")
      dThread.start()

      dispatchers = d :: dispatchers
    }
  }

  /**
    * Returns a free port that hasn't been used by any of the grains.
    */
  def getFreePort: Int = {
    val portsLeft = slaveConfig.grainPorts.diff(portsUsed)

    if (portsLeft.size == 0) {
      this.stop()
      throw new RuntimeException("No free ports left to start grain socket.")
    }

    val port = portsLeft.toList(0)
    portsUsed = Set(port).union(portsUsed)
    port
  }

  /** Control loop. */
  def run(): Unit = {
    var oldTimeHeartbeat: Long = System.currentTimeMillis()
    var oldTimeMetrics: Long = System.currentTimeMillis()

    while (running) {
      // If not connected to the master, lets do a handshake.
      if (!connectedToMaster) {
        if (!connectToMaster()) return // If we can't connect, exit this loop.
      }

      // Keep track of local time, to ensure sending heartbeats on time.
      val newTime: Long = System.currentTimeMillis()
      val timeDiffHeartbeat = newTime - oldTimeHeartbeat
      val timeDiffMetrics = newTime - oldTimeMetrics

      // Check if it is time to send heartbeats again.
      if (timeDiffHeartbeat >= protocol.heartbeatInterval) {
        logger.debug("Sending heartbeats to master.")

        // Send heartbeat to the master.
        val heartbeat = Packet(PacketType.HEARTBEAT, this.uuid, newTime)
        packetManager.send(heartbeat, masterConfig.host, masterConfig.udpPort)

        // Update time
        oldTimeHeartbeat = newTime
      }

      // Check if it is time to send load info again.
      if (timeDiffMetrics >= protocol.metricsInterval) {
        logger.debug("Sending load metrics to master.")

        val data = metricsRegistryFactory.getRegistryLoads()
        if (data.nonEmpty) {
          // Send load metrics to the master.
          val metrics = Packet(PacketType.METRICS,
                               this.uuid,
                               newTime,
                               prepareMetricsData(data))
          packetManager.send(metrics, masterConfig.host, masterConfig.udpPort)
        }
        oldTimeMetrics = newTime
      }

      verifyMasterAlive()

      // Now time to sleep :)
      Thread.sleep(SLEEP_TIME)
    }
  }

  /**
    * Transforms map with load per service to String representation.
    *
    * @param data
    * @return List of String representation of the load data.
    */
  def prepareMetricsData(data: Map[String, (Int, Int)]): List[String] = {
    var prepared: List[String] = List()
    for ((id, (load, count)) <- data) {
      if (!id.equals(uuid)) {
        val s = id + ":" + load.toString + ":" + count.toString
        prepared = s :: prepared
      }
    }
    prepared
  }

  /**
    * Trying to connect to master.
    * 1) Sends handshake packet.
    * 2) Expects welcome packet which toggles `connectedToMaster` flag.
    *   - If not received within `connectionDelay`ms, then retry `conectionAttempts` times.
    *   - If no welcome packet is received at all. Slave silo is shut down.
    */
  def connectToMaster(): Boolean = {
    logger.info("Connecting to master.")
    for (i <- 1 to protocol.connectionAttempts) {
      // Send a handshake and wait for a bit.
      val handshake =
        new Packet(PacketType.HANDSHAKE,
                   this.uuid,
                   System.currentTimeMillis(),
                   List(slaveConfig.tcpPort.toString))
      packetManager.send(handshake, masterConfig.host, masterConfig.udpPort)
      Thread.sleep(protocol.connectionDelay)

      // If connected, the slave can start its life.
      if (connectedToMaster) {
        return true
      }

      logger.info(
        s"Couldn't connect to master. Attempt $i/${protocol.connectionAttempts}.")
    }

    // If couldn't connect to the master after x attempts, shutdown server.
    logger.error("Couldn't connect to master. Now shutting down.")
    this.stop()

    // We couldn't connect at all.
    return false
  }

  /**
    * Verifies if the master is still alive. If not, the slave gets disconnected (and then tries to reconnect).
    */
  def verifyMasterAlive(): Unit = {
    val diffTime = System.currentTimeMillis() - masterInfo.lastHeartbeat
    if (diffTime >= protocol.deathTime) {
      //consider it dead
      logger.warn("Connection to master timed out. Will try reconnect.")
      masterInfo = MasterInfo("", -1)
      connectedToMaster = false
    }
  }

  /**
    * Event-driven method which is triggered when a packet is received.
    * Forwards the packet to the correct handler.
    *
    * @param packet the received packet.
    * @param host   the host receiving from.
    * @param port   the port receiving from.
    */
  override def onReceive(
      packet: Packet,
      host: String,
      port: Int
  ): Unit = packet.packetType match {
    case PacketType.WELCOME       => processWelcome(packet, host, port)
    case PacketType.HEARTBEAT     => processHeartbeat(packet, host, port)
    case PacketType.SLAVE_CONNECT => processSlaveConnect(packet, host, port)
    case PacketType.SLAVE_DISCONNECT =>
      processSlaveDisconnect(packet, host, port)
    case PacketType.SHUTDOWN => processShutdown(packet, host, port)
    case _                   => logger.error(s"Did not expect this packet: $packet.")

  }

  /**
    * Processes a welcome.
    * 1). If a welcome packet is received, the `masterInfo` is updated with the correct UUID.
    *
    * @param packet The welcome packet.
    * @param host   The host receiving from.
    * @param port   The port receiving from.
    */
  def processWelcome(packet: Packet, host: String, port: Int): Unit = {
    masterInfo = MasterInfo(packet.uuid, System.currentTimeMillis())
    connectedToMaster = true
    logger.info(
      s"Successfully connected to the master with uuid: ${masterInfo.uuid}.")
  }

  /**
    * Processes a heartbeat.
    * 1). If the packet is from an unknown UUID, we ignore this packet.
    *   - TODO I'm not sure if this is already a good thing, what if the master crashed and got a new UUID?
    * 2) Master information gets updated with the latest heartbeat, so that we know its alive.
    *
    * @param packet The heartbeat packet.
    * @param host   The host receiving from.
    * @param port   The port receiving from.
    */
  def processHeartbeat(packet: Packet, host: String, port: Int): Unit = {
    if (packet.uuid != masterInfo.uuid) {
      logger.debug("Received heartbeat packet from an unknown source.")
      return
    }

    // Update latest info on master.
    this.masterInfo =
      masterInfo.copy(lastHeartbeat = System.currentTimeMillis())
  }

  /**
    * Processes connection of a new slave.
    * 1). Add slave to local table (if not already there).
    *
    * @param packet the connect packet.
    * @param host   the host receiving from.
    * @param port   the port receiving from.
    */
  def processSlaveConnect(packet: Packet, host: String, port: Int): Unit = {
    // If slave is already in the cluster, we will not add it again.
    if (slaves.contains(packet.uuid)) return

    // Store slaveInfo from data in packet.
    slaves.put(packet.uuid,
               SlaveInfo(packet.uuid,
                         packet.data(0),
                         packet.data(1).toInt,
                         packet.data(2).toInt))
    logger.debug(
      s"Added new slave ${protocol.shortUUID(packet.uuid)} to local hashtable.")
  }

  /**
    * Processes disconnect of a new slave.
    * 1). Remove slave from local table (if there).
    *
    * @param packet the disconnect packet.
    * @param host   the host receiving from.
    * @param port   the port receiving from.
    */
  def processSlaveDisconnect(packet: Packet, host: String, port: Int): Unit = {
    // If slave is not in the cluster, we will ignore this packet.
    if (!slaves.contains(packet.uuid)) return

    // Store slaveInfo from data in packet.
    slaves.remove(packet.uuid)
    logger.debug(
      s"Removed slave ${protocol.shortUUID(packet.uuid)} from local hashtable.")
  }

  /**
    * Processes a shutdown packet by stopping the slave.
    *
    * @param packet the shutdown packet.
    * @param host   the host receiving from.
    * @param port   the port receiving from.
    */
  def processShutdown(packet: Packet, host: String, port: Int): Unit = {
    // Check if it is actually the master.
    if (packet.uuid != masterInfo.uuid) {
      logger.warn(
        "Got a shutdown packet from a source which doesn't seem to be the master. Ignoring it.")
      return
    }

    // Stops the slave.
    this.stop()
  }

  /** Returns all slaves. **/
  def getSlaves(): List[SlaveInfo] = slaves.toList.map(_._2)

  /**
    * Stopping the slave.
    * Returns if it isn't running.
    */
  def stop(): Unit = {
    if (!running) return
    logger.info(f"Now stopping slave with id: ${protocol.shortUUID(uuid)}.")

    // Send shutdown packet to master.
    val shutdown =
      Packet(PacketType.SHUTDOWN, this.uuid, System.currentTimeMillis())
    packetManager.send(shutdown, masterConfig.host, masterConfig.udpPort)

    // Stop dispatchers
    this.dispatchers.foreach(_.stop())

    // Cancel packet manager and stop slave.
    this.packetManager.cancel()
    this.running = false
    this.slaves.clear()
    logger.info("Slave exited.")
  }
}
