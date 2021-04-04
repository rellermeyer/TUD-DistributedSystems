package nl.tudelft.IN4391G4.extensions

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.Config

import scala.concurrent.duration.MILLISECONDS
import scala.concurrent.{Await, ExecutionContext, duration}
import scala.util.{Failure, Success}

object ActorExtensions {

  def getActorRefFromConfig(system: ActorSystem, config: Config, rootKey :String) : Option[ActorRef] = {
    var path: String = null
    try {
      val ip = config.getString(s"$rootKey.ip")
      val port = config.getInt(s"$rootKey.port")
      val poolname = config.getString(s"$rootKey.poolname")
      val machinename = config.getString(s"$rootKey.machinename")
      path = s"akka.tcp://$poolname@$ip:$port/user/$machinename"
      val selection = system.actorSelection(path)
      val timeout = new duration.FiniteDuration(5000, MILLISECONDS)
      val future = selection.resolveOne(timeout)
      Some(Await.result(future, timeout))
    }
    catch {
      case e: Exception => {
        println(e+s" occurred while looking for actor ${path}")
        None
      }
    }

  }

  def getActorRefFromConfig(system: ActorSystem, config: Config, rootKey :String, fn: ActorRef => Unit)(implicit context: ExecutionContext) : Unit = {
    var path: String = null
    try {
      val ip = config.getString(s"$rootKey.ip")
      val port = config.getInt(s"$rootKey.port")
      val poolname = config.getString(s"$rootKey.poolname")
      val machinename = config.getString(s"$rootKey.machinename")
      path = s"akka.tcp://$poolname@$ip:$port/user/$machinename"
      val selection = system.actorSelection(path)
      val timeout = new duration.FiniteDuration(5000, MILLISECONDS)
      val future = selection.resolveOne(timeout)
      future.onComplete {
        case Success(result)  => fn(result)
        case Failure(failure) => println(failure+s" occurred while looking for actor ${path}")
      }
    }
    catch {
      case e: Exception => {
        println(e+s" occurred while looking for actor ${path}")
        //None
      }
    }
  }

}