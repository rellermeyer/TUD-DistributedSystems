import java.util
import scala.collection.mutable.ListBuffer

// Definition for custom Read Request
class StartsWithReadRequest(val s: String, val ip: String) extends BayouReadRequest[String](ip) {
  override def query(list: ListBuffer[String]): ListBuffer[String] = list.filter((str: String) => str.startsWith(s))
}

// Definition for custom Write Request
class SimpleWrite(val s: String, val ip: String) extends BayouWriteRequest[String](ip) {
  override def update(data: ListBuffer[String]) {
    data += s
  }

  override def dependencyCheck(data: ListBuffer[String]): Boolean = true

  override def mergeProcedure(data: ListBuffer[String]) {
    println("Merge procedure failed")
  }
}

// Definition for custom Master
class MyMaster(override val knownServers: util.List[String]) extends Master[String]

// Definition for custom Slave
class MySlave(override val knownServers: util.List[String]) extends Slave[String]