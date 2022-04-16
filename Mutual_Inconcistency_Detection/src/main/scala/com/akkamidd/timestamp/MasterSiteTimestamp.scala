package com.akkamidd.timestamp
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.timestamp.SiteTimestamp.{Merged, TimestampProtocol}

import java.io.PrintWriter


// the master actor who spawn the sites
object MasterSiteTimestamp {

  // MasterTimestampProtocol - Defines the messages that dictates the protocol of the master site of the timestamp algorithm.
  sealed trait MasterTimestampProtocol

  final case class Broadcast(
                              msg: TimestampProtocol,
                              from: ActorRef[TimestampProtocol],
                              partitionSet: Set[ActorRef[TimestampProtocol]]
                            ) extends MasterTimestampProtocol
  final case class FileUploadMasterSite(
                                         to: String,
                                         fileName: String,
                                         timestamp: String,
                                         partitionList: List[Set[String]]
                                       ) extends MasterTimestampProtocol
  final case class FileUpdateMasterSite(
                                         to: String,
                                         fileName: String,
                                         partitionList: List[Set[String]]
                                       ) extends MasterTimestampProtocol
  final case class Merge(
                          fromSiteMerge: String,
                          toSiteMerge: String,
                          partitionList: List[Set[String]],
                          writerIcd: Option[PrintWriter]
                        ) extends MasterTimestampProtocol
  final case class SpawnSite(siteName: String) extends MasterTimestampProtocol

  def apply(debugMode: Boolean): Behavior[MasterTimestampProtocol] = Behaviors.setup {
    context => masterSiteReceive(context, List(), debugMode)
  }

  def findSiteGivenName(
                         siteName: String,
                         children: List[ActorRef[TimestampProtocol]]
                       ): Option[ActorRef[TimestampProtocol]] =
  {
    for (child <- children) {
      if (child.path.name.equals(siteName)) {
        return Some(child)
      }
    }
    None
  }

  def getPartitionActorRefSet(
                               children: List[ActorRef[TimestampProtocol]],
                               partitionSetString: Set[String]
                             ): Set[ActorRef[TimestampProtocol]] =
  {
    partitionSetString.map(s => {
      findSiteGivenName(s, children).get
    })
  }

  // given a site "from", find a partition that the site is currently in
  def findPartitionSet(
                        fromSite: String,
                        sitesPartitionedList: List[Set[String]]
                      ): Set[String] =
  {
    for (set <- sitesPartitionedList) {
      if (set.contains(fromSite)) {
        return set
      }
    }
    // if the site is not found in partitionList , return a empty set
    Set[String]()
  }

  def masterSiteReceive(
                         context: ActorContext[MasterTimestampProtocol],
                         children: List[ActorRef[TimestampProtocol]],
                         debugMode: Boolean
                       )
  : Behaviors.Receive[MasterTimestampProtocol] = Behaviors.receiveMessage {



    case Broadcast(msg: TimestampProtocol, from: ActorRef[TimestampProtocol], partitionSet: Set[ActorRef[TimestampProtocol]]) =>
      partitionSet.foreach { child =>
        if(!child.equals(from)) {
          child ! msg
          if (debugMode) {
            context.log.info("from {} , send message to {}", from, child.toString)
          }
        }
      }
      masterSiteReceive(context, children, debugMode)



    case FileUploadMasterSite(siteThatUploads: String, timestamp: String, fileName: String, partitionList: List[Set[String]]) =>
      val site = findSiteGivenName(siteThatUploads, children).get

      val getPartitionSet = findPartitionSet(siteThatUploads, partitionList)
      val partitionSetRefs = getPartitionActorRefSet(children, getPartitionSet)

      site ! SiteTimestamp.FileUpload(fileName, timestamp, context.self, partitionSetRefs)

      masterSiteReceive(context, children, debugMode)



    case FileUpdateMasterSite(siteThatUpdates: String, fileName: String, partitionList: List[Set[String]]) =>
      val site = findSiteGivenName(siteThatUpdates, children).get

      val getPartitionSet = findPartitionSet(siteThatUpdates, partitionList)
      val partitionSetRefs = getPartitionActorRefSet(children, getPartitionSet)

      site ! SiteTimestamp.FileUpdate(fileName, context.self, partitionSetRefs)

      masterSiteReceive(context, children, debugMode)

    case Merge(fromSiteMerge, toSiteMerge, partitionList, writerIcd) =>
      val siteFrom = findSiteGivenName(fromSiteMerge, children).get
      val siteTo = findSiteGivenName(toSiteMerge, children).get

      val partitionSet = findPartitionSet(fromSiteMerge, partitionList)
      val partitionSetRefs = getPartitionActorRefSet(children, partitionSet)

      siteFrom ! Merged(siteTo, context.self, partitionSetRefs, writerIcd)

      masterSiteReceive(context, children, debugMode)

    // create/spawn sites
    case SpawnSite(siteName: String) =>
      val spawnedSite = context.spawn(SiteTimestamp(debugMode), siteName)
      val newChildren = spawnedSite +: children

      if (debugMode) {
        context.log.info(s"$newChildren")
      }

      masterSiteReceive(context, newChildren, debugMode)
  }

}
