package org.tudelft.crdtgraph

import org.tudelft.crdtgraph.DataStore._
import org.tudelft.crdtgraph.OperationLogs._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import org.tudelft.crdtgraph.DataStore
import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable.ArrayBuffer
import scala.io.StdIn
import scala.concurrent.Future
import akka.http.scaladsl.client.RequestBuilding.Post
import scala.concurrent.ExecutionContext.Implicits.global


object Synchronizer {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  var hard_coded_targets = ArrayBuffer[String]()
  var sleepTime = 10000
  var timeout = 10000
  var maxFailCount = 120

  def configureCounters(counters: ArrayBuffer[Int], targets: ArrayBuffer[String]): Unit = {
    if(targets.length > counters.length) {
      var diff = targets.length - counters.length
      for(i <- 1 to diff) {
        counters += 0
      }
    }
  }

  def synchronize(targets: ArrayBuffer[String], changesQueue: ArrayBuffer[String]) = {
    new Thread(new Runnable {
      def run: Unit = {
        var counters = ArrayBuffer[Int]()
        while(true) {
          configureCounters(counters, targets)

          // Updates that will have to be sent
          var updates = ArrayBuffer[ArrayBuffer[String]]()

          for(x <- targets) {
            updates += ArrayBuffer[String]()
          }

          // Amount of updates. Will be used to extract updates from ChangesQueue
          var amountOfUpdates = ArrayBuffer[Int]()

          print("Test")

          // Initialize amountOfUpdates so it can be used by a loop
          for(i <- 0 to targets.length - 1) {
            amountOfUpdates += changesQueue.length - counters(i)
          }
          // Extract updates from ChangesQueue
          for(i <- 0 to targets.length - 1) {
            for(x <- 1 to amountOfUpdates(i)) {
              if(amountOfUpdates(i) > 0) {
                updates(i) += changesQueue(counters(i)) //maybe create temporary counter
                counters(i) += 1 //CHANGE
              }
            }
          }
          print("crayzy")

          //convert updates to JSON

          // Send updates to all targets. Decide on what framework to use
          for(i <- 0 to targets.length - 1) {
            for(x <- updates(i)) {
              Http().singleRequest(Post(targets(i), x))
              Thread.sleep(10)
            }
          }

          // Make the thread sleep for 10 seconds
          Thread.sleep(10000)
        }
      }
    }).start
  }
}