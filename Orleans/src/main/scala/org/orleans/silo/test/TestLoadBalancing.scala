package org.orleans.silo.test

import org.orleans.silo.control.{CreateGrainRequest, CreateGrainResponse, DeleteGrainRequest}
import org.orleans.silo.services.grain.GrainRef

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.reflect._
import scala.reflect.runtime.universe._

object TestLoadBalancing {
  // Testing load balancing.
  // NOTE: To observe the effect add a Thread.sleep() to GreeterGrain receive() method.
  def main(args: Array[String]): Unit = {
    println("Trying to get the socket")

    var id: String = ""

    val classtag = classTag[GreeterGrain]
    val typetag = typeTag[GreeterGrain]

    // The master grain in the master has ID "master" so it's easy to find!
    val g = GrainRef("master", "localhost", 1400)

    // Try to create a grain
    println("Creating the grain!")
    val result = g ? CreateGrainRequest(classtag, typetag)
    val mappedResult = result.map {
      case value: CreateGrainResponse =>
        println("Received CreateGrainResponse!")
        println(value)
        id = value.id
      case other => println(s"Something went wrong: $other")
    }
    Await.result(mappedResult, 5 seconds)

    println(s"ID of the grain is $id")
    Thread.sleep(1000)

    println("Creating the grain!")
    val result1 = g ? CreateGrainRequest(classtag, typetag)
    val mappedResult1 = result1.map {
      case value: CreateGrainResponse =>
        println("Received CreateGrainResponse!")
        println(value)
        id = value.id
      case other => println(s"Something went wrong: $other")
    }
    Await.result(mappedResult1, 5 seconds)

    println(s"ID of the grain is $id")
    Thread.sleep(1000)

    println("Creating the grain!")
    val result2 = g ? CreateGrainRequest(classtag, typetag)
    val mappedResult2 = result2.map {
      case value: CreateGrainResponse =>
        println("Received CreateGrainResponse!")
        println(value)
        id = value.id
      case other => println(s"Something went wrong: $other")
    }
    Await.result(mappedResult2, 5 seconds)

    println(s"ID of the grain is $id")

    // Search for the grain.
    // Only 1 activation exists by now.
//    println("Searching for the grain")
//    var port : Int = 0
//    // Search for a grain
//    g ? SearchGrainRequest(id, classtag, typetag) onComplete {
//      case Success(value: SearchGrainResponse) =>
//        println(value)
//        port = value.port
//      case Failure(exception) => exception.printStackTrace()
//    }
//    Thread.sleep(1000)
//
//    // Turn on logging in Master's processLoadData() method and
//    // see how the queue piles up.
//    println("Sending hello to the greeter grain")
//    val g1 : GrainRef = GrainRef(id, "localhost", port)
//    for (i ← (1 to 15)) {
//      Thread.sleep(250)
//      g1 ! s"hello from $i"
//    }

//    Thread.sleep(3000)
    // Create another activation of the same grain.
    // Also see if the activation will be created on different slave (It should).
//    println("Try to activate other grain")
//    val resultActivate = g ? ActiveGrainRequest(id, classtag, typetag)
//    val mappedResultActivate = resultActivate.map {
//      case value: ActiveGrainResponse =>
//        println("Received ActiveGrainResponse!")
//        println(value)
//      case other => println(s"Something went wrong: $other")
//    }
//    Await.result(mappedResultActivate, 5 seconds)
//
//    Thread.sleep(2000)

    // Create another grain.
    // Also see if the grain will be created on different slave (It should).
//    var id1 = ""
//    println("Creating the other grain!")
//    val result1 = g ? CreateGrainRequest(classtag, typetag)
//    val mappedResult1 = result1.map {
//      case value: CreateGrainResponse =>
//        println("Received CreateGrainResponse!")
//        println(value)
//        id1 = value.id
//      case other => println(s"Something went wrong: $other")
//    }
//    Await.result(mappedResult1, 5 seconds)
//
//    println(s"ID of the grain is $id1")

    // Search for the grain.
    // 2 activations exists. Searching should return slave with lower total load.
    // (See the logs in Master's processLoadData() method to verify)
//    var port2 = 0
//    println("Searching for the grain")
//    g ? SearchGrainRequest(id, classtag, typetag) onComplete {
//      case Success(value: SearchGrainResponse) =>
//        println(value)
//        port2 = value.port
//      case Failure(exception) => exception.printStackTrace()
//    }
//    Thread.sleep(1000)
//
//    println("Sending hello to the greeter grain")
//    val g2 : GrainRef = GrainRef(id, "localhost", port2)
//    for (i ← (1 to 15)) {
//      Thread.sleep(250)
//      g2 ! s"hello from $i"
//    }

    Thread.sleep(500000)

    // Delete that grain
    println("Trying to delete grain")
    // Try to delete the grain
    g ! DeleteGrainRequest(id)
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) / 1e6 + "ms")
    result
  }
}
