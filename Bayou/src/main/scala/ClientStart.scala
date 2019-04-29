// Definition of a simple client, sends 3 strings
object ClientStart {
  def main(args: Array[String]): Unit = {
    val client = new Client[String](args(0))

    val bayou_write1 = new SimpleWrite(args(1), client.ip)
    val bayou_write2 = new SimpleWrite(args(2), client.ip)
    val bayou_write3 = new SimpleWrite(args(3), client.ip)

    try {
      client.send(bayou_write1)
    }
    catch {
      case e: Exception => {
        print("Send failed, restarting")
        System.exit(1)
      }
    }

    Thread.sleep(2000)

    try {
      client.send(bayou_write1)
    }
    catch {
      case e: Exception => {
        println("Send failed, restarting")
        System.exit(1)
      }
    }

    Thread.sleep(2000)

    try {
      client.send(bayou_write1)
    }
    catch {
      case e: Exception => {
        println("Send failed, restarting")
        System.exit(1)
      }

    }
    Thread.sleep(2000)

    val bayou_read = new StartsWithReadRequest(args(4), client.ip)
    try {
      client.send(bayou_write1)
    }
    catch {
      case e: Exception => {
        println("Send failed, restarting")
        System.exit(1)
      }
    }
    Thread.sleep(2000)
  }
}
