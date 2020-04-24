package com.github.arucard21.globe.replicator.distributedobject

import java.net.URI

sealed trait GlobeMessage { def message: String }
case object Invoke extends GlobeMessage { val message = "Invoke" }
case object InvokeSend extends GlobeMessage { val message = "Send" }
case object Return extends GlobeMessage { val message = "Return" }

object ReplicationSubobject {
  private var locations : Array[URI] = null
  private var isInvoked = false
  private var locked = false

  def start(method: String): GlobeMessage = {
    if (locations == null || locations.isEmpty) {
      locations = CommunicationSubobject.lookup_locations(DOApplication.getLookupServiceUri, DOApplication.getDistributedObjectName)
    }
    method match {
      case "setNumber" => {
        if (acquireLockForDistributedObject) {
          InvokeSend
        }
        else {
          throw new IllegalStateException("Failed to acquire lock for the distributed object")
        }
      }
      case "getNumber" => Invoke
      case _ => throw new IllegalStateException("Unknown method called")
    }
  }

  def invoked(): Unit = {
    isInvoked = true
  }

  def send(method: String, parameter: Int): Boolean = {
    val handledLocations = locations.filter(location => CommunicationSubobject.send_request(location, method, parameter))
    if (handledLocations != null && handledLocations.size == locations.size){
      true
    }
    else{
      throw new IllegalStateException(s"The method $method was not processed correctly at ${locations.size - handledLocations.size} location(s)")
    }
  }

  def finish(): GlobeMessage = {
    isInvoked = false
    if(!locked || releaseLockForDistributedObject) {
      Return
    }
    else{
      throw new IllegalStateException("Not all locks could be released when finishing replication")
    }
  }

  def acquireLockForDistributedObject(): Boolean = {
    if(locked) {
      return false
    }
    // First lock self
    locked = true
    // Simulate a longer locking mechanism (avoids achieving a lock too quickly, making the concurrency test more difficult)
    Thread.sleep(100)
    val ownLocation = DOApplication.getDistributedObjectUri
    val filteredLocations = locations.filter(location =>  location != ownLocation)
    val lockedLocations = filteredLocations.filter(location => CommunicationSubobject.acquire_lock(location))
    if(lockedLocations != null && lockedLocations.size + 1 == locations.size) {
      true
    }
    else {
      // Own lock release
      locked = false
      lockedLocations.map(location => !CommunicationSubobject.release_lock(location))
      if (lockedLocations != null && lockedLocations.size > 0){
        println(s"The lock could not be acquired on all other objects but the acquired locks at ${lockedLocations.size} location(s) could no longer be released")
      }
      false
    }
  }

  def releaseLockForDistributedObject():Boolean = {
    val releasedLocations = locations.filter(location => CommunicationSubobject.release_lock(location))
    if(releasedLocations != null && releasedLocations.size == locations.size) {
      true
    }
    else {
      println(s"The lock could not be released from ${locations.size - releasedLocations.size} location(s)")
      false
    }
  }

  def acquireLock(): Boolean = {
    if (locked){
      false
    }
    else {
      locked = true
      true
    }
  }

  def releaseLock(): Boolean = {
    if (!locked){
      false
    }
    else {
      locked = false
      true
    }
  }
}
