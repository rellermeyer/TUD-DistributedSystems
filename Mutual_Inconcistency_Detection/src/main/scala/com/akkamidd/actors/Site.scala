package com.akkamidd.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.MasterSite.Broadcast
import org.slf4j.Logger

import java.io.PrintWriter


object Site {
  // SiteProtocol: The messages that define the protocol between Sites
  sealed trait SiteProtocol

  final case class FileUpload(
                               timestamp: String,
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               fileName: String,
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
  final case class FileUpdate(
                               originPointer: (String, String),
                               parent: ActorRef[MasterSite.MasterSiteProtocol],
                               partitionSet: Set[ActorRef[SiteProtocol]]
                             ) extends SiteProtocol
  final case class FileDuplicate(
                                  originPointer: (String, String),
                                  versionVector: Map[String, Int],
                                  fileName: String,
                                  parent: ActorRef[MasterSite.MasterSiteProtocol],
                                  partitionSet: Set[ActorRef[SiteProtocol]]
                                ) extends SiteProtocol
  final case class FileUpdatedConfirm(
                                       originPointer: (String, String),
                                       updatedVersion: Int,
                                       siteActor: ActorRef[SiteProtocol]
                                     ) extends SiteProtocol
  final case class Merged(
                           to: ActorRef[SiteProtocol],
                           parent: ActorRef[MasterSite.MasterSiteProtocol],
                           partitionSet: Set[ActorRef[SiteProtocol]],
                           writerIcd: Option[PrintWriter]
                         ) extends SiteProtocol
  final case class CheckInconsistency(
                           fileList: Map[(String, String), Map[String, Int]],
                           parent: ActorRef[MasterSite.MasterSiteProtocol],
                           partitionSet: Set[ActorRef[SiteProtocol]],
                           writerIcd: Option[PrintWriter]
                         ) extends SiteProtocol
  final case class ReplaceFileList(
                                    fileListToReplace: Map[(String, String), Map[String, Int]]
                                  ) extends SiteProtocol

  /**
   * Method apply is called immediately when the site is spawned
   * @param debugMode Set to true to print the debugging information
   * @return return a hashmap that maintains the current state of the file list
   */
  def apply(debugMode: Boolean): Behavior[SiteProtocol] =
    fromMap(Map[(String, String), Map[String, Int]](), debugMode) // A hashmap mapping origin pointers of files to their corresponding version vectors

  /**
   * A state machine where state is reprensented by a file list. By receiving message it will moved to another state
   * @param fileList Current file list
   * @param debugMode Set to true to print the debugging information
   * @return a new state
   */
  def fromMap(fileList: Map[(String, String), Map[String, Int]], debugMode: Boolean): Behavior[SiteProtocol] =  Behaviors.setup {
    context =>
      Behaviors.receiveMessage {
        case FileUpload(timestamp, parent, fileName, partitionSet) =>
          val siteName = context.self.path.name

          // System wide unique ID of the file ensuring unique id for each file uploaded regardless of file name.
          val originPointer = (siteName, timestamp)

          // Check if the file already exists by checking the hash in the originPointers map.
          // Edge case: in case two files are uploaded at the same time which result in same hash.
          if (fileList.contains(originPointer)) {
            context.log.error(s"[FileUpload] originPointer = $originPointer already exists in fileList = $fileList")
            Behaviors.same
          }

          // Version vector is a list containing what version of a file the different sites have
          // example = versionVector: (A->1, B->2)
          val versionVector = Map[String, Int](siteName -> 0)
          val newFileList = fileList + (originPointer -> versionVector)

          parent ! Broadcast(
            FileDuplicate(originPointer = originPointer, versionVector = versionVector, fileName = fileName, parent = parent, partitionSet),
            context.self,
            partitionSet
          )

          context.log.info(s"[FileUpload] File $fileName uploaded! originPointer = $originPointer")

          fromMap(newFileList, debugMode)

        case FileUpdate(originPointer: (String, String), parent, partitionList) =>
          // Check if the hashFile exists
          if (fileList.contains(originPointer)) {
            val siteName = context.self.path.name

            // Increment the versionVector corresponding to originPointer by 1.
            val newVersion: Int = fileList(originPointer)(siteName) + 1
            val newFileList = updateFileList(fileList, originPointer, siteName, newVersion)

            context.log.info(s"[FileUpdate] File $originPointer is updated!")
            parent ! Broadcast(
              FileUpdatedConfirm(originPointer = originPointer, updatedVersion = newVersion, siteActor = context.self),
              context.self,
              partitionList
            )

            fromMap(newFileList, debugMode)
          } else {
            context.log.info(s"[FileUpdate] originPointer = $originPointer does not exist in fileList = $fileList")
            fromMap(fileList, debugMode)
          }

        case FileDuplicate(originPointer: (String, String), versionVector: Map[String, Int], filename: String, parent, partitionSet) =>
          val siteName = context.self.path.name
          // Check if site is already listed in version vector
          if (!versionVector.contains(siteName)) {
            // Check if fileList actually keeps track of the file
            if (!fileList.contains(originPointer)) {
              val newVersionVector = versionVector ++ Map(siteName -> 0)
              val newFileList = fileList + (originPointer -> newVersionVector)

              if (debugMode) {
                context.log.info(s"[FileDuplicate] site $siteName has duplicated $originPointer using version vector $versionVector. fileList $newFileList.")
              }

              parent ! Broadcast(
                FileDuplicate(originPointer = originPointer, versionVector = newVersionVector, fileName = filename, parent = parent, partitionSet),
                context.self,
                partitionSet
              )

              fromMap(newFileList, debugMode)

            } else {
              val newFileList = mergeFileList(fileList, originPointer, versionVector)
              if (debugMode) {
                context.log.info(s"[FileDuplicate] site $siteName has version vector $versionVector. fileList $newFileList.")
              }
              fromMap(newFileList, debugMode)
            }

          } else {
            val newFileList = mergeFileList(fileList, originPointer, versionVector)
            if (debugMode) {
              context.log.info(s"[FileDuplicate] originPointer = $originPointer already exists in fileList = $newFileList. VersionVec $versionVector. Site $siteName")
            }
            fromMap(newFileList, debugMode)
          }

        case FileUpdatedConfirm(originPointer, newVersion, fromSite) =>
          val siteThatUpdatedVersion = fromSite.path.name
          if (fileList.contains(originPointer) && fileList(originPointer).contains(siteThatUpdatedVersion)) {
            val newFileList = updateFileList(fileList, originPointer, siteThatUpdatedVersion, newVersion)

            if (debugMode) {
              context.log.info(s"[FileUpdatedConfirm] originPointer = $originPointer, version = $newVersion, newFileList = $newFileList. Site ${context.self.path.name}")
            }

            fromMap(newFileList, debugMode)
          } else {
            if (debugMode) {
              context.log.error(s"originPointer = $originPointer not in fileList = $fileList. newVersion $newVersion. Site ${context.self.path.name}")
            }

            Behaviors.unhandled
          }

        case Merged(to, parent, partitionSet, writerIcd) =>
          if (debugMode) {
            context.log.info(s"[Merged] sending fileList of site ${context.self.path.name} to site ${to.path.name}. FileList sent: $fileList")
          }
          to ! CheckInconsistency(fileList, parent, partitionSet, writerIcd)
          fromMap(fileList, debugMode)

        case CheckInconsistency(fromFileList, parent, partitionSet, writerIcd) =>
          val newFileList = inconsistencyDetection(context.log, fileList, fromFileList, partitionSet, debugMode, writerIcd)
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

        case ReplaceFileList(newFileList) =>
          if (debugMode) {
            context.log.info(s"[ReplaceFileList.${context.self.path.name}] Replaced FileList with newFileList $newFileList")
          }
          fromMap(newFileList, debugMode)

      }
  }

  /**
   * Merge the received filelist into own fileList in order to duplicate the file as well as the corresponding version vector
   * the original site
   * @param fileList The own file list
   * @param originPointer The origin pointer of the file that is duplicated
   * @param versionVector The version vector of the file that is duplicated
   * @return the merged file list
   */
  private def mergeFileList(
                             fileList: Map[(String, String), Map[String, Int]],
                             originPointer: (String,String),
                             versionVector:Map[String, Int]
                           ): Map[(String, String), Map[String, Int]] =
  {
    if(!fileList.contains(originPointer)) {
      val newFileList = fileList + (originPointer -> versionVector)
      newFileList
    }
    else { // or if fileList already has the originPointer
      var oldVersionVector = fileList(originPointer)
      for((siteName,siteVersion) <- versionVector){
        if(!oldVersionVector.contains(siteName) || oldVersionVector(siteName)<siteVersion){
          oldVersionVector += (siteName->siteVersion)
        }
      }
      val newFileList = fileList + (originPointer -> oldVersionVector)
      newFileList
    }
  }

  /**
   *
   * @param fileList The own file list
   * @param originPointer The origin pointer of the file to be updated
   * @param siteName The name of the site where the file is originally updated
   * @param newVal The new version number
   * @return The updated file list
   */
  private def updateFileList(
                      fileList: Map[(String, String), Map[String, Int]],
                      originPointer: (String, String),
                      siteName: String,
                      newVal: Int
                    ): Map[(String, String), Map[String, Int]] = {
    fileList.updatedWith(key = originPointer) {
      case Some(map) =>
        val newMap = map.updatedWith(key = siteName) {
          case Some(_) =>
            Some(newVal)

          case None =>
            Some(-1)
        }
        Some(newMap)
      case None =>
        Some(Map("" -> -1))
    }
  }

  /**
   * Helper method for merging two file lists. It also keeps track of how many detection have been detected and
   * writes it to a file.
   * @param log Logger for printing out information to the terminal.
   * @param fileListP1 File list of Partition 1.
   * @param fileListP2 File List of Partition 2.
   * @return Merged file List.
   */
  private def inconsistencyDetection(
                                      log: Logger,
                                      fileListP1: Map[(String, String), Map[String, Int]],
                                      fileListP2: Map[(String, String), Map[String, Int]],
                                      partitionSet: Set[ActorRef[SiteProtocol]],
                                      debugMode: Boolean,
                                      writerIcd: Option[PrintWriter]
                                    ): Map[(String, String), Map[String, Int]] = {
    var counter: Int = 0

    // Zip on the same origin pointers
    val zippedLists = for {
      (op1, vv1) <- fileListP1
      (op2, vv2) <- fileListP2
      if op1 == op2
    } yield (op1, vv1, vv2)

    // To keep the unique origin pointers in both the first and second partition.
    val uniqueFilesP1 = fileListP1.filter(f => !fileListP2.contains(f._1))
    val uniqueFilesP2 = fileListP2.filter(f => !fileListP1.contains(f._1))

    // Empty filelist for results
    var fileList = Map[(String, String), Map[String, Int]]()

    for ((originPointer, vv1, vv2) <- zippedLists) {
      // Zip on same siteNames
      val zipVV = for {
        (siteName1, version1) <- vv1
        (siteName2, version2) <- vv2
        if siteName1 == siteName2
      } yield (siteName1, version1, version2)

      // To keep the unique siteNames in version vector.
      val uniqueVV1 = vv1.filter(vv => !vv2.contains(vv._1))
      val uniqueVV2 = vv2.filter(vv => !vv1.contains(vv._1))

      var versionVector = Map[String, Int]()

      // Keep track on the differences with regards to the version vector for each partition respective.
      var count1 = 0
      var count2 = 0

      for ((siteName, version1, version2) <- zipVV) {
        if (version1 > version2) {
          count1 += 1
          versionVector = versionVector + (siteName -> version1)
        } else if (version1 < version2) {
          count2 += 1
          versionVector = versionVector + (siteName -> version2)
        } else {
          versionVector = versionVector + (siteName -> version1) // doesn't matter which version we take.
        }
      }

      versionVector = versionVector ++ uniqueVV1 ++ uniqueVV2

      // Check whether one of the version vectors is dominant over the other or if both contain conflicting updated site versions.
      if (count1 != 0 && count2 == 0 || count2 != 0 && count1 == 0) {
        log.info(s"[Inconsistency Detected] For File $originPointer -> Compatible version conflict detected: $vv1 - $vv2")
        counter += 1
      } else if (count1 != 0 && count2 != 0) {
        log.info(s"[Inconsistency Detected] For File $originPointer -> Incompatible version conflict detected: $vv1 - $vv2")
        counter += 1
      } else {
        log.info(s"[Consistency Detected] For File $originPointer -> no version conflict detected: $vv1 - $vv2")
      }
      fileList = fileList + (originPointer -> versionVector)
    }

    fileList = fileList ++ uniqueFilesP1 ++ uniqueFilesP2

    var finalFileList = Map[(String, String), Map[String, Int]]()

    // Final loop to add every site of the partition to every version vector.
    for ((originPointer, versionVector) <- fileList) {
      var newVersionVector = versionVector
      for (site <- partitionSet) {
        val siteName = site.path.name
        if (!versionVector.contains(siteName)) {
          newVersionVector = newVersionVector + (siteName -> 0)
        }
      }
      finalFileList = finalFileList + (originPointer -> newVersionVector)
    }

    if (debugMode) {
      log.info(s"[LOGGER ID] $finalFileList. FL1 $fileListP1  FL2 $fileListP2 PartitionSet ${partitionSet.map(_.path.name)}")
    }

    writerIcd match {
      case Some(pw) =>
        pw.println(counter.toString)
      case None =>
    }

    finalFileList
  }


}
