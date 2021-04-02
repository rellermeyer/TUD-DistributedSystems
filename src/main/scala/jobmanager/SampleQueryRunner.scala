// package jobmanager

import java.rmi.Naming
import scala.collection.mutable.ArrayBuffer
import jobmanager.JobManagerInterface

/* Object for running a query. Can specify the types of operations (only map and reduce), the prallelism of each
 * operation and the data size. The parallelism needs to match with the number of operations. The map operation will
 * increase the value received from the upstream by 1 and send it to its downstream, reduce will be the sink that then
 * sends the final result to the JobManager.
 *
 */
object SampleQueryRunner {
  def main(args: Array[String]): Unit = {
    // (map, 3), (map, 2), (reduce, 1)
    val ops = Array("map", "map", "reduce")
    val parallelisms = Array(7, 2, 1)
    val dataSize = 4000

    val jobManager =
      Naming.lookup("jobmanager").asInstanceOf[JobManagerInterface]
    val success = jobManager.runJob(ops, parallelisms, dataSize)

    if (success) {
      println("Started query sucessfully")
    } else {
      println("Job failed")
    }
  }
}
