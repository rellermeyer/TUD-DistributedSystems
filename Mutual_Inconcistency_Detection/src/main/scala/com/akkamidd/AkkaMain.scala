package com.akkamidd

import akka.actor.typed.ActorSystem
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.{FileUpdateMasterSite, FileUploadMasterSite, MasterSiteProtocol}
import com.akkamidd.timestamp.MasterSiteTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp.MasterTimestampProtocol
import org.slf4j.Logger

import java.io.PrintWriter
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

object AkkaMain extends App {
  val numSiteActors = args(0).toLong
  val siteActorNames = List.range(0, numSiteActors).map("Site"+_.toString)

  val masterSystem = ActorSystem(MasterSite(debugMode = false), "MasterSite")
  var partitionList = UtilFuncs.spawnSites(masterSystem, siteActorNames, 3000)
  val writerIcd: Option[PrintWriter] = None

  println(s"$numSiteActors Site actors spawned successfully!")
  println(s"Site Actor Names: $siteActorNames")

  while (true) {
    StdIn.readLine match {
      case command if command contains "upload" =>
        val uploadCommand = command.split("-").tail
        val currTimestamp = System.currentTimeMillis.toString
        val siteToUpload = uploadCommand(0)
        val fileName = uploadCommand(1)

        UtilFuncs.callUploadFile(siteToUpload, currTimestamp, masterSystem, fileName, partitionList)

      case command if command contains "update" =>
        val updateCommand = command.split("-").tail
        val siteToUpload = updateCommand(0)
        val originPointerParts = updateCommand(1).tail.dropRight(1).split(",")
        val originPointer = (originPointerParts(0).trim, originPointerParts(1).trim)

        UtilFuncs.callUpdateFile(siteToUpload, originPointer, masterSystem, partitionList)

      case command if command contains "split" =>
        val splitCommand = command.split("-").tail

        val siteNameToSplit = splitCommand(0)
        if (splitCommand.length == 3) {
          val timeoutValue = splitCommand(1).toLong
          partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNameToSplit, timeoutValue, timeoutValue)
        } else {
          partitionList = UtilFuncs.callSplit(masterSystem, partitionList, siteNameToSplit, 1000, 1000)
        }

      case command if command contains "merge" =>
        val mergeCommand = command.split("-").tail
        val siteFrom = mergeCommand(0)
        val siteTo = mergeCommand(1)

        if (mergeCommand.length == 3) {
          val timeoutValue = mergeCommand(2).toLong
          partitionList = UtilFuncs.callMerge(siteFrom, siteTo, masterSystem, partitionList, timeoutValue, timeoutValue, writerIcd)
        } else {
          partitionList = UtilFuncs.callMerge(siteFrom, siteTo, masterSystem, partitionList, 1000, 1000, writerIcd)
        }

      case "quit" =>
        UtilFuncs.terminateSystem(masterSystem)
        System.exit(0)

      case _ =>
        println("Not a valid command!")
    }
  }

  UtilFuncs.terminateSystem(masterSystem)

}


object UtilFuncsTimestamp {
  /**
   * Initiates the termination of the actor system and awaits until all Actors in the system are shutdown.
   * @param actorSystem The Actor System that needs to be shutdown
   * @param awaitDuration The maximum duration to await for termination completion from the given system
   */
  def terminateSystem(
                       actorSystem: ActorSystem[MasterTimestampProtocol],
                       awaitDuration: Duration = Duration.Inf
                     ): Unit =
  {
    actorSystem.terminate()
    Await.ready(actorSystem.whenTerminated, awaitDuration)
  }

  /**
   * A interface that is used for calling the upload method
   * @param siteName The site where a file is going to be uploaded to
   * @param timestamp The current timestamp
   * @param masterSystem The master system where file is to be uploaded
   * @param fileName The name of the file that is going to be uploaded
   * @param partitionList The current partition list
   */
  def callUploadFile(
                      siteName: String,
                      timestamp: String,
                      masterSystem: ActorSystem[MasterTimestampProtocol],
                      fileName: String,
                      partitionList: List[Set[String]]
                    ): Unit =
  {
    masterSystem ! MasterSiteTimestamp.FileUploadMasterSite(siteName, timestamp, fileName, partitionList)
  }

  /**
   * A interface that is used for calling the update method
   * @param siteName The site where a file is going to be updated
   * @param originPointer The
   * @param masterSystem The master system where file is to be updated
   * @param partitionList The current partition list
   */
  def callUpdateFile(
                      siteName: String,
                      fileName: String,
                      masterSystem: ActorSystem[MasterTimestampProtocol],
                      partitionList: List[Set[String]]
                    ): Unit =
  {
    masterSystem ! MasterSiteTimestamp.FileUpdateMasterSite(siteName, fileName, partitionList)
  }

