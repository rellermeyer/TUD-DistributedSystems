package invalidationlog

import java.io.{IOException, _}
import java.util.logging.{Level, Logger}

import core.{Body, glob}
import helper.{CheckpointSeeder, fileHelper}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Promise

object Checkpoint {
  private val LOGGER = Logger.getLogger(Checkpoint.getClass.getName)
}

/**
  * Class that logically represents a Checkpoint.
  * It uses @HashMap in order to store objects.
  *
  * @param items
  */
class Checkpoint(dir: String) extends Serializable {
  private val name = "checkpoint.txt"
  private var items = mutable.HashMap[String, CheckpointItem]()

  // when the object is created, initialize state restoration from file.
  {
    items = init()

    // Add a shutdown hook, so when the application is turned off, checkpoint state is dumped to file.
    sys.addShutdownHook({
      logMessage("System is shutting down, dumping checkpoint to a file.")
      val file = getCheckpointFile(dir)
      val oos = new ObjectOutputStream(new FileOutputStream(file.getPath))
      oos.writeObject(this)
      oos.close

      logMessage("Checkpoint dumped successfully.")

    })
  }

  /**
    * Method, that returns @CheckpointItem by object ID.
    *
    * @param objId
    * @return
    */
  def getById(objId: String): Option[CheckpointItem] = {
    items.contains(fileHelper.makeUnix(objId)) match {
      case true => Some(items(objId))
      case _ => None
    }
  }

  def isNewer(body: Body): Boolean = {
    getById(body.path) match {
      case Some(value) =>
        if (value.timestamp < body.timestamp) {
          return true
        }
      case None => return true

    }

    false
  }

  /**
    * Updates the existing item in the buffer.
    *
    * @param newItem
    */
  def update(newItem: CheckpointItem): CheckpointItem = {
    val id = fileHelper.makeUnix(newItem.id)

    items.contains(id) match {
      case false => insert(newItem)
      case _ => {
        items.update(id, newItem)
        return newItem
      }
    }
  }

  def swapFileByVersion(body: Body, newFile: File): Unit = {
    this.synchronized {

      if (isNewer(body)) {

        val file = new File(body.directory + "/" +  body.path)


        if (!file.exists()) {
          try {
            val dir = file.getParentFile
            dir.mkdirs()
          } catch {
            case e: Exception =>
              e.printStackTrace()
          }
        }


        newFile.renameTo(file)

        update(new CheckpointItem(body.path, body, false, body.timestamp))

      }
    }
  }

  /**
    * Method that returns all items from @Checkpoint as @List of Tuples (String, Item)
    *
    * @return
    */
  def getAllItems(): List[(String, CheckpointItem)] = {
    items.toList
  }

  /**
    * Method that clears the checkpoint.
    */
  def clear(): Unit = {
    items = new mutable.HashMap[String, CheckpointItem]()
  }

  /**
    * Method that inserts item into checkpoint.
    *
    * @param item
    * @return
    */
  private def insert(item: CheckpointItem): CheckpointItem = {
    val it = new CheckpointItem(fileHelper.makeUnix(item.id), item.body, item.invalid, item.timestamp)
    items += it.id -> it

    return item
  }

  /**
    * Function that initializes checkpoint from file
    *
    * @return
    */
  private def init(): mutable.HashMap[String, CheckpointItem] = {
    val file = getCheckpointFile(dir)
    try {
      val ois = new ObjectInputStream(new FileInputStream(dir + name))
      val inst = ois.readObject.asInstanceOf[Checkpoint]
      ois.close
      return inst.items
    } catch {
      case e: Exception => {
        logMessage(s"Error while reading ${dir + name} checkpoint from file", Level.SEVERE)
        return new mutable.HashMap[String, CheckpointItem]()
      }
    }

  }

  private def getCheckpointFile(dir: String): File = {
    val file = new File(dir + name)

    if (!file.exists()) {
      file.getParentFile.mkdirs()
      file.createNewFile()
    }

    file
  }

  private def logMessage(message: String, level: Level = null, logger: Logger = Checkpoint.LOGGER): Unit = {
    logger.log(if (level == null) Level.INFO else level, s"[Checkpoint]\t$message")
  }
}
