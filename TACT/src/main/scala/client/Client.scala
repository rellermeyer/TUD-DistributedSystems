package main.scala.client

import java.rmi.Naming
import java.time.LocalDateTime

import main.scala.tact.Tact

object Client {

  /**
    * @param args
    */
  def main(args: Array[String]): Unit = {
    val rmiServer = args(0)
    val replicaId = args(1)
    val readOrWrite = args(2)
    val key = args(3).toCharArray()(0)

    val start = System.currentTimeMillis()
    val server = Naming.lookup("//" + rmiServer + "/" + replicaId) match {
      case s: Tact => s
      case other => throw new RuntimeException("Wrong objesct: " + other)
    }

    if (readOrWrite == "read") {
      val value = server.read(key)
      val latency = System.currentTimeMillis() - start

      println("[" + LocalDateTime.now() + "][Client] Read key = " + key + " and value = " + value + " in " + latency + "ms")
    } else if (readOrWrite == "write") {
      val value = args(4).toInt
      server.write(key, value)
      val latency = System.currentTimeMillis() - start

      println("[" + LocalDateTime.now() + "][Client] Write key = " + key + " and value = " + value + " in " + latency + "ms")
    }
  }
}
