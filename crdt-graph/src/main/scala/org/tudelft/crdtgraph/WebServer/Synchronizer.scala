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

  def synchronize(targets: ArrayBuffer[String]) = {
    new Thread(new Runnable {
      def run: Unit = {
        var counter = 0
        while(counter >= 0) {
          // Updates that will have to be sent
          var updates = ArrayBuffer[OperationLog]()
          // Amount of updates. Will be used to extract updates from ChangesQueue
          var amountOfUpdates = 0
          print("Test")
          // Initialize amountOfUpdates so it can be used by a loop
          if(ChangesQueue.length - (counter + 1) > 0) {
            amountOfUpdates = ChangesQueue.length - (counter + 1)
          }

          // Extract updates from ChangesQueue
          for(i <- 1 to amountOfUpdates) {
            updates += ChangesQueue(counter)
            counter += 1
          }

          //convert updates to JSON

          // Send updates to all targets. Decide on what framework to use
          for(x <- targets) {
            Http().singleRequest(Post(x, "data"))
          }

          // Make the thread sleep for 10 seconds
          Thread.sleep(10000)
        }
      }
    }).start
  }
}