// package jobmanager

import java.rmi.Naming
import scala.collection.mutable.ArrayBuffer

object SampleQueryRunner {
    def main(args: Array[String]): Unit = {
        val jobManager = Naming.lookup("jobmanager").asInstanceOf[JobManagerInterface]
        jobManager.runStaticJob()
        println("Finished")
    }
}
