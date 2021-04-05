package core

import java.io._
import java.net.Socket

import clock.{Clock, ClockInfluencer}
import helper.fileHelper
import invalidationlog.Checkpoint

import scala.concurrent.{ExecutionContext, Future}

case class Body(@transient var directory: String, var path: String) extends Serializable with ClockInfluencer {
  path = fileHelper.makeUnix(path)
  override var timestamp: Long = 0

  import java.io.FileInputStream

  def bind(node: Node): Unit = {
    directory = node.dataDir
  }

  def send(conn: Socket, clock: Clock, withStamp: Boolean = true): Unit = {
    val input = new BufferedInputStream(new FileInputStream(directory + path))
    val output = conn.getOutputStream

    if (withStamp)
      timestamp =  clock.sendStamp(this)

    new ObjectOutputStream(output).writeObject(this)

    val byteArray = new Array[Byte](4 * 1024)



    Future {
      try {
        var len = input.read(byteArray)
        while (len != -1) {
          output.write(byteArray, 0, len)
          len = input.read(byteArray)
        }
        output.flush()
        input.close()
        output.close()
        conn.close()
      } catch {
        case e: Exception =>
          e.printStackTrace()
      }
    }(ExecutionContext.global)
  }

  def receive(ds: InputStream, checkpoint: Checkpoint, clock: Clock): Future[Unit] = {
    fileHelper.checkSandbox(path)

    val filePath = directory + path
    // If the path does not exist yet, create the necessary parent folders

    val tmpFile = File.createTempFile(path, ".tmp")
    val output = new FileOutputStream(tmpFile)
    val input = new BufferedInputStream(ds)

    val byteArray = new Array[Byte](4 * 1024)

    clock.receiveStamp(this)

    Future {
      try {
        var len = input.read(byteArray)
        while (len != -1) {

          output.write(byteArray, 0, len)

          len = input.read(byteArray)

        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
      }

      checkpoint.swapFileByVersion(this, tmpFile)

      input.close()
      output.close()
    }(ExecutionContext.global)
  }

}
