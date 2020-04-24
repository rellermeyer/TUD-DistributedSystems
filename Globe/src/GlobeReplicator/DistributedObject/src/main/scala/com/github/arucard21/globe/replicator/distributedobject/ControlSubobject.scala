package com.github.arucard21.globe.replicator.distributedobject

object ControlSubobject {

  def handle_request(method : String, parameter: Int) = {
    if(method == "setNumber"){
      SemanticsSubobject.number = parameter
    }
    else{
      throw new IllegalStateException("Could not handle the request with unknown method: " + method)
    }
  }

  def getNumber: Int = {
    var returnValue: Int = -1
    val replication = ReplicationSubobject.start("getNumber")
    replication match {
      case Invoke => {
        returnValue = SemanticsSubobject.number
        ReplicationSubobject.invoked
      }
      case InvokeSend => {
        println("Invoking getNumber with replication. This is incorrect for our replication policy")
        returnValue = SemanticsSubobject.number
        ReplicationSubobject.invoked
        ReplicationSubobject.send("getNumber", -1)
      }
      case _ => {
        throw new IllegalStateException("The Replication subobject returned an unexpected value on start method")
      }
    }
    ReplicationSubobject.finish match {
      case Return => returnValue
      case _ => throw new IllegalStateException("Could not finish the replication mechanism")
    }
  }

  def setNumber (newNumber : Integer) = {
    val replication = ReplicationSubobject.start("setNumber")
    replication match {
      case Invoke => {
        println("Invoking setNumber without replication. This is incorrect for our replication policy")
        SemanticsSubobject.number = newNumber
        ReplicationSubobject.invoked
      }
      case InvokeSend => {
        SemanticsSubobject.number = newNumber
        ReplicationSubobject.invoked
        ReplicationSubobject.send("setNumber", newNumber)
      }
      case _ => {
        throw new IllegalStateException("The Replication subobject returned an unexpected value on start method")
      }
    }
    ReplicationSubobject.finish match {
      case Return => true
      case _ => throw new IllegalStateException("Could not finish the replication mechanism")
    }
  }

  def getState = {
    SemanticsSubobject.number.toString
  }

  def setState (newState : String) = {
    SemanticsSubobject.number = newState.toInt
  }
}
