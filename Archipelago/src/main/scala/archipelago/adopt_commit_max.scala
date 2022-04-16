package archipelago

import akka.actor.Actor

import scala.collection.mutable.ArrayBuffer

class max_register extends Actor{
  var register : Set[(Int,Int)] = Set((0,-1))
  def receive: PartialFunction[Any,Unit] = {
    case "reset" => {
      register = Set((0,-1))
      Main.resetNode ! "max_register_reset"
    }
    case (c:Int,v:Int) => //if some processor writes to the max register
      register = register + ((c,v)) //add element to set and send max value back
      sender() ! (register.max,"R_step_done")
  }
}

class adopt_commit_max extends Actor{
  var A = new ArrayBuffer[Int]()
  var B = new ArrayBuffer[(String,Int)]() //B contains a tuple of a string and an int for control and value
  for(i <- 0 until Main.N){
    A += -1 //initialize the array A
    B += (("",-1))
  }
  def receive: PartialFunction[Any, Unit] = {
    case "reset" => {
      A = new ArrayBuffer[Int]()
      B = new ArrayBuffer[(String,Int)]() //B contains a tuple of a string and an int for control and value
      for(i <- 0 until Main.N){
        A += -1 //initialize the array A
        B += (("",-1))
      }
      Main.resetNode ! "adopt_commit_max_reset"
    }

    case ("propose_A_step",v:Int) =>  //make the adopt commit max object perform until the collect of A step
      val index = sender().path.name.toInt //gets the index of the register to write to for the sending processor
      A(index) = v
      val Sa = collectA(A)
      sender() ! (Sa,"A_step_done")

    case ("propose_B_step",sa:Set[Int]) =>
      val index = sender().path.name.toInt
      val Sa_temp = sa - (-1)
      if(Sa_temp.toList.length == 1){
        B(index) = ("commit",Sa_temp.toList.head)
      }
      else{
        B(index) = ("adopt",Sa_temp.max)
      }
      val Sb = collectB(B)
      sender() ! (Sb,"B_step_done")

  }
  def collectA(buff: ArrayBuffer[Int]): Set[Int] = {
    var Sx : Set[Int] = Set()
    for(i <- 0 until Main.N){
      Sx = Sx + buff(i)
    }
    Sx
  }
  def collectB(buff: ArrayBuffer[(String,Int)]): Set[(String,Int)] = {
    var Sx : Set[(String,Int)] = Set()
    for(i <- 0 until Main.N){
      Sx = Sx + buff(i)
    }
    Sx
  }
}
