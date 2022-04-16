package archipelago

import akka.actor.Actor

import scala.util.control.Breaks.{break, breakable}

class Processor extends Actor{
  var state = 0 //this can be 0,1,2,3,4 to indicate None,R-done,A-done,B-done, finished
  var Sa : Set[Int] = Set()
  var Sb : Set[(String,Int)] = Set()
  var pValue:Int = -1 //this is the value to propose. Initially -1
  var c = 0 //index of adopt_commit_max_object to refer to
  def receive: PartialFunction[Any, Unit] = {
    case "reset" => {
      state = 0
      Sa = Set()
      Sb = Set()
      pValue = -1
      c = 0
      Main.resetNode ! "node_reset"
    }

    case ((c_prime:Int,v:Int),"R_step_done") =>
      state = 1
      //println(self.path.name,"R_step done","state:",state)
      c = c_prime
      pValue = v
      Main.puppeteer ! "round_done"

    case (sa:Set[Int],"A_step_done") =>
      state = 2
      //println(self.path.name,"A_step done","state:",state)
      Sa = sa
      Main.puppeteer ! "round_done"

    case (sb:Set[(String,Int)],"B_step_done") =>
      state = 3
      Sb = sb
      val Sb_temp = Sb - (("",-1))
      //println("Sb for", self.path.name,"sb:",Sb_temp)
      if(Sb_temp.toList.length == 1){
        val (control,v) = Sb_temp.toList.head
        c = c + 1
        if(control == "commit")
          state = 4
        else
          state = 0
        pValue = v

      }
      else if(Sb_temp.toList.length != 1){
        c = c + 1
        breakable{
          for(i <- Sb_temp.toList.indices){
            val (ctrl,vl) = Sb_temp.toList(i)
            if(ctrl == "commit"){
              pValue = vl
              state = 0
              break
            }
          }
        }
        if(state != 0){
          val (ctrl,vl) = Sb.max
          pValue = vl
          state = 0
        }
      }
      //println(self.path.name,"B_step done","state:",state)
      Main.puppeteer ! "round_done"

    case "enabled" => //enabled for this round by puppeteer
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
        B_step(c,Sa)
      }
      else if(state == 4){ //finished process
        Main.puppeteer ! "already_decided"
        Main.puppeteer ! "round_done"
      }

    case "disabled" =>
      if(state == 4){
        Main.puppeteer ! "already_decided"
      }
      else{
        println("node: "+self.path.name+" disabled")
      }
      Main.puppeteer ! "round_done"

    case "printFinalValue" =>
      println("node: "+self.path.name + " value: "+pValue.toString)

  }
  def R_step(pValue:Int,c:Int): Unit ={
    Main.m ! (c,pValue)
  }
  def A_step(c:Int,v:Int): Unit = {
    Main.C(c) ! ("propose_A_step",v)
  }
  def B_step(c:Int,Sa:Set[Int]): Unit ={
    Main.C(c) ! ("propose_B_step",Sa)
  }
}
