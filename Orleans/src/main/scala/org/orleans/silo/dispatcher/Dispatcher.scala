package org.orleans.silo.dispatcher

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap, Executors, ThreadPoolExecutor}

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.metrics.{Registry, RegistryFactory}
import org.orleans.silo.services.grain.Grain
import org.orleans.silo.storage.GrainDatabase
import org.orleans.silo.{Master, Slave}

import scala.concurrent.Future
import scala.reflect._
import scala.reflect.runtime.universe._

/**
  * Dispatcher that will hold the messages for a certain type of grain
  *
  * @param port port in which the dispatcher will be waiting for requests
  * @tparam T type of the grain that the dispatcher will serve
  */
class Dispatcher[T <: Grain: ClassTag: TypeTag](val port: Int, val registryFactory: Option[RegistryFactory])
    extends Runnable
    with LazyLogging {

  val SLEEP_TIME: Int = 50
  private val THREAD_POOL_DEFAULT_SIZE: Int = 8

  var running: Boolean = true

  // Thread pool to execute new request
  // The thread pool has a variable size that will scale with the number of requests to
  // make it perform better under load while also being efficient in lack of load
  private val pool: ThreadPoolExecutor =
    Executors
      .newFixedThreadPool(THREAD_POOL_DEFAULT_SIZE)
      .asInstanceOf[ThreadPoolExecutor]

  // Maps of Mailbox and grains linking them to an ID
  private[dispatcher] val mailboxIndex: ConcurrentHashMap[String, List[Mailbox]] =
    new ConcurrentHashMap[String, List[Mailbox]]()
  private[dispatcher] val grainMap: ConcurrentMap[Mailbox, Grain] =
    new ConcurrentHashMap[Mailbox, Grain]()

  // Create the message receiver and start it
  private val clientReceiver: ClientReceiver[T] =
    new ClientReceiver[T](mailboxIndex, port, registryFactory)
  val cRecvThread: Thread = new Thread(clientReceiver)
  cRecvThread.setName(s"ClientReceiver-$port")
  cRecvThread.start()

  logger.info(
    s"Dispatcher for ${classTag[T].runtimeClass.getName} started in port $port")

  /**
    * Creates a new grain and returns its id so it can be referenced
    * by the user and indexed by the master
    */
  def addGrain(implicit typeTag: TypeTag[T]): String = {
    // Create the id for the new grain
    val id: String = UUID.randomUUID().toString
    // Create a new grain of that type with the new ID
    val grain: T = classTag[T].runtimeClass
      .getConstructor(classOf[String])
      .newInstance(id)
      .asInstanceOf[T]
    // Create a mailbox
    val mbox: Mailbox = new Mailbox(grain, registryFactory)

    // Store the new grain to persistant storage
    logger.debug(s"Type of grain: $typeTag")
    GrainDatabase.instance.store(grain)(classTag, typeTag)

    // Put the new grain and mailbox in the indexes so it can be found
    this.grainMap.put(mbox, grain)

    val currentMailboxes: List[Mailbox] = this.clientReceiver.mailboxIndex.getOrDefault(id, List())
    this.clientReceiver.mailboxIndex.put(id, mbox :: currentMailboxes)
    if (registryFactory.isDefined) {
      val registry: Registry =
        registryFactory.get.getOrCreateRegistry(id)
      registry.addActiveGrain()
    }


    // Return the id of the grain
    id
  }

  /**
    * Activates an existing grain.
    */
  def addActivation(id: String, typeTag: TypeTag[T]): Unit = {
    logger.debug(
      s"Adding Activation of grain with id $id to dispatcher with type ${typeTag.toString()}")
    val grain: T = GrainDatabase.instance.get[T](id)(classTag, typeTag).get

    val mailbox = new Mailbox(grain, registryFactory)

    this.grainMap.put(mailbox, grain)
    this.grainMap.forEach((mbox, grain) => logger.info(s"Mailbox ${mbox.id} --> $grain"))
    val currentMailboxes: List[Mailbox] = this.clientReceiver.mailboxIndex.getOrDefault(grain._id, List())
    this.clientReceiver.mailboxIndex.put(grain._id, mailbox :: currentMailboxes)
    if (registryFactory.isDefined) {
      val registry: Registry =
        registryFactory.get.getOrCreateRegistry(id)
      registry.addActiveGrain()
    }
  }

  /**
    * Adds a master grain implementation, that will manage the requests for
    * create, delete or search for grains
    * @return
    */
  def addMasterGrain(master: Master): String = {
    // Create the id for the new grain
    val id: String = "master"
    // Create a new grain of that type with the new ID
    val grain: T = classTag[T].runtimeClass
      .getConstructor(classOf[String], classOf[Master])
      .newInstance(id, master)
      .asInstanceOf[T]
    // Create a mailbox
    val mbox: Mailbox = new Mailbox(grain, registryFactory)

    // Put the new grain and mailbox in the indexes so it can be found
    this.grainMap.put(mbox, grain)
    this.clientReceiver.mailboxIndex.put(id, List(mbox))

    logger.debug(s"Created master grain with id $id")
    // Return the id of the grain
    id
  }

  /**
    * Adds the slave grain
    */
  def addSlaveGrain(slave: Slave): String = {
    // Use the same id as the slave
    val id = slave.uuid
    // Create the grain that will manage the slave of that type with the new ID
    val grain: T = classTag[T].runtimeClass
      .getConstructor(classOf[String], classOf[Slave])
      .newInstance(id, slave)
      .asInstanceOf[T]
    // Create a mailbox
    val mbox: Mailbox = new Mailbox(grain, registryFactory)

    // Put the new grain and mailbox in the indexes so it can be found
    this.grainMap.put(mbox, grain)
    this.clientReceiver.mailboxIndex.put(id, List(mbox))

    // Return the id of the grain
    logger.debug(s"Created slave grain with id $id")
    id
  }

  /**
    * Delete a grain and its mailbox
    *
    * @param id
    */
  // TODO we should be careful cause maybe the mailbox is running
  def deleteGrain(id: String) = {
    // delete from index and delete mailbox
    logger.info(s"Deleting information for grain $id")
    this.grainMap.remove(this.clientReceiver.mailboxIndex.get(id))

    // Deleting grain from database. Returned Future contains the deleted grain if successful
    // and otherwise a Failure with the exception
    val result: Future[Boolean] = GrainDatabase.instance.delete(id)

    this.clientReceiver.mailboxIndex.remove(id)
  }

  override def run(): Unit = {
    while (running) {
      // Iterate through the mailboxes and if one is not empty schedule it
      this.grainMap.forEach((mbox, _) => {
        if (!mbox.isEmpty && mbox.isRunning.getAcquire() == false) {
          // if the mailbox is not empty schedule the mailbox
          // Executing the mailbox basically delivers all the messages
          logger.debug(s"Running mailbox ${mbox.id}")
          mbox.isRunning.set(true)
          pool.execute(mbox)
        }
      })
      Thread.sleep(SLEEP_TIME)
    }
  }

  def stop() = {
    logger.debug(
      s"Stopping dispatcher for ${classTag[T].runtimeClass.getClass}.")
    clientReceiver.stop()
    pool.shutdown()
    this.running = false
  }

  def getType(): ClassTag[T] = classTag[T]
}
