package CRUSH.controller

import CRUSH.utils.crushmap.{ CrushMap, PlacementRule }
import better.files._
import com.typesafe.config.{ Config, ConfigFactory }
import io.methvin.better.files._

import java.util.concurrent._
import java.util.{ Timer, TimerTask }
import scala.concurrent.ExecutionContext.Implicits.global

object RootController {
  def readMap(): CrushMap = {
    val conf: Config = ConfigFactory.load()
    val crushConfig  = conf.getObject("crushmap.root")
    Rootstore.maxTimeOut = conf.getInt("crushmap.timeout")
    val buckets = Parser.parse(crushConfig)

    CrushMap(Some(buckets), Nil)
  }
  def readRules(): PlacementRule = {
    val conf: Config = ConfigFactory.load()
    val ruleConfig   = conf.getObject("rules")
    Parser.parseRule(ruleConfig)
  }
  def filterMap(): Unit = {
    Rootstore.purgeDeadNodes()
    Rootstore.filterMap()
  }

}

object Controller extends App {

  val timer = new Timer

  def delay(f: () => Unit, n: Long): Unit = timer.schedule(new TimerTask() { def run(): Unit = f() }, n)

  // parse the config
  Rootstore.configMap = RootController.readMap()
  Rootstore.placementRule = RootController.readRules()
  println(Rootstore.configMap)
  println(Rootstore.placementRule)
  // start file watcher
  val myDir = File(getClass.getResource("/application.conf"))
  val watcher = new RecursiveFileMonitor(myDir) {
    override def onCreate(file: File, count: Int): Unit = println(s"$file got created")
    override def onModify(file: File, count: Int): Unit = {
      println(s"$file got modified $count times")
      Rootstore.configMap = RootController.readMap()
      Rootstore.placementRule = RootController.readRules()
    }
    override def onDelete(file: File, count: Int): Unit = println(s"$file got deleted")
  }
  watcher.start()

  // start watchdog interval to loop over activeNodes and filter the crushmap
  val ex = new ScheduledThreadPoolExecutor(1)
  val task = new Runnable {
    def run(): Unit = RootController.filterMap()
  }
  val f = ex.scheduleAtFixedRate(task, 1, 1, TimeUnit.SECONDS)

  // init akka

  // start akka + handling requests
}
