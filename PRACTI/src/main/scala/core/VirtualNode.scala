package core

import java.io.{IOException, ObjectOutputStream, OutputStream}
import java.net.Socket
import java.util.logging.{Level, Logger}

import scala.concurrent.{ExecutionContext, Future}

object VirtualNode {
  private val LOGGER = Logger.getLogger(core.Node.getClass.getName)
}

class VirtualNode(val id: Int, val hostname: String, val port: Int) extends Serializable {

  @transient var controllerSocket: Socket = null
  @transient var outputStream: ObjectOutputStream = null

  def getControllerPort: Int = {
    port + 2
  }

  def getCorePort: Int = {
    port
  }

  def getHTTPPort: Int = {
    port + 1
  }

  @transient def getControllerSocket: Socket = {
    if (controllerSocket == null || controllerSocket.isClosed) {
      controllerSocket = new Socket(hostname, getControllerPort)
      controllerSocket.setKeepAlive(true)
    }


    controllerSocket
  }

  @transient def sendToCore(objectToSend: Object): Unit = {
    val coreSocket = new Socket(hostname, getCorePort)
    val outputStream = new ObjectOutputStream(coreSocket.getOutputStream)

    outputStream.writeObject(objectToSend)
    coreSocket.close()
  }

  @transient def sendToCoreAsync(objectToSend: Object): Unit = {
    Future {
      sendToCore(objectToSend)
    }(ExecutionContext.global)
  }

  @transient def sendToController(objectToSend: Object): Unit = {
    this.synchronized {
      if (outputStream == null) {
        controllerSocket = new Socket(hostname, getControllerPort)
        controllerSocket.setKeepAlive(true)
        val output = controllerSocket.getOutputStream
        outputStream = new ObjectOutputStream(output)
      }

      outputStream.writeObject(objectToSend)
      outputStream.flush()
    }
  }

  @transient def sendToControllerAsync(objectToSend: Object): Future[Unit] = {
    Future {
      sendToController(objectToSend)
    }(ExecutionContext.global)
  }

  @transient def logMessage(message: String, level: Level = null, logger: Logger = VirtualNode.LOGGER): Unit = {
    logger.log(if (level == null) Level.INFO else level, s"=======>[ID:$id][$hostname]\t$message\n")
  }

  override def toString: String = {
    this.id + "[" + hostname + ":" + port + "]"
  }
}
