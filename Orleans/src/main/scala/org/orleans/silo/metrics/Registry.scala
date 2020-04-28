package org.orleans.silo.metrics

import java.util.concurrent.atomic.AtomicInteger

/**
 * Registry for collecting information about requests toi grain.
 */
class Registry() {
  var requestsReceived: AtomicInteger = new AtomicInteger(0)
  var requestsHandled: AtomicInteger = new AtomicInteger(0)
  var grainsActivated: AtomicInteger = new AtomicInteger(0)

  /**
   * Increase the counter of requests received.
   */
  def addRequestReceived(): Unit = {
    requestsReceived.addAndGet(1)
  }

  /**
   * Increase the counter of requests processed.
   */
  def addRequestHandled(): Unit = {
    requestsHandled.addAndGet(1)
  }

  /**
   * Increase the counter of active Grains.
   */
  def addActiveGrain(): Unit = {
    grainsActivated.addAndGet(1)
  }
}
