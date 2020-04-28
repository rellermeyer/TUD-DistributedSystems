package org.orleans.silo.test

import org.orleans.silo.communication.ConnectionProtocol.SlaveInfo

object Test {

  def main(args: Array[String]): Unit = {
//    val grainMap: ConcurrentHashMap[String, List[GrainInfo]] =
    ////      new ConcurrentHashMap[String, List[GrainInfo]]()
    ////    grainMap.put("1234", List(new GrainInfo("abc", "123",123, GrainState.InMemory, 0)))
    ////
    ////    val grain: Option[GrainInfo] = grainMap.get("1234").find(x => x.address.equals("123") && x.port.equals(123))
    ////    val reportingGrain: GrainInfo = grain.get
    ////    reportingGrain.load = 5

    val slaves = scala.collection.mutable.HashMap[String, SlaveInfo]()

    for ((k, v) <- slaves) {
      val totalLoad: Int = 5
      v.totalLoad = totalLoad
    }

  }

}
