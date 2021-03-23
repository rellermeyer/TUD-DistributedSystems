package org.tudelft.crdtgraph

import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.model._
import org.tudelft.crdtgraph.WebServer.OperationLogFormat

import scala.collection.mutable.{HashMap, ArrayBuffer}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.util.{Failure, Success}
import akka.stream.ActorMaterializer


object Synchronizer {
  var sleepTime = 10000
  var maxFailCount = 20
  var instancesCheckTime = 5

  private var targets = ArrayBuffer[String]()
  private var counters = HashMap[String, Int]()
  private var failCounts = HashMap[String, Int]()

  def synchronize(mainSystem: ActorSystem, mainMaterializer: ActorMaterializer) = {
    import mainSystem.dispatcher
    implicit var system = mainSystem
    checkForNewInstances(mainSystem)
    addMissingCounters()

    new Thread(new Runnable {
      def run: Unit = {
        var instancesCheckCounter = 0
        while (true) {
          if (instancesCheckCounter >= instancesCheckTime) {
            checkForNewInstances(mainSystem)
            addMissingCounters()
            removeFailingInstances()
            instancesCheckCounter = 0
            println("Performed instances check.")
            println("New peers are: " + targets)
          }

          // Send updates to all targets. Decide on what framework to use
          targets.foreach(target => {

            var data = DataStore.getLastChanges(counters(target))
            println("Synchronizing with: " + target)

            if (data.nonEmpty) {
              println("Exchanging " + data.length.toString + " operations.")
              var json = data.map(log => log.toJson(OperationLogFormat)).toVector.toJson(spray.json.DefaultJsonProtocol.vectorFormat)
              val responseFuture = Http().singleRequest(
                HttpRequest(
                  method = HttpMethods.POST,
                  uri = target + "/applychanges",
                  entity = HttpEntity(
                    ContentTypes.`application/json`, json.toString()
                  )
                )
              )

              responseFuture
                .onComplete {
                  case Success(res) => {
                    counters(target) += data.length
                    failCounts(target) = 0
                  }
                  case Failure(_) => failCounts(target) += 1
                }

              //Release IO between sync calls
              Thread.sleep(10)
            } else {
              println("No new changes")
            }
          })

          // Increment and sleep for n seconds (default: 10)
          instancesCheckCounter += 1
          Thread.sleep(sleepTime)
        }
      }
    }).start
  }

  private def addMissingCounters(): Unit = {
    targets.foreach(target => {
      if (!counters.contains(target)) {
        counters(target) = 0
      }
    })
    targets.foreach(target => {
      if (!failCounts.contains(target)) {
        failCounts(target) = 0
      }
    })
  }

  private def removeFailingInstances() = {
    var toRemove = ArrayBuffer[String]()
    targets.foreach(target => {
      if (failCounts(target) > maxFailCount) {
        println("Target " + target + " has failed " + failCounts(target) + " times to respond.")
        println("Target " + target + " is removed from synchronization list.")
        failCounts.remove(target)
        counters.remove(target)
        toRemove += target
      }
    })
    targets --= toRemove
  }

  private def checkForNewInstances(mainSystem: ActorSystem) = {
    val newAddresses = ClusterListener.getBroadcastAddresses(mainSystem)
    newAddresses.foreach(address => {
      if (!targets.contains(address)) {
        targets += address
      }
    })
  }

}