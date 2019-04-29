import java.io.{ObjectInputStream, ObjectOutputStream}
import java.net.{InetAddress, ServerSocket, Socket}
import java.util
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import scala.collection.mutable.ListBuffer
import scala.math.Ordered.orderingToOrdered
import scala.util.Random

// general master definition
trait Master[D] extends Server[D] {
  def clientWrite(stampedBayouWrite: StampedBayouWrite[D]) {
    writeCommitted(stampedBayouWrite)
  }

  def antiEntropy(thatTentativeStack: ListBuffer[StampedBayouWrite[D]], thatCommittedStack: ListBuffer[StampedBayouWrite[D]]) = synchronized {
    for (w <- thatCommittedStack) {
      if (!(this.committedStack contains w)) {
        writeCommitted(w)
      }
    }

    for (w <- thatTentativeStack) {
      if (!(this.committedStack contains w)) {
        writeCommitted(w)
      }
    }
  }

  override def clientRead(bayouRead: BayouReadRequest[D]): ListBuffer[D] = bayouRead.query(committedDatabase)

}

// general slave definition
trait Slave[D] extends Server[D] {
  def clientWrite(stampedBayouWrite: StampedBayouWrite[D]) {
    writeTentative(stampedBayouWrite)
  }

  def antiEntropy(thatTentativeStack: ListBuffer[StampedBayouWrite[D]], thatCommittedStack: ListBuffer[StampedBayouWrite[D]]) = synchronized {
    for (w <- thatCommittedStack) {
      if (!(this.committedStack contains w)) {
        writeCommitted(w)
      }
    }
    // its gonna be uggo boys
    var tentativeTemp = new ListBuffer[StampedBayouWrite[D]]()
    for (w <- this.tentativeStack) {
      if (!(this.committedStack contains w)) {
        tentativeTemp += w
      }
    }
    for (w <- thatTentativeStack) {
      if (!((tentativeTemp contains w) || (this.committedStack contains w))) {
        tentativeTemp += w
      }
    }

    tentativeTemp.sortBy(sbw => sbw) // already ordered, need to be explicit
    this.tentativeStack = ListBuffer[StampedBayouWrite[D]]()
    this.tentativeDatabase = this.committedDatabase.clone()

    for (w <- tentativeTemp) {
      writeTentative(w)
    }
  }
}

// parant definitions used in master and slaves
trait Server[D] {
  val id = Ip.getIp
  val knownServers: util.List[String]
  val rand = new Random
  val port = 9999
  var committedDatabase: ListBuffer[D] = new ListBuffer()
  var tentativeDatabase: ListBuffer[D] = new ListBuffer()
  var committedStack: ListBuffer[StampedBayouWrite[D]] = new ListBuffer()
  var tentativeStack: ListBuffer[StampedBayouWrite[D]] = new ListBuffer()

  def run() = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val t = new Thread {
      override def run(): Unit = {
        initAntiEntropy()
      }
    }
    ex.scheduleAtFixedRate(t, 30, 60, TimeUnit.SECONDS)
    t.start()

