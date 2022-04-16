package archipelago

import akka.actor.{ActorRef, ActorSystem, Props}

import java.io.{BufferedWriter, File, FileWriter}
import scala.sys.exit


object Main{
  val K = 1 //the max number of disabled processes per-round
  val rounds = 20 //no. of rounds
  val adversaryMode = false //whether the adversary selection strategy is to be used
  //TODO: Currently adversaryMode only works for 2 processors
  val N = 4 //the number of processors
  val actorSystem = ActorSystem("ActorSystem")
  val m = actorSystem.actorOf(Props[max_register])
  val puppeteer = actorSystem.actorOf(Props[Puppeteer])
  val C = new Array[ActorRef](rounds) //array of adopt commit max objects
  val nodes = new Array[ActorRef](N)
  var trialsCompleted = 0
  var sampleTrials = 1
  val resetNode = actorSystem.actorOf(Props[resetter])
  for(i <- 0 until rounds){
    C(i) = actorSystem.actorOf(Props[adopt_commit_max])
  }
  for(i <- 0 until N){
    nodes(i) = actorSystem.actorOf(Props[Processor],i.toString)
  }
  def main(args:Array[String]){
    puppeteer ! "beginSimulation"
  }
  def writeData(success:Int,roundCount:Int): Unit ={
    if(sampleTrials != 0) {
      val bw = new BufferedWriter(new FileWriter(new File("file.txt"), true))
      val stringToWrite = success.toString + " " + roundCount.toString + "\n"
      bw.write(stringToWrite)
      bw.close
    }
    trialsCompleted += 1
    if(trialsCompleted < sampleTrials){
      puppeteer ! "reset"
      m ! "reset"
      for(i <- 0 until rounds){
        C(i) ! "reset"
      }
      for(i <- 0 until N){
        nodes(i) ! "reset"
      }
    }
    else{
      exit(0)
    }
  }
  def new_round(): Unit= {
    println("starting new round")
    puppeteer ! "beginSimulation"
  }
}