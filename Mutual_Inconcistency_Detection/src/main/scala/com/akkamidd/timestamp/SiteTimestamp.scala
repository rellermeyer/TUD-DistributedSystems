package com.akkamidd.timestamp

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.timestamp.MasterSiteTimestamp.{Broadcast, MasterTimestampProtocol}
import org.slf4j.Logger

import java.io.PrintWriter

object SiteTimestamp {
  // TimestampProtocol: The messages that define the protocol between Sites that use timestamp algorithm
  sealed trait TimestampProtocol

  final case class FileUpload(
                               fileName: String,
                               timestamp: String,
                               parent: ActorRef[MasterTimestampProtocol],
                               partitionSet: Set[ActorRef[TimestampProtocol]]
                             ) extends TimestampProtocol
  final case class FileUpdate(
                               fileName: String,
                               parent: ActorRef[MasterTimestampProtocol],
                               partitionSet: Set[ActorRef[TimestampProtocol]]
                             ) extends TimestampProtocol
  final case class FileDuplicate(
                                  fileName: String,
                                  timestamp: String,
                                  parent: ActorRef[MasterTimestampProtocol],
                                  partitionSet: Set[ActorRef[TimestampProtocol]]
                                ) extends TimestampProtocol
  final case class FileUpdatedConfirm(
                                       fileName: String,
                                       updatedTimestamp: String,
                                       siteActor: ActorRef[TimestampProtocol]
                                     ) extends TimestampProtocol
  final case class Merged(
                           to: ActorRef[TimestampProtocol],
                           parent: ActorRef[MasterTimestampProtocol],
                           partitionSet: Set[ActorRef[TimestampProtocol]],
                           writerIcd: Option[PrintWriter]
                         ) extends TimestampProtocol
  final case class CheckInconsistency(
                                       fileList: Map[String, (String, String)],
                                       parent: ActorRef[MasterTimestampProtocol],
                                       partitionSet: Set[ActorRef[TimestampProtocol]],
                                       writerIcd: Option[PrintWriter]
                                     ) extends TimestampProtocol
  final case class ReplaceFileList(
                                    fileListToReplace: Map[String, (String, String)]
                                  ) extends TimestampProtocol



  def apply(debugMode: Boolean): Behavior[TimestampProtocol] =
    // A hashmap mapping filename to the timestamp
    fromMap(Map[String, (String, String)](), debugMode)

