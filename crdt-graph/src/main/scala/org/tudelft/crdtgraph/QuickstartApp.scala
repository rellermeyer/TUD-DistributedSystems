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
import org.tudelft.crdtgraph.OperationLogs._


final case class VertexCaseClass(vertexName: String)
final case class ArcCaseClass(sourceVertex: String, targetVertex: String)


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val vertexFormat = jsonFormat1(VertexCaseClass)
  implicit val arcFormat = jsonFormat2(ArcCaseClass)
}

object WebServer extends Directives with JsonSupport {
  implicit object OperationLogFormat extends JsonFormat[OperationLog] {
    def write(obj: OperationLog): JsValue = {
      JsObject(
        ("opType", JsString(obj.opType)),
        ("operationUuid", JsString(obj.operationUuid)),
        ("timestamp", JsString(obj.timestamp)),
        ("vertexName", JsString(obj.vertexName)),
        ("vertexUuid", JsString(obj.vertexUuid)),
        ("vertexUuids", JsArray(obj.vertexUuids.toVector.map(x => JsString(x)).toVector)),
        ("sourceVertex", JsString(obj.sourceVertex)),
        ("targetVertex", JsString(obj.targetVertex)),
        ("arcUuid", JsString(obj.arcUuid)),
        ("arcUuids", JsArray(obj.arcUuids.map((id:String) => JsString(id)).toVector))
      )
    }

    def read(json: JsValue): OperationLog = json match {
      case JsObject(fields) => {
        var log = new OperationLog()
        log.opType = fields("opType").convertTo[String]
        log.operationUuid = fields("operationUuid").convertTo[String]
        log.timestamp = fields("timestamp").convertTo[String]
        log.vertexName = fields("vertexName").convertTo[String]
        log.vertexUuid = fields("vertexUuid").convertTo[String]
        log.vertexUuids = fields("vertexUuids").convertTo[Array[String]]
        log.sourceVertex = fields("sourceVertex").convertTo[String]
        log.targetVertex = fields("targetVertex").convertTo[String]
        log.arcUuid = fields("arcUuid").convertTo[String]
        log.arcUuids = fields("arcUuids").convertTo[Array[String]]
        log
      }
      case _ => deserializationError("Json is required")
    }
  }

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  val trueString = "true"
  val falseString = "false"


  def main(args: Array[String]) {
    val route: Route =
        post {
          pathPrefix("addvertex") {
            entity(as[VertexCaseClass]) { vertex =>
              if (DataStore.addVertex(vertex.vertexName)) {
                complete(trueString)
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        post {
          pathPrefix("addarc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex

              if(!DataStore.lookUpVertex(src)) {
                complete(falseString)

              } else if(!DataStore.lookUpVertex(dst)) {
                complete(falseString)

              } else {
                if (DataStore.addArc(src, dst)) {
                  complete(trueString)
                } else {
                  complete(falseString)
                }
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
                  complete(trueString)
                } else {
                  complete(falseString)
                }
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        delete {
          pathPrefix("removearc") {
            entity(as[ArcCaseClass]) { arc =>
              val src = arc.sourceVertex
              val dst = arc.targetVertex
              if(DataStore.lookUpArc(src, dst)){
                if(DataStore.removeArc(src, dst)) {
                  complete(trueString)
                } else {
                  complete(falseString)
                }
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        post {
          pathPrefix("applychanges") {
            entity(as[JsValue]) { oplog =>
              var logs = oplog.convertTo[Vector[OperationLog]]
              if( DataStore.applyChanges(logs)){
                complete(trueString)
              }
              else{
                complete(falseString)
              }
            }
          }
        } ~
        get {
          pathPrefix("lookupvertex") {
            parameter("vertexName") { vertexName =>
              if(DataStore.lookUpVertex(vertexName)){
                complete(trueString)
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        get {
          pathPrefix("lookuparc") {
            parameter("sourceVertex", "targetVertex") { (sourceVertex, targetVertex) =>
              if(DataStore.lookUpArc(sourceVertex, targetVertex)){
                complete(trueString)
              } else {
                complete(falseString)
              }
            }
          }
        } ~
        get {
          pathPrefix("debug-get-changes")  {
            var changes = DataStore.ChangesQueue.toVector
            complete(changes.map( log => log.toJson))
          }
        }

    val port = if (args.length > 0) args(0).toInt else 8080
    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done

  }
}