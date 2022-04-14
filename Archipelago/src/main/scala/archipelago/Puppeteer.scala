package archipelago

import akka.actor.Actor

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class Puppeteer extends Actor {
  var responses = 0
  var roundCnt = 0
  var procStats = new ArrayBuffer[Int]() //binary array that holds 0/1 for disabled/enabled processors in current round
  var alreadyDecided = 0
  def receive: PartialFunction[Any, Unit] = {
    case "reset" => {
      responses = 0
      roundCnt = 0
      procStats = new ArrayBuffer[Int]()
      alreadyDecided = 0
      Main.resetNode ! "puppeteer_reset"
    }

    case "beginSimulation" =>
      beginSimulation()

    case "round_done" =>
      responses = (responses + 1) % Main.N
      if (responses == 0) {
        roundCnt = roundCnt + 1
        println("----------------round done------------------")
        if(roundCnt == Main.rounds){
          for (i <- 0 until Main.N) {
            Main.nodes(i) ! "printFinalValue"
          }
          if(alreadyDecided == Main.N){ //if all processes have reached consensus then report as success else report failure
            println("writing to file")
            Main.writeData(1,roundCnt)
          }
          else{ //write the failure stats onto the file
            println("writing to file")
            Main.writeData(0,roundCnt)
          }
        }
        else if(alreadyDecided != Main.N){
          procStats = getProcStatus(roundCnt)
          self ! ("new_round",procStats)
        }
        else if(alreadyDecided == Main.N){
          for (i <- 0 until Main.N) {
            Main.nodes(i) ! "printFinalValue"
          }
          println("writing to file")
          Main.writeData(1,roundCnt)
        }
      }

    case ("new_round",procStats:ArrayBuffer[Int]) =>
      alreadyDecided = 0
      println("---------------------round "+roundCnt.toString+"----------------")
      for (i <- 0 until Main.N) {
        if(procStats(i) == 1)
          Main.nodes(i) ! "enabled"
        else{
          Main.nodes(i) ! "disabled"
        }
      }

    case "already_decided" =>
      alreadyDecided += 1
      if(alreadyDecided == Main.N){
        println("looks like all have converged already....stopping simulation early")
      }

  }
  def beginSimulation(): Unit = {
    println("beginning simulation")
    if(!Main.adversaryMode) {
      println("--------------STANDARD MODE---------------")
      procStats = getProcStatus(roundCnt) //this returns an array of all processes to disabled/enable for the current round
      self ! ("new_round",procStats)
    }
    else if(Main.adversaryMode && Main.N != 2 && Main.K != 1){
      println("PLEASE SET THE NUMBER OF PROCESSORS TO 2 AND DISABLED PROCESSOR LIMIT TO 1 FOR ADVERSARY MODE")
    }
    else{
      println("--------------ADVERSARY MODE----------------")
      procStats = getProcStatus(roundCnt) //this returns an array of all processes to disabled/enable for the current round
      self ! ("new_round",procStats)
    }

  }
  def getProcStatus(rnd: Int): ArrayBuffer[Int] = {
    if(!Main.adversaryMode){
      val nodeList = List.range(0,Main.N)
      val shuffledList = Random.shuffle(nodeList)
      val disabledNodes = shuffledList.take(Main.K)
      val finalStat = new ArrayBuffer[Int]()
      for(i <-0 until Main.N){
        if(disabledNodes.contains(i)){
          finalStat += 0
        }
        else{
          finalStat += 1
        }
      }
      finalStat
    }
    else{
      val finalStat = new ArrayBuffer[Int]()
      if(rnd%5 == 0 ){
        finalStat += 1
        finalStat += 0
      }
      else if(rnd%5 == 1){
        finalStat += 1
        finalStat += 1
      }
      else if(rnd%5 == 2){
        finalStat += 0
        finalStat += 1
      }
      else if(rnd%5 == 3){
        finalStat += 0
        finalStat += 1
      }
      else if(rnd%5 == 4){
        finalStat += 1
        finalStat += 0
      }
      finalStat
    }
  }
}