  /**
   * Send messages to MasterSite to instruct it to create a new Site Actor in the Actor System.
   * @param masterSystem Mastersite.
   * @param siteNameList List of Sitenames.
   * @param timeout Timeout for sleeping threads.
   * @param partitionList List of partitions.
   * @return creates a list of created sites.
   */
  def spawnSites(
                  masterSystem: ActorSystem[MasterTimestampProtocol],
                  siteNameList: List[String],
                  timeout: Long,
                  partitionList: List[Set[String]] = List(),
                ): List[Set[String]] =
  {
    siteNameList.foreach(siteName => {
      masterSystem ! MasterSiteTimestamp.SpawnSite(siteName)
    })

    Thread.sleep(timeout)

    siteNameList.toSet +: partitionList
  }

  /**
   *
   * @param siteNameFrom A site from the partition P that is going to be merged in another partition
   * @param siteNameTo A site from the partition that P is going to be merged into
   * @param masterSystem The master system where merge is going to happen
   * @param sitesPartitionedList The original partition list before merging
   * @param timeoutBeforeExec The timeout set before the merge as a laxity to make sure the previous operations are completed before merge start
   * @param timeoutAfterExec The timeout setting after the merge as a laxity to make sure the merge can be completed
   * @param writerIcd The file writer for ICD result
   * @return
   */
  def callMerge(
                 siteNameFrom: String,
                 siteNameTo: String,
                 masterSystem: ActorSystem[MasterTimestampProtocol],
                 sitesPartitionedList: List[Set[String]],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long,
                 writerIcd: Option[PrintWriter]
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = mergePartition(sitesPartitionedList, siteNameFrom, siteNameTo)
    if(!newPartitionList.equals(sitesPartitionedList)){
      masterSystem ! MasterSiteTimestamp.Merge(siteNameFrom, siteNameTo, newPartitionList, writerIcd)
    }

    Thread.sleep(timeoutAfterExec)

    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    newPartitionList
  }

  /**
   *
   * @param masterSystem The master system where splitting is going to happen
   * @param sitesPartitionedList The original site partition before splitting
   * @param siteAtWhichSplit The threshold of splitting. The sites whose name is lexicographically bigger than the threshold will be assigned to one partition
   *                         while the rest will be assigned to another one
   * @param timeoutBeforeExec The timeout set before the merge as a laxity to make sure the previous operations are completed before merge start
   * @param timeoutAfterExec The timeout setting after the merge as a laxity to make sure the merge can be completed
   * @return The
   */
  def callSplit(
                 masterSystem: ActorSystem[MasterTimestampProtocol],
                 sitesPartitionedList: List[Set[String]],
                 siteAtWhichSplit: String,
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = splitPartition(sitesPartitionedList, siteAtWhichSplit)

    Thread.sleep(timeoutAfterExec)

    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    newPartitionList
  }

  /**
   * Split a specific partition into two new subpartitions.
   * @param sitesPartitionedList The original partition list where splitting is going to happen
   * @param siteAtWhichSplit The threshold of splitting. The sites whose name is lexicographically bigger than the threshold will be assigned to one partition
   *                         while the rest will be assigned to another one
   * @return A new partition list after splitting
   */
  def splitPartition(
                      sitesPartitionedList: List[Set[String]],
                      siteAtWhichSplit: String
                    ): List[Set[String]] =
  {
    var newPartitionList:List[Set[String]] = sitesPartitionedList

    for (set <- newPartitionList){
      if (set.contains(siteAtWhichSplit)) {
        // the sites whose number is bigger than siteAtWhichSplit should be splitted away
        val partToSplit = set.filter(x => x.substring(4).toInt> siteAtWhichSplit.substring(4).toInt)
        // if the site is the biggest in the current partition, then nothing to split
        if (partToSplit.isEmpty){
          return sitesPartitionedList
        }
        // remove The old partition
        newPartitionList = newPartitionList.filter(!_.equals(set))
        // create new partition for the remaining part
        val setRemain = set -- partToSplit
        newPartitionList = newPartitionList :+ setRemain
        // create new partition for the partToSplit and append the new one to partition list
        newPartitionList = newPartitionList :+ partToSplit
        return newPartitionList
      }
    }

    sitesPartitionedList
  }

