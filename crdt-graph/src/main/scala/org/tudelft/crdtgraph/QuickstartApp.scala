package org.tudelft.crdtgraph

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.tudelft.crdtgraph.DataStore
import spray.json.DefaultJsonProtocol._

import DataStore._
import spray.json._


import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.Seq
import scala.io.StdIn
import scala.concurrent.Future

import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives


final case class VertexCaseClass(vertexName: String)
final case class ArcCaseClass(sourceVertex: String, targetVertex: String)
final case class OperationLogsCaseClass(messages: List[String])


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val vertexFormat = jsonFormat1(VertexCaseClass)
  implicit val arcFormat = jsonFormat2(ArcCaseClass)
  implicit val orderFormat = jsonFormat1(OperationLogsCaseClass)
}

object WebServer extends Directives with JsonSupport {

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher



  def main(args: Array[String]) {

    val route: Route =
      get {
        pathPrefix("graph" / "vertex" / """.+""".r) { id =>
          var vertex = id.asInstanceOf[String]
          var result = DataStore.lookUpVertex(vertex)
          if(result){
            complete(StatusCodes.OK)
          } else {
            complete(StatusCodes.NotFound)
          }
        }
      } ~
        post {
          pathPrefix("addvertex") {
            entity(as[VertexCaseClass]) { vertex =>
              if (!DataStore.lookUpVertex(vertex.vertexName)){
                if (DataStore.addVertex(vertex.vertexName)) {
                  complete("Added vertex " + vertex.vertexName)
                } else {
                  complete("Could not add vertex " + vertex.vertexName)
                }
              } else {
                complete("There is already a vertex called " + vertex.vertexName)
              }
            }
          }
        } ~
        post {
          pathPrefix("addarc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex

              if(!DataStore.lookUpArc(src, dst)){
                if(!DataStore.lookUpVertex(src)){
                  complete("Vertex " + src + " does not exist, cannot perform addArc")

                } else if(!DataStore.lookUpVertex(dst)){
                  complete("Vertex " + dst + " does not exist, cannot perform addArc")

                } else{
                  if (DataStore.addArc(src, dst)) {
                    complete("Added arc between " + src + " and " + dst)
                  } else {
                    complete("Could not add arc between " + src + " and " + dst)
                  }
                }
              } else {
                complete("There is already an arc with this src and dst")
              }
            }
          }
        } ~
        delete {
          pathPrefix("removevertex") {
            entity(as[VertexCaseClass]) { vertex =>
              val vertexName = vertex.vertexName
              if(DataStore.lookUpVertex(vertexName)){
                if (DataStore.removeVertex(vertexName)) {
                  complete("Removed vertex " + vertexName +  " succesfully")
                } else {
                  complete("Could not remove vertex " + vertexName)
                }
              } else {
                complete("No vertex called " + vertexName)
              }
            }
          }
        } ~
        delete {
          //gives nullpointer
          pathPrefix("removearc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex
              if(DataStore.lookUpArc(src, dst)){
                if(DataStore.removeArc(src, dst)) {
                  complete("Removed arc between " + src + " and " + dst + " succesfully")
                } else {
                  complete("Could not remove arc")
                }
              } else {
                complete("No arc called between " + src + " and " + dst)
              }
            }
          }
        } ~
        post {
          pathPrefix("applychanges") {
            entity(as[OperationLogsCaseClass]) { oplog =>
              println(oplog)
              complete(oplog)
            }
          }
        } ~
        get {
          pathPrefix("lookupvertex") {
            parameter("vertexName") { vertexName =>
              if(DataStore.lookUpVertex(vertexName)){
                complete("Vertex " + vertexName + " exists")
              } else {
                complete("Vertex " + vertexName + " does not exist")
              }
            }
          }
        } ~
        get {
          pathPrefix("lookuparc") {
            parameter("sourceVertex", "targetVertex") { (sourceVertex, targetVertex) =>
              if(DataStore.lookUpArc(sourceVertex, targetVertex)){
                complete("Arc between " + sourceVertex + " and " + targetVertex + " exists")
              } else {
                complete("No arc between " + sourceVertex + " and " + targetVertex)
              }
            }
          }
        }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done

  }
}