import java.io._
import java.net._

class ClientSocket[D](ip: String, port: Integer) {

  @throws(classOf[UnsupportedOperationException])
  def send(server: String, obj: BayouRequest[D]): BayouResponse[D] = {
    try {
      val socket = new Socket(InetAddress.getByName(server), port)
      val out = new ObjectOutputStream(socket.getOutputStream)
      val in = new ObjectInputStream(socket.getInputStream)
      out.writeObject(obj)
      out.flush()

      // read response
      val result = in.readObject() match {
        case x: BayouReadResponse[D] => x
        case y: BayouWriteResponse[D] => y
        case z: BayouAntiEntropyResponse[D] => {
          throw new UnsupportedOperationException("Anti-entropy response received on client")
        }
        case _ => {
          throw new UnsupportedOperationException("Unknown response type received on client")
        }
      }
      out.close()
      in.close()
      socket.close()
      result

    } catch {
      case e: Exception => {
        System.err.println(e.getMessage)
        throw e
      }
    }
  }
}
