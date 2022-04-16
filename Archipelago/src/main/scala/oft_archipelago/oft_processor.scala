package oft_archipelago

import akka.actor.Actor
import oft_archipelago.Main._

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

class Processor extends Actor{
  var state = 0 //this can be 0,1,2,3,4 to indicate None,R-done,A-done,B-done, finished
  var pValue:Int = -1 //this is the value to propose. Initially -1
  var c = 0 //index of adopt_commit_max_object to refer to
  var procStats: ArrayBuffer[Int] = new ArrayBuffer()
  //oft variables
  var R: Set[(Int, Int)] = Set()
  var flag = false
  var status = 0 //neutral state, neither enabled nor disabled
  var A = new ArrayBuffer[Set[Int]]()
  var B = new ArrayBuffer[Set[(Boolean,Int)]]() //B contains a tuple of a string and an int for control and value
  var num_R_received = 0
  var num_A_received = 0
  var num_B_received = 0
  var A_received : Set[Int] = Set()
  var B_received : Set[(Boolean,Int)] = Set()
  for (i <- 0 until N){
    procStats += -1
  }
  for(i <- 0 until rounds){
    A += Set.empty
    B += Set.empty
  }
  def receive: PartialFunction[Any, Unit] = {
    case "reset" => {
      state = 0
      pValue = -1
      c = 0
      procStats = new ArrayBuffer()
      A = new ArrayBuffer[Set[Int]]()
      B = new ArrayBuffer[Set[(Boolean,Int)]]()
      num_A_received = 0
      num_R_received = 0
      num_B_received = 0
      A_received = Set()
      B_received = Set()
      status = 0
      flag = false
      R = Set()
      for (i <- 0 until N){
        procStats += -1
      }
      for(i <- 0 until rounds){
        A += Set.empty
        B += Set.empty
      }
      Main.resetNode ! "node_reset"
    }

    case ((c_prime:Int,v:Int),"R_step_done") =>
      state = 1
      c = c_prime
      pValue = v
      //println("R_step node: "+self.path.name+"<C,V>: "+c.toString+" "+pValue.toString)
      Main.puppeteer ! "round_done"

    case "A_step_done" =>
      state = 2
      //println("A_step node: "+self.path.name+"<C,V>: "+c.toString+" "+pValue.toString)
      Main.puppeteer ! "round_done"

    case ("B_step_done") =>
      //println("B_step node: "+self.path.name+"<C,V>: "+c.toString+" "+pValue.toString)
      Main.puppeteer ! "round_done"

    case ("enabled",ps: ArrayBuffer[Int]) => //enabled for this round by puppeteer
      status = 1
      procStats = ps
      if(pValue == -1){
        pValue = self.path.name.toInt //this can be changed to a more generic value later
      }
      if(state == 0){
        println("node: "+self.path.name+" starting R step")
        R_step(pValue,c)
      }
      else if(state == 1){
        println("node: "+self.path.name+" starting A step")
        A_step(c,pValue)
      }
      else if(state == 2){
        println("node: "+self.path.name+" starting B step")
        B_step(c,pValue,flag)
      }
      else if(state == 3){
        //println("hmm weird",self.path.name)
      }
      else if(state == 4){ //finished process
        Main.puppeteer ! "already_decided"
        Main.puppeteer ! "round_done"
      }

    case ("disabled",ps:ArrayBuffer[Int]) =>
      status = -1
      procStats = ps
      if(state == 4){
        Main.puppeteer ! "already_decided"
      }
      else{
        //println("node: "+self.path.name+" disabled state:"+state.toString)
      }
      Main.puppeteer ! "round_done"

    case "printFinalValue" =>
      println("node: "+self.path.name + " value: "+pValue.toString)
    case ("R_request",j:Int,v:Int) => {
      if(1 == 1){ //only process if enabled
        //println("sending R")
        R = R + ((j,v))
        sender() ! ("R_response",j,R)
      }
      else{
        println("hmm weird. Got from "+sender().path.name+" I'm "+self.path.name+" my status is "+status.toString+" my proc status is "+procStats(self.path.name.toInt))
      }
    }
    case ("R_response",j:Int,r: Set[(Int, Int)]) => {
      if(1==1){
        num_R_received = (num_R_received + 1)
        if(num_R_received <= N-K){
          R = R union r
        }
        if(num_R_received == N-K){
          //println("done R")
          num_R_received = 0
          var maxI = 0
          for (tuple <- R) {
            if (tuple._1 > maxI)
              maxI = tuple._1
          }
          var maxV = 0
          for (tuple <- R) {
            if (tuple._1 == maxI && tuple._2 > maxV)
              maxV = tuple._2
          }
          c = maxI
          pValue = maxV
          self ! ((c,pValue),"R_step_done")
        }
      }


    }
    case ("A_request",j:Int,v:Int) => {
      if(1 == 1){
        A(j)  = A(j) + ((v))
        sender() ! ("A_response",j,A(j))
      }
      else{
        println("hmm weird A")
      }

    }
    case ("A_response",j:Int,aj:Set[Int])=> {
      if (1 == 1) {
        num_A_received += 1
        if (num_A_received <= N - K ) {
          A_received = A_received union aj
        }
        if (num_A_received == N - K ) {
          //println("done A")
          val S = A_received
          if (S.toList.length == 1 && N-K != 1) {
            flag = true
            pValue = S.toList(0)
          }
          else {
            flag = false
            pValue = S.max
          }
          A_received = Set()
          num_A_received = 0
          self ! "A_step_done"
        }
      }
    }

    case ("B_request",j:Int,f:Boolean,v:Int) => {
      if(1 == 1){
        B(j) = B(j) union Set((f,v))
        sender() ! ("B_response",j,B(j))
      }
      else{
        println("hmm weird B")
      }

    }
    case ("B_response",j:Int,bj: Set[(Boolean,Int)]) => {
      num_B_received += 1
      if(num_B_received <= N-K){
        B_received = B_received union bj
      }
      if(num_B_received == N-K){
        val S = B_received
        //println("done B")
        //c = c + 1
        if(S.toList.length == 1){
          val (b,v) = S.toList(0)
          if(b == true){
            pValue = v
            state = 4
          }
          else{
            pValue = v
            state = 0
          }
        }
        else{
          breakable{
            for(i <- S.toList.indices){
              val (ctrl,vl) = S.toList(i)
              if(ctrl == true){
                pValue = vl
                state = 0
                break
              }
            }
          }
          if(state != 0){
            val (ctrl,vl) = S.max
            pValue = vl
            state = 0
          }
        }
        B_received = Set()
        num_B_received = 0
        self ! "B_step_done"
      }
    }
  }
  def R_step(v:Int,c:Int): Unit ={
    var cnt = 0
    for(i <- 0 until Main.N){
      if(procStats(i) == 1){
        nodes(i) ! ("R_request",c,v)
      }
      else{
        cnt += 1
      }
    }
    //println("I'm "+self.path.name+" I didn't send to "+cnt)
    //Main.m ! (c,pValue)
  }
  def A_step(c:Int,v:Int): Unit = {
    for(i <- 0 until Main.N){
      if(procStats(i) == 1){
        nodes(i) ! ("A_request",c,v)
      }

    }
    //Main.C(c) ! ("propose_A_step",v)
  }
  def B_step(c:Int,v:Int,f:Boolean): Unit ={
    for(i <- 0 until Main.N){
      if(procStats(i) == 1){
        nodes(i) ! ("B_request",c,flag,pValue)
      }

    }
  }
}