  /**
   * Merge two specific partitions into one
   * @param sitesPartitionedList The original partition list where merging is going to happen
   * @param firstSite A site in the first partition that is going to be merged
   * @param secondSite A site in the second partition that is going to be merged
   * @return A new partition list after merging
   */
  def mergePartition(
                      sitesPartitionedList: List[Set[String]],
                      firstSite: String,
                      secondSite: String
                    ): List[Set[String]] =
  {
    var setsToMerge: List[Set[String]] = List()
    var newPartitionList: List[Set[String]] = sitesPartitionedList

    if(firstSite == null || secondSite == null) {
      return newPartitionList
    }

    for(set <- sitesPartitionedList) {
      if(set.contains(firstSite) || set.contains(secondSite)) {
        // get the sets which need to be merged
        setsToMerge = setsToMerge :+ set
        // remove the set for the merge
        newPartitionList = newPartitionList.filter(!_.equals(set))
      }
    }

    // Two partitions should be merged at once
    if (setsToMerge.length != 2) {
      return sitesPartitionedList
    }

    var newPartition: Set[String] = Set()

    for(set <- setsToMerge) {
      newPartition = newPartition.union(set)
    }

    newPartitionList :+ newPartition
  }

  /**
   * Print the current partition list
   * @param sitesPartitionedList Current partition list
   * @param logger The console logger
   */
  def printCurrentNetworkPartition(
                                    sitesPartitionedList: List[Set[String]],
                                    logger: Logger
                                  ): Unit =
  {
    val result = new StringBuilder()

    result.append("The network partition is: " )
    for(set <- sitesPartitionedList) {
      result.append("{")
      for(site <- set) {
        result.append(site + ",")
      }
      // Remove last comma
      result.deleteCharAt(result.length() - 1)
      result.append("},")
    }
    // Remove last comma
    result.deleteCharAt(result.length()  - 1)
    logger.info(result.toString())
  }

}

object UtilFuncs {

  /**
   * Initiates the termination of the actor system and awaits until all Actors in the system are shutdown.
   * @param actorSystem The Actor System that needs to be shutdown
   * @param awaitDuration The maximum duration to await for termination completion from the given system
   */
  def terminateSystem(
                       actorSystem: ActorSystem[MasterSiteProtocol],
                       awaitDuration: Duration = Duration.Inf
                     ): Unit =
  {
    actorSystem.terminate()
    Await.ready(actorSystem.whenTerminated, awaitDuration)
  }

  /**
   * A interface that is used for calling the upload method
   * @param siteName The site where a file is going to be uploaded to
   * @param timestamp The current timestamp
   * @param masterSystem The master system where file is to be uploaded
   * @param fileName The name of the file that is going to be uploaded
   * @param partitionList The current partition list
   */
  def callUploadFile(
                  siteName: String,
                  timestamp: String,
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  fileName: String,
                  partitionList: List[Set[String]]
                ): Unit =
  {
    masterSystem ! FileUploadMasterSite(siteName, timestamp, fileName, partitionList)
  }

  /**
   * A interface that is used for calling the update method
   * @param siteName The site where a file is going to be updated
   * @param originPointer The
   * @param masterSystem The master system where file is to be updated
   * @param partitionList The current partition list
   */
  def callUpdateFile(
                  siteName: String,
                  originPointer: (String, String),
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  partitionList: List[Set[String]]
                ): Unit =
  {
    masterSystem ! FileUpdateMasterSite(siteName, originPointer, partitionList)
  }

  /**
   * Send messages to MasterSite to instruct it to create a new Site Actor in the Actor System.
   * @param masterSystem The Actor System that is used to send messages to the MasterSite
   * @param siteNameList The names of the Site Actors to spawn
   * @param timeout The incurred timeout after sending the Spawn Messages to the master sites to
   *                ensure all sites are spawned before continuing with other operations on the system
   * @param partitionList The initial partition list to use, List() by default
   * @return The partition list that contains 1 set containing the names of the spawned sites.
   *         We assume that all Site Actors are initially in 1 partition.
   */
  def spawnSites(
                  masterSystem: ActorSystem[MasterSiteProtocol],
                  siteNameList: List[String],
                  timeout: Long,
                  partitionList: List[Set[String]] = List(),
                ): List[Set[String]] =
  {
    siteNameList.foreach(siteName => {
      masterSystem ! MasterSite.SpawnSite(siteName)
    })

    Thread.sleep(timeout)

    siteNameList.toSet +: partitionList
  }

  /**
   *
   * @param siteNameFrom A site from the partition P that is going to be merged in another partition
   * @param siteNameTo A site from the partition that P is going to be merged into
   * @param masterSystem The master system where merge is going to happen
   * @param sitesPartitionedList The original partition list before merging
   * @param timeoutBeforeExec The timeout set before the merge as a laxity to make sure the previous operations are completed before merge start
   * @param timeoutAfterExec The timeout setting after the merge as a laxity to make sure the merge can be completed
   * @param writerIcd The file writer for ICD result
   * @return
   */
  def callMerge(
                 siteNameFrom: String,
                 siteNameTo: String,
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long,
                 writerIcd: Option[PrintWriter]
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = mergePartition(sitesPartitionedList, siteNameFrom, siteNameTo)
    if(!newPartitionList.equals(sitesPartitionedList)){
      masterSystem ! MasterSite.Merge(siteNameFrom, siteNameTo, newPartitionList, writerIcd)
    }

    Thread.sleep(timeoutAfterExec)

    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    newPartitionList
  }

  /**
   *
   * @param masterSystem The master system where splitting is going to happen
   * @param sitesPartitionedList The original site partition before splitting
   * @param siteAtWhichSplit The threshold of splitting. The sites whose name is lexicographically bigger than the threshold will be assigned to one partition
   *                         while the rest will be assigned to another one
   * @param timeoutBeforeExec The timeout set before the merge as a laxity to make sure the previous operations are completed before merge start
   * @param timeoutAfterExec The timeout setting after the merge as a laxity to make sure the merge can be completed
   * @return The
   */
  def callSplit(
                 masterSystem: ActorSystem[MasterSiteProtocol],
                 sitesPartitionedList: List[Set[String]],
                 siteAtWhichSplit: String,
                 timeoutBeforeExec: Long,
                 timeoutAfterExec: Long
               ): List[Set[String]] =
  {
    Thread.sleep(timeoutBeforeExec)

    val newPartitionList = splitPartition(sitesPartitionedList, siteAtWhichSplit)

    Thread.sleep(timeoutAfterExec)

    printCurrentNetworkPartition(newPartitionList, masterSystem.log)

    newPartitionList
  }

  /**
   * Split a specific partition into two new subpartitions.
   * @param sitesPartitionedList The original partition list where splitting is going to happen
   * @param siteAtWhichSplit The threshold of splitting. The sites whose name is lexicographically bigger than the threshold will be assigned to one partition
   *                         while the rest will be assigned to another one
   * @return A new partition list after splitting
   */
  def splitPartition(
                      sitesPartitionedList: List[Set[String]],
                      siteAtWhichSplit: String
                    ): List[Set[String]] =
  {
    var newPartitionList: List[Set[String]] = sitesPartitionedList

    for (set <- newPartitionList){
      if (set.contains(siteAtWhichSplit)) {
        // the sites whose number is bigger than siteAtWhichSplit should be splitted away
        //val partToSplit = set.filter(_>siteAtWhichSplit)
        val partToSplit = set.filter(x => x.substring(4).toInt> siteAtWhichSplit.substring(4).toInt)
        // if the site is the biggest in the current partition, then nothing to split
        if (partToSplit.isEmpty){
          return sitesPartitionedList
        }
        // remove The old partition
        newPartitionList = newPartitionList.filter(!_.equals(set))
        // create new partition for the remaining part
        val setRemain = set -- partToSplit
        newPartitionList = newPartitionList :+ setRemain
        // create new partition for the partToSplit and append the new one to partition list
        newPartitionList = newPartitionList :+ partToSplit
        return newPartitionList
      }
    }

    sitesPartitionedList
  }

  /**
   * Merge two specific partitions into one
   * @param sitesPartitionedList The original partition list where merging is going to happen
   * @param firstSite A site in the first partition that is going to be merged
   * @param secondSite A site in the second partition that is going to be merged
   * @return A new partition list after merging
   */
  def mergePartition(
                      sitesPartitionedList: List[Set[String]],
                      firstSite: String,
                      secondSite: String
                    ): List[Set[String]] =
  {
    var setsToMerge: List[Set[String]] = List()
    var newPartitionList: List[Set[String]] = sitesPartitionedList

    if(firstSite == null || secondSite == null) {
      return newPartitionList
    }

    for(set <- sitesPartitionedList) {
      if(set.contains(firstSite) || set.contains(secondSite)) {
        // get the sets which need to be merged
        setsToMerge = setsToMerge :+ set
        // remove the set for the merge
        newPartitionList = newPartitionList.filter(!_.equals(set))
      }
    }

    // Two partitions should be merged at once
    if (setsToMerge.length != 2) {
      return sitesPartitionedList
    }

    var newPartition: Set[String] = Set()

    for(set <- setsToMerge) {
      newPartition = newPartition.union(set)
    }

    newPartitionList :+ newPartition
  }

  /**
   * Print the current partition list
   * @param sitesPartitionedList Current partition list
   * @param logger The console logger
   */
  def printCurrentNetworkPartition(
                                    sitesPartitionedList: List[Set[String]],
                                    logger: Logger
                                  ): Unit =
  {
    val result = new StringBuilder()

    result.append("The network partition is: " )
    for(set <- sitesPartitionedList) {
      result.append("{")
      for(site <- set) {
        result.append(site + ",")
      }
      // Remove last comma
      result.deleteCharAt(result.length() - 1)
      result.append("},")
    }
    // Remove last comma
    result.deleteCharAt(result.length()  - 1)
    logger.info(result.toString())
  }
}