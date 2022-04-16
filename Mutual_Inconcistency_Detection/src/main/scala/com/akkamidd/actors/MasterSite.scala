package com.akkamidd.actors
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.akkamidd.actors.Site.{Merged, SiteProtocol}

import java.io.PrintWriter

// the master actor who spawn the sites
object MasterSite {

  // MasterSiteProtocol - Defines the messages that dictates the protocol of the master site.
  sealed trait MasterSiteProtocol
  final case class Broadcast(
                              msg: Site.SiteProtocol,
                              from: ActorRef[Site.SiteProtocol],
                              partitionSet: Set[ActorRef[SiteProtocol]]
                            ) extends MasterSiteProtocol
  final case class FileUploadMasterSite(
                                         to: String,
                                         timestamp: String,
                                         fileName: String,
                                         partitionList: List[Set[String]]
                                       ) extends MasterSiteProtocol
  final case class FileUpdateMasterSite(
                                         to: String,
                                         originPointer: (String, String),
                                         partitionList: List[Set[String]]
                                       ) extends MasterSiteProtocol
  final case class Merge(
                          fromSiteMerge: String,
                          toSiteMerge: String,
                          partitionList: List[Set[String]],
                          writerIcd: Option[PrintWriter]
                        ) extends MasterSiteProtocol
  final case class SpawnSite(siteName: String) extends MasterSiteProtocol

  def apply(debugMode: Boolean): Behavior[MasterSiteProtocol] = Behaviors.setup {
    context => masterSiteReceive(context, List(), debugMode)
  }

  /**
   * Given a site name, find the corresponding ActorRef
   * @param siteName The given site name
   * @param children Child (site) list of master site
   * @return The corresponding ActorRef
   */
  def findSiteGivenName(
                         siteName: String,
                         children: List[ActorRef[SiteProtocol]]
                       ): Option[ActorRef[SiteProtocol]] =
  {
    for (child <- children) {
      if (child.path.name.equals(siteName)) {
        return Some(child)
      }
    }
    None
  }

  /**
   * Transform the partition list of site names to partition list of corresponding ActorRefs
   * @param children The child (site) list of master site
   * @param partitionSetString The original partition list which is represented by site names
   * @return The partition list with ActorRefs
   */
  def getPartitionActorRefSet(
                               children: List[ActorRef[SiteProtocol]],
                               partitionSetString: Set[String]
                             ): Set[ActorRef[SiteProtocol]] =
  {
    partitionSetString.map(s => {
      findSiteGivenName(s, children).get
    })
  }


  /**
   * Given a site "from", find a partition that the site is currently in
   * @param fromSite the given site name
   * @param sitesPartitionedList the current partition list
   * @return the partition set that "fromSite" is currently in
   */
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

  /**
   * A state machine where state is reprensented by context and children list. By receiving message it will moved to another state
   * @param context The context of master site
   * @param children The children (site) list of master site
   * @param debugMode Set to true to enable the debugging information in console
   * @return The new state
   */
  def masterSiteReceive(
                         context: ActorContext[MasterSiteProtocol],
                         children: List[ActorRef[SiteProtocol]],
                         debugMode: Boolean
                       )
  : Behaviors.Receive[MasterSiteProtocol] = Behaviors.receiveMessage {

    case Broadcast(msg: SiteProtocol, from: ActorRef[SiteProtocol], partitionSet: Set[ActorRef[SiteProtocol]]) =>
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

      site ! Site.FileUpload(timestamp, context.self, fileName, partitionSetRefs)

      masterSiteReceive(context, children, debugMode)

    case FileUpdateMasterSite(siteThatUpdates: String, originPointer: (String, String), partitionList: List[Set[String]]) =>
      val site = findSiteGivenName(siteThatUpdates, children).get

      val getPartitionSet = findPartitionSet(siteThatUpdates, partitionList)
      val partitionSetRefs = getPartitionActorRefSet(children, getPartitionSet)

      site ! Site.FileUpdate(originPointer, context.self, partitionSetRefs)

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
      val spawnedSite = context.spawn(Site(debugMode), siteName)
      val newChildren = spawnedSite +: children

      if (debugMode) {
        context.log.info(s"$newChildren")
      }

      masterSiteReceive(context, newChildren, debugMode)
  }

}
