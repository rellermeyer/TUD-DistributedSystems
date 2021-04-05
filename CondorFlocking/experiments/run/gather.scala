import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets
import scala.io.Source

case class JobInsert(num: Int)

case class Activity(num: Int, time: Long, name: String, busy: Long, available: Long){
  def busyRatio(): Double ={
    return busy * 1.0 / (busy + available)
  }

  def availableRatio(): Double ={
    return available * 1.0 / (busy + available)
  }
}
case class JobTiming(num: Int, time: Long, name: String, id: String, runtime: Long, total: Long)
case class Cpuload(num: Int, time: Long, name: String, load: Double)

class WorkstationLog(logPath: String, logActivities: List[Activity], logJobs: List[JobTiming], firstJob: JobInsert){
  val path = logPath
  val activities = logActivities
  val jobs = logJobs
}

object gather {

	def main(args: Array[String]): Unit = {
    var logs: List[WorkstationLog] = List()
    var loads: List[Cpuload] = List()
    var minStartTime: Long = Long.MaxValue
		for(file <- args){
      var num = 0;
      var firstJobInsert: JobInsert = null
      var activities: List[Activity] = List()
      var jobs: List[JobTiming] = List()
      val lines = Source.fromFile(file, "UTF-8").getLines
			for (line <- lines){
				val activityPattern = ".*,([0-9]*) Workstation (.*): Busy for ([0-9]*) ms, Available for ([0-9]*) ms".r;
        val jobPattern = ".*,([0-9]*) Submitter (.*): Got result for (.*) in (\\d*) ms \\((\\d*) ms total\\): .*".r;
        val cpuPattern = ".*,([0-9]*) (.*) CPU Load (.*)".r;
        val insertPattern = ".*,Received .* Available .*".r;
        line match {
					case activityPattern(time, name, busy, avail) => if(avail != 0){
            activities = activities ::: List(Activity(num, time.toLong, name, busy.toLong, avail.toLong))
            num += 1
          }
          case jobPattern(time, name, id, runtime, total) =>
            jobs = jobs ::: List(JobTiming(num, time.toLong, name, id, runtime.toLong, total.toLong))
            num += 1
          case cpuPattern(time, name, load) =>
            loads = loads ::: List(Cpuload(num, time.toLong, name, load.toDouble))
            num += 1
					case insertPattern() => if(firstJobInsert == null) {
            firstJobInsert = JobInsert(num)
            num += 1
          }
          case _ =>
				}
			}
      if(firstJobInsert != null){
        // find last activity before the first job was inserted.
        // this will be the start time of the experiment
        activities.filter(_.num < firstJobInsert.num).lastOption match{
          case Some(a) => minStartTime = Math.min(minStartTime, a.time)
          case None =>
        }
      }
      if(!activities.isEmpty){
        // only logs with workstation activity count
        logs = logs ::: List(new WorkstationLog(file, activities, jobs, firstJobInsert))
      } else{
        println(s"Skipping file ${file}")
      }
		}

    var maxTime: Long = 0
    for(log <- logs){
      println(log.path)
      log.jobs.lastOption match {
        case Some(j) => maxTime = Math.max(maxTime, j.time - minStartTime)
        case None =>
      }
    }
    var endTime = minStartTime + maxTime
    println("Max time = "+maxTime+" ms, minStartTime = "+minStartTime)
    val jobSb = StringBuilder.newBuilder
    val activitySb = StringBuilder.newBuilder
    val cpuSb = StringBuilder.newBuilder

    // csv headers
    jobSb.append("time (since java epoch),submitter name,uuid,runtime(jar),total runtime(start shadow to receive result)\n")
    activitySb.append("time (since java epoch),execution machine name,busy (ms),available (ms),busy ratio (0 to 1),available ratio (0 to 1)\n")
    cpuSb.append("time (since java epoch),time since start (ms),machine name,load (0 to 1)\n")


    for(log <- logs){
      val afterEnd = log.activities.filter(_.time > endTime)
      if(afterEnd.nonEmpty){

        val firstActivity = log.activities.filter(_.time <= minStartTime).last

        val a = afterEnd.head
        val activity = Activity(a.num, a.time, a.name, a.busy, a.available - firstActivity.available)
        activitySb.append(f"${activity.time},${activity.name},${activity.busy},${activity.available},${activity.busyRatio()},${activity.availableRatio()}\n")
        println(s"Log ${log.path} had activity ${activity} ${activity.busyRatio()} ${activity.availableRatio()}")
      }
      for(job <- log.jobs){
        jobSb.append(f"${job.time},${job.name},${job.id},${job.runtime},${job.total}\n")
      }
    }
    var managers: Set[String] = Set()
    for(cpuLoad <- loads){
      managers = managers + cpuLoad.name
      cpuSb.append(f"${cpuLoad.time},${cpuLoad.time - minStartTime},${cpuLoad.name},${cpuLoad.load}\n")
    }
    println(managers)

    var loadMap: Map[String, Double] = Map()
    val cpuPerMachineSb = StringBuilder.newBuilder
    cpuPerMachineSb.append("time (since java epoch),time since bag start (ms)")
    for(manager <- managers){
      cpuPerMachineSb.append(s",${manager}")
      loadMap = loadMap + (manager -> 0)
    }
    cpuPerMachineSb.append("\n")

    for(cpuLoad <- loads.sortBy(_.time)){
      if(cpuLoad.time - minStartTime > 0){
        cpuPerMachineSb.append(f"${cpuLoad.time},${cpuLoad.time - minStartTime}")
        for(manager <- managers){
          if(manager == cpuLoad.name){
            loadMap = loadMap + (manager -> cpuLoad.load)
          }
          cpuPerMachineSb.append(","+loadMap.getOrElse(manager, () => 0))
        }
        cpuPerMachineSb.append("\n")
      }
    }
    for(manager <- managers){
      val machineCpuSb = StringBuilder.newBuilder
      // csv header
      machineCpuSb.append("time (since java epoch),machine name,load (0 to 1)\n")
      for(cpuLoad <- loads.sortBy(_.time).filter(_.name == manager).filter(_.time - minStartTime > 0)){
        machineCpuSb.append(f"${cpuLoad.time},${cpuLoad.time - minStartTime},${cpuLoad.name},${cpuLoad.load}\n")
      }
      Files.write(Paths.get(s"cpuload_${manager}.csv"), machineCpuSb.toString().getBytes(StandardCharsets.UTF_8))
    }
    Files.write(Paths.get("jobs.csv"), jobSb.toString().getBytes(StandardCharsets.UTF_8))
    Files.write(Paths.get("activities.csv"), activitySb.toString().getBytes(StandardCharsets.UTF_8))
    Files.write(Paths.get("cpuload.csv"), cpuSb.toString().getBytes(StandardCharsets.UTF_8))
    Files.write(Paths.get("cpuloadPerMachine.csv"), cpuPerMachineSb.toString().getBytes(StandardCharsets.UTF_8))
    Files.write(Paths.get("results.csv"), s"Runtime (ms)\n${maxTime}".getBytes(StandardCharsets.UTF_8))
	}

}