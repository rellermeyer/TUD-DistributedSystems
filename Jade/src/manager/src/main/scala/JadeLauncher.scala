import java.net.InetAddress

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.circe._
import io.circe.generic.auto._

import scala.collection.mutable
import scala.io.Source

/**
  * The launcher for the manager part of JADE.
  */
object JadeLauncher extends App {
    val localhost: InetAddress = InetAddress.getLocalHost
    var localIpAddress: String = localhost.getHostAddress
    var ipList = Array(localIpAddress)
    var managerIpList: Array[String] = Array.empty

    var system: ActorSystem = if (!args.isEmpty) {
        localIpAddress = args.head
        val config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + localIpAddress)
            .withFallback(ConfigFactory.load("application"))
        ActorSystem("Manager", config)
    } else {
        ActorSystem("Manager")
    }

    val configFilename = "default.json"
    val configFile = Source.fromResource(configFilename)

    val json: Either[ParsingFailure, Json] = parser.parse(configFile.getLines.mkString)
    val config = json match {
            case Right(x) => x.as[JadeManager.ManagerConfig] match {
                    case Right(x) => x
                    case Left(_) => JadeManager.ManagerConfig(List())
                }
            case Left(_) => JadeManager.ManagerConfig(List())
        }

    println("Jade launcher started")

    if (!args.drop(1).isEmpty) {
        val ipCount: Int = args.drop(1).head.toInt
        println(s"Found $ipCount Node IPs")
        ipList = args.drop(1).tail.slice(0, ipCount)
        managerIpList = args.drop(1).tail.slice(ipCount, args.tail.length)
    }

    println("Loading the following ip addresses:")
    ipList.foreach {
        println
    }

    if(managerIpList.isEmpty) {
        println("Running without manager replicas")
        JadeManager.start(ipList.toList, config)
    } else {
        println("Connecting to manager replicas on the following ip addresses:")
        var managerIpMap: mutable.Map[Int, String] = mutable.Map.empty
        var i = 0
        var id = 0
        for(ip <- managerIpList) {
            println(ip)
            if(ip.equals(localIpAddress)) {
                id = i
            } else {
                managerIpMap += (i -> ip)
            }
            i += 1
        }

        println("Connecting to peer replicas...")
        JadeManager.connectToReplicas(id, managerIpMap.toMap, () => {
            println("Connected")
            JadeManager.start(ipList.toList, config)
        })
    }
}
