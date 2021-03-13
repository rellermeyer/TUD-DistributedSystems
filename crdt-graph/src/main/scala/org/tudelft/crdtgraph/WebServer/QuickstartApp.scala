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
import org.tudelft.crdtgraph.DataStore
import scala.collection.mutable.ArrayBuffer


import org.tudelft.crdtgraph.DataStore
import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable.ArrayBuffer
import scala.io.StdIn
import scala.concurrent.Future

object WebServer {

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
        get {
          pathPrefix("addvertex" / """\w+""".r) { id =>
            if (!DataStore.lookUpVertex(id)){
              val returnval = DataStore.addVertex(id)
              complete("Called addVertex on " + id + ", returnval is " + returnval)
            } else {
              complete("There is already a vertex called " + id)
            }
          }
        } ~
        get {
          pathPrefix("addarc" / """[a-zA-Z0-9\&\-\.']*""".r ) { id  =>
            val split = id.split("-")
            val src = split(0)
            val dst = split(1)
            if(!DataStore.lookUpArc(src, dst)){
              if(!DataStore.lookUpVertex(src)){
                complete("Vertex " + src + " does not exist, cannot perform addArc")

              } else if(!DataStore.lookUpVertex(dst)){
                complete("Vertex " + dst + " does not exist, cannot perform addArc")

              } else{
                val returnval = DataStore.addArc(src, dst)
                complete("called addArc with " + src + " and " + dst + " with returnval " + returnval)
              }

            } else {
              complete("There is already an arc with this src and dst")
            }
          }
        } ~
        get {
          pathPrefix("removevertex" / """\w+""".r) { id =>
            // there might be no item for a given id
            if(DataStore.lookUpVertex(id)){
              val returnval = DataStore.removeVertex(id)
              complete("called removeVertex on " + id + ", returnval is " + returnval)
            } else {
              complete("No vertex called " + id)
            }
          }
        } ~
        get {
          //gives nullpointer
          pathPrefix("removearc" / """[a-zA-Z0-9\&\-\.']*""".r) { id =>
            val split = id.split("-")
            val src = split(0)
            val dst = split(1)
            if(DataStore.lookUpArc(src, dst)){
              val returnval = DataStore.removeArc(src, dst)
              //complete("called removeArc on " + src + " and " + dst + ", returnval is " + returnval)
              complete("test")
            } else {
              complete("No arc called " + id)
            }
          }
        } ~
        get {
          //ask west how to create operationlogs
          pathPrefix("applychanges" / """[a-zA-Z0-9\&\-\.']*""".r) { id =>
            var oplogs = id.split("-")
            var operationLogs = ArrayBuffer[OperationLog]()
            for (oplog <- oplogs){
              var optype = oplog.split(",")(0)
              var opUUID = oplog.split(",")(1)
            }
            //val returnval = DataStore.applyChanges(operationLogs)
            //complete("Applied changes with returnval " + returnval)
            complete("test")

          }
        } ~
        get {
          pathPrefix("lookupvertex" / """\w+""".r) { id =>
            val returnval = DataStore.lookUpVertex(id)
            complete("called lookUpVertex on  " + id + " return is: " + returnval)
          }
        } ~
        get {
          pathPrefix("lookuparc" / """[a-zA-Z0-9\&\-\.']*""".r) { id =>
            val split = id.split("-")
            val src = split(0)
            val dst = split(1)
            val returnval = DataStore.lookUpArc(src, dst)
            complete("called lookUpArc on  " + src + " and " + dst + " return is: " + returnval)
          }
        }~
        get {
          pathPrefix("synchronize") {
            pathEnd{
              DataStore.synchronize(ArrayBuffer[Int]())
              complete("called synchronizer")
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