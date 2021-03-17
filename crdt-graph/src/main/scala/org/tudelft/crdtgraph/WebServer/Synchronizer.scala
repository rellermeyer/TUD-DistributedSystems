package org.tudelft.crdtgraph

import spray.json._
import DefaultJsonProtocol._
import org.tudelft.crdtgraph.DataStore._
import org.tudelft.crdtgraph.OperationLogs._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._

import org.tudelft.crdtgraph.WebServer.OperationLogFormat

import org.tudelft.crdtgraph.DataStore
import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable.ArrayBuffer
import scala.io.StdIn
import scala.concurrent.Future
import akka.http.scaladsl.client.RequestBuilding.Post
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import DataStore._
import spray.json._

import scala.io.StdIn
import scala.concurrent.Future
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import org.tudelft.crdtgraph.OperationLogs._

import scala.concurrent.Future
import scala.util.{ Failure, Success }


object Synchronizer {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  var hard_coded_targets = ArrayBuffer[String]()
  var sleepTime = 10000
  var maxFailCount = 100

  def configureCounters(counters: ArrayBuffer[Int], targets: ArrayBuffer[String]): Unit = {
    if(targets.length > counters.length) {
      var diff = targets.length - counters.length
      for(i <- 1 to diff) {
        counters += 0
      }
    }
  }

  def synchronize(targets: ArrayBuffer[String]) = {
    new Thread(new Runnable {
      def run: Unit = {
        var counters = ArrayBuffer[Int]()
        var failCount = ArrayBuffer[Int]()
        while(true) {
          configureCounters(counters, targets)
          configureCounters(failCount, targets)

          print("Test")
            
          // Send updates to all targets. Decide on what framework to use
          for(i <- 0 to targets.length - 1) {
            var data = DataStore.ChangesQueue.drop(counters(i))
            var count = data.length
            var json = data.map(log => log.toJson(OperationLogFormat)).toVector.toJson(spray.json.DefaultJsonProtocol.vectorFormat)
            println(targets(i))
            println(counters(i))
            println(json)
            val responseFuture: Future[HttpResponse] = Http().singleRequest(Post(targets(i) + "/applychanges", json.toString()))
            responseFuture
              .onComplete {
                      case Success(res) => if(json != "[]") counters(i) += count else failCount(i) +=1
                      case Failure(_)   => failCount(i) += 1

              }
            Thread.sleep(10)
          }

          for(i <- 0 to targets.length - 1) {
            if(failCount(i) > maxFailCount) {
              print("Target " + targets(i) + " has failed " + failCount(i) + " times to respond. Consider removing this target")
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