    try {
      val serverSocket: ServerSocket = new ServerSocket(port)
      while (true) {
        val client = serverSocket.accept()
        handleRequest(client)
      }
    } catch {
      case e: Exception => System.err.println(e.getMessage)
    }
  }


  def handleRequest(client: Socket): Unit = {
    try {
      val in = new ObjectInputStream(client.getInputStream)
      val out = new ObjectOutputStream(client.getOutputStream)
      val t = new Thread {
        override def run(): Unit = {
          var bayouResponse: BayouResponse[D] = null
          in.readObject() match {
            case bayouRead: BayouReadRequest[D] => {
              val buffer = clientRead(bayouRead)
              bayouResponse = new BayouReadResponse[D](buffer)
            }
            case bayouWrite: BayouWriteRequest[D] => {
              val success = clientWrite(bayouWrite)
              bayouResponse = new BayouWriteResponse[D](success)
            }
            case bayouAntiEntropy: BayouAntiEntropyRequest[D] => {
              antiEntropy(bayouAntiEntropy.tentativeStack, bayouAntiEntropy.committedStack)
              println("{" +
                "\"class_type\":"+s""""${Server.this.getClass.toString}",""" +
                "\"message_type\":"+"\"anti_entropy_request\"," +
                "\"ip\":"+s""""${id}",""" +
                "\"timestamp\":"+s""""${System.currentTimeMillis()}",""" +
                "\"tentative_stack\":"+s""""${tentativeStack.mkString("[",",","]")}",""" +
                "\"commited_stack\":"+s""""${committedStack.mkString("[",",","]")}",""" +
                "\"tentative_db\":"+s""""${tentativeDatabase.mkString("[",",","]")}",""" +
                "\"commited_db\":"+s""""${committedDatabase.mkString("[",",","]")}"""" +
                "}")
              bayouResponse = new BayouAntiEntropyResponse[D](tentativeStack, committedStack)
            }
              case _ => throw new UnsupportedOperationException("Unknown request type received on server")
          }
          out.writeObject(bayouResponse)
        }
      }
      t.start()
    } catch {
      case e: Exception => System.err.println(e.getMessage);
    }
  }

  def writeCommitted(stampedBayouWrite: StampedBayouWrite[D]) {
    stampedBayouWrite.doBayouWrite(committedDatabase)
    committedStack += stampedBayouWrite
  }

  def writeTentative(stampedBayouWrite: StampedBayouWrite[D]) {
    stampedBayouWrite.doBayouWrite(tentativeDatabase)
    tentativeStack += stampedBayouWrite
  }

  def stamp(bayouWrite: BayouWriteRequest[D]): StampedBayouWrite[D] = {
    Thread.sleep(10) // make sure stamps are unique
    new StampedBayouWrite(bayouWrite, new BayouStamp(System.currentTimeMillis(), this.id))
  }

  def clientWrite(bayouWrite: BayouWriteRequest[D]): Boolean = {
    try {
      clientWrite(stamp(bayouWrite))
      true
    } catch {
      case e: Exception => {
        System.err.println(s"Write Operation Failed: $bayouWrite")
        false
      }
    }
  }

  def clientRead(bayouRead: BayouReadRequest[D]): ListBuffer[D] = bayouRead.query(tentativeDatabase)

  def clientWrite(stampedBayouWrite: StampedBayouWrite[D])

  def antiEntropy(otherTentavive: ListBuffer[StampedBayouWrite[D]], otherCommited: ListBuffer[StampedBayouWrite[D]])

  def initAntiEntropy(): Unit = {
    val pickedServerIp = knownServers.get(rand.nextInt(knownServers.size()))

    val localhost: InetAddress = InetAddress.getLocalHost
    val antiEntropyRequest = new BayouAntiEntropyRequest[D](localhost.getHostAddress, tentativeStack, committedStack)
    try {
      val socket = new Socket(InetAddress.getByName(pickedServerIp), port)
      val out = new ObjectOutputStream(socket.getOutputStream)
      val in = new ObjectInputStream(socket.getInputStream)
      out.writeObject(antiEntropyRequest)
      out.flush()

      // read response
      in.readObject() match {
        case resp: BayouAntiEntropyResponse[D] => {

          antiEntropy(resp.tentativeStack, resp.committedStack)
          println("{" +
            "\"class_type\":"+s""""${this.getClass.toString}",""" +
            "\"message_type\":"+"\"anti_entropy_response\"," +
            "\"ip\":"+s""""${id}",""" +
            "\"timestamp\":"+s""""${System.currentTimeMillis()}",""" +
            "\"tentative_stack\":"+s""""${tentativeStack.mkString("[",",","]")}",""" +
            "\"commited_stack\":"+s""""${committedStack.mkString("[",",","]")}",""" +
            "\"tentative_db\":"+s""""${tentativeDatabase.mkString("[",",","]")}",""" +
            "\"commited_db\":"+s""""${committedDatabase.mkString("[",",","]")}"""" +
            "}")
        }
        case _ => throw new UnsupportedOperationException("Unknown anti-entropy response type received on server")
      }

      out.close()
      in.close()
      socket.close()

    } catch {
      case e: Exception => System.err.println(e.getMessage);
    }
  }
}

//Timestamp used for requests
class BayouStamp(val timeStamp: Long, val serverStamp: String) extends Ordered[BayouStamp] with Serializable {
  def compare(that: BayouStamp): Int =
    (this.timeStamp, this.serverStamp) compareTo(that.timeStamp, that.serverStamp)

  override def toString = s"($timeStamp, $serverStamp)"

  override def equals(that: Any): Boolean = {
    that match {
      case that: BayouStamp => that.timeStamp.equals(this.timeStamp) && that.serverStamp.equals(this.serverStamp)
      case _ => false
    }
  }
}

// Requests stamped with timestamp used on stacks
class StampedBayouWrite[D](val bayouWrite: BayouWriteRequest[D], val bayouStamp: BayouStamp) extends Ordered[StampedBayouWrite[D]] with Serializable {
  def doBayouWrite(data: ListBuffer[D]) {
    if (bayouWrite.dependencyCheck(data)) {
      bayouWrite.update(data)
    } else {
      bayouWrite.mergeProcedure(data)
    }
  }

  def compare(that: StampedBayouWrite[D]): Int = this.bayouStamp compareTo that.bayouStamp

  override def toString = s"$bayouStamp"

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: StampedBayouWrite[D] => that.bayouWrite.equals(this.bayouWrite) && that.bayouStamp.equals(this.bayouStamp)
      case _ => false
    }
  }
}