  def fromMap(fileList: Map[String, (String, String)], debugMode: Boolean): Behavior[TimestampProtocol] =  Behaviors.setup {
    context =>
      Behaviors.receiveMessage {

        /**
         * Upload file onto the current site. A new entry is added to the file list and sends to other sites to duplicate.
         */
        case FileUpload(fileName: String, timestamp: String, parent: ActorRef[MasterTimestampProtocol], partitionSet: Set[ActorRef[TimestampProtocol]]) =>
          // Check if the file already exists in the file list.
          if (fileList.contains(fileName)) {
            context.log.error(s"[FileUpload] File name = $fileName already exists in fileList = $fileList")
            Behaviors.same
          }

          // Append new filename timestamp pair to the list. Use same timestamp for previous as for new.
          val newFileList = fileList + (fileName -> (timestamp, timestamp))

          parent ! Broadcast(
            FileDuplicate(fileName = fileName, timestamp = timestamp, parent = parent, partitionSet),
            context.self,
            partitionSet
          )

          context.log.info(s"[FileUpload] File uploaded! File = $fileName , fileList = $newFileList")

          fromMap(newFileList, debugMode)


        /**
         * Updates the timestamp related to a file and calls broadcast such that other sites know about the update.
         */
        case FileUpdate(fileName: String, parent: ActorRef[MasterTimestampProtocol], partitionList: Set[ActorRef[TimestampProtocol]]) =>
          // Check if the hashFile exists
          if (fileList.contains(fileName)) {

            // Increment the versionVector corresponding to originPointer by 1.
            val newTimestamp = System.currentTimeMillis.toString
            val newFileList = updateFileList(fileList = fileList, fileName = fileName, newVal = newTimestamp)

            context.log.info(s"[FileUpdate] File $fileName is updated. fileList becomes = $newFileList")
            parent ! Broadcast(
              FileUpdatedConfirm(fileName = fileName, updatedTimestamp = newTimestamp, siteActor = context.self),
              context.self,
              partitionList
            )

            fromMap(newFileList, debugMode)
          } else {
            context.log.error(s"[FileUpdate] File = $fileName does not exist in fileList = $fileList")
            fromMap(fileList, debugMode)
          }


        /**
         * Duplicates file onto current site and broadcast updated file list to other sites.
         */
        case FileDuplicate(fileName: String, timestamp: String, parent, partitionSet: Set[ActorRef[TimestampProtocol]]) =>
          val siteName = context.self.path.name
          // Check if fileList actually keeps track of the file
          if (!fileList.contains(fileName)) {
            val newFileList = fileList + (fileName -> (timestamp, timestamp))

            if (debugMode) {
              context.log.info(s"[FileDuplicate] site $siteName has duplicated $fileName at timestamp $timestamp. fileList $newFileList.")
            }

            parent ! Broadcast(
              FileDuplicate(fileName = fileName, timestamp = timestamp, parent = parent, partitionSet),
              context.self,
              partitionSet
            )

            fromMap(newFileList, debugMode)

          } else {
            val newFileList = mergeFileList(fileList, fileName, (timestamp, timestamp))

            if (debugMode) {
              context.log.info(s"[FileDuplicate] site $siteName has file with name $fileName. fileList $newFileList.")
            }

            fromMap(newFileList, debugMode)
          }


        /**
         * Confirms a file updated has succeeded.
         */
        case FileUpdatedConfirm(fileName: String, updatedTimestamp: String, fromSite: ActorRef[TimestampProtocol]) =>
          if (fileList.contains(fileName)) {
            val newFileList = updateFileList(fileList, fileName, updatedTimestamp)

            if (debugMode) {
              context.log.info(s"[FileUpdatedConfirm] File = $fileName, version = $updatedTimestamp, newFileList = $newFileList. Site ${context.self.path.name}")
            }

            fromMap(newFileList, debugMode)
          } else {
            if (debugMode) {
              context.log.error(s"File = $fileName not in fileList = $fileList. newVersion $updatedTimestamp. Site ${context.self.path.name}")
            }

            Behaviors.unhandled
          }


        /**
         * Merges file list of current site and an other site.
         */
        case Merged(to, parent, partitionSet, writerIcd) =>
          if (debugMode) {
            context.log.info(s"[Merged] sending fileList of site ${context.self.path.name} to site ${to.path.name}. FileList sent: $fileList")
          }

          to ! CheckInconsistency(fileList, parent, partitionSet, writerIcd)
          fromMap(fileList, debugMode)


        /**
         * Performs inconsistency detection for the timestamp algorithm.
         */
        case CheckInconsistency(fromFileList, parent, partitionSet, writerIcd) =>
          val newFileList = inconsistencyDetection(context.log, fileList, fromFileList, debugMode, writerIcd)
          if (newFileList.nonEmpty) {
            parent ! Broadcast(
              ReplaceFileList(newFileList),
              context.self,
              partitionSet
            )
            fromMap(newFileList, debugMode)
          } else {
            fromMap(fileList, debugMode)
          }


        /**
         * Replace current file list with another.
         */
        case ReplaceFileList(newFileList) =>
          if (debugMode) {
            context.log.info(s"[ReplaceFileList.${context.self.path.name}] Replaced FileList with newFileList $newFileList")
          }

          fromMap(newFileList, debugMode)
      }
  }

