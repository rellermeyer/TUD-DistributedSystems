package org.tudelft.crdtgraph

import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.model._
import org.tudelft.crdtgraph.WebServer.{OperationLogFormat}

import scala.collection.mutable.ArrayBuffer
import akka.actor.ActorSystem
import akka.http.scaladsl.Http

import scala.util.{Failure, Success}
import akka.stream.ActorMaterializer

object Synchronizer {
//  implicit val system = ActorSystem()
//  import system.dispatcher

  var hard_coded_targets = ArrayBuffer[String]()
  var sleepTime = 10000
  var maxFailCount = 100

  def addMissingCounter(counters: ArrayBuffer[Int], targets: ArrayBuffer[String]): Unit = {
    if(targets.length > counters.length) {
      var diff = targets.length - counters.length
      for(i <- 1 to diff) {
        counters += 0
      }
    }
  }

  def synchronize(targets: ArrayBuffer[String], mainSystem: ActorSystem, mainMaterializer: ActorMaterializer) = {
    import mainSystem.dispatcher
    implicit var system = mainSystem
    new Thread(new Runnable {
      def run: Unit = {
        var counters = ArrayBuffer[Int]()
        var failCount = ArrayBuffer[Int]()
        while(true) {
          addMissingCounter(counters, targets)
          addMissingCounter(failCount, targets)

          println("Begin synchronization")
          // Send updates to all targets. Decide on what framework to use
          for(i <- targets.indices) {
            var data = DataStore.getLastChanges(counters(i))
            println("Synchronizing with: " + targets(i))
            if(data.nonEmpty) {
              println("Exchanging "+ data.length.toString + "operations")
              var json = data.map(log => log.toJson(OperationLogFormat)).toVector.toJson(spray.json.DefaultJsonProtocol.vectorFormat)
              val responseFuture = Http().singleRequest(
                HttpRequest(
                  method = HttpMethods.POST,
                  uri = targets(i) + "/applychanges",
                  entity = HttpEntity(
                    ContentTypes.`application/json`, json.toString()
                  )
                )
              )

              responseFuture
                .onComplete {
                  case Success(res) => counters(i) += data.length
                  case Failure(_)   => failCount(i) += 1

                }
              Thread.sleep(10)
            } else {
              println("No new changes")
            }
          }

          for(i <- targets.indices) {
            if(failCount(i) > maxFailCount) {
              println("Target " + targets(i) + " has failed " + failCount(i) + " times to respond. Consider removing this target")
              // Might add code to drop the target
            }
          }

          // Make the thread sleep for 10 seconds
          Thread.sleep(sleepTime)
        }
      }
    }).start
  }
}