  /**
   * Helper method for merging file lists.
   * @param fileList File list to merge.
   * @param fileName The name of the file to check for.
   * @param timestamp Timestamp of the file.
   * @return merged file list.
   */
  private def mergeFileList(
                             fileList: Map[String, (String, String)],
                             fileName: String,
                             timestamp: (String, String)
                           ): Map[String, (String, String)] =
  {
    if(!fileList.contains(fileName)) {
      val newFileList = fileList + (fileName -> timestamp)
      newFileList
    }
    else { // or if fileList already has the file
      var currentTimestamp = fileList(fileName)
      if(currentTimestamp._2.toLong <= timestamp._2.toLong){
        currentTimestamp = timestamp
      }

      val newFileList = fileList + (fileName -> currentTimestamp)
      newFileList
    }
  }

  /**
   * Helper method for updating a file list
   * @param fileList File list used by the current Site.
   * @param fileName File to change.
   * @param newVal New timestamp to change to.
   * @return updated file list.
   */
  private def updateFileList(
                              fileList: Map[String, (String, String)],
                              fileName: String,
                              newVal: String
                            ): Map[String, (String, String)] = {
    fileList.updatedWith(key = fileName) {
      case Some(oldVal) =>
        if (oldVal._2 < newVal) {
          Some((oldVal._2, newVal))
        } else {
          Some(oldVal)
        }
      case None =>
        Some(("", ""))
    }
  }

  /**
   * Helper method for merging two file lists
   * @param log Logger for printing out information to the terminal.
   * @param fileListP1 File list of Partition 1.
   * @param fileListP2 File List of Partition 2.
   * @return Merged file List.
   */
  private def inconsistencyDetection(
                                      log: Logger,
                                      fileListP1: Map[String, (String, String)],
                                      fileListP2: Map[String, (String, String)],
                                      debugMode: Boolean,
                                      writerIcd: Option[PrintWriter]
                                    ): Map[String, (String, String)] = {
    var counter:Int = 0

    // Zip on the same fileName
    val zippedLists = for {
      (file1, version1) <- fileListP1
      (file2, version2) <- fileListP2
      if file1 == file2
    } yield (file1, version1, version2)

    // To keep the unique files in both the first and second partition.
    val uniqueFilesP1 = fileListP1.filter(f => !fileListP2.contains(f._1))
    val uniqueFilesP2 = fileListP2.filter(f => !fileListP1.contains(f._1))

    var fileList = Map[String, (String, String)]()

    for ((filename, time1, time2) <- zippedLists) {
      // Check whether one of the timestamps is dominant over the other.
      if(time1._1.toLong == time2._1.toLong && time1._2.toLong == time2._2.toLong) {
        log.info(s"[Consistency Detected] For File $filename -> no version conflict detected: $time1 <=> $time2")
        fileList = fileList + (filename -> time1)

      } else if (time2._1.toLong == time1._2.toLong) { // (11, 30) and (10, 11)
        log.info(s"[Inconsistency Detected] For File $filename -> Compatible version conflict detected: $time1 > $time2")
        fileList = fileList + (filename -> time1)
        counter += 1
      } else if  (time1._1.toLong == time2._2.toLong) { // (10, 11) and (11, 30)
        log.info(s"[Inconsistency Detected] For File $filename -> Compatible version conflict detected: $time1 < $time2")
        fileList = fileList + (filename -> time2)
        counter += 1

      } else {
        // We pick the version with the more updated timestamp regardless of the previous timestamps.
        if (time1._2.toLong > time2._2.toLong) {
          log.info(s"[Inconsistency Detected] For File $filename -> Incompatible version conflict detected: $time1 > $time2")
          fileList = fileList + (filename -> time1)
        } else {
          log.info(s"[Inconsistency Detected] For File $filename -> Incompatible version conflict detected: $time1 < $time2")
          fileList = fileList + (filename -> time2)
        }
        counter += 1
      }
    }

    fileList = fileList ++ uniqueFilesP1 ++ uniqueFilesP2

    if (debugMode) {
      log.info(s"[LOGGER ID] $fileList. FL1 $fileListP1  FL2 $fileListP2")
    }

    writerIcd match {
      case Some(pwd) =>
        pwd.println(counter.toString)
      case None =>
    }

    fileList
  }

}
