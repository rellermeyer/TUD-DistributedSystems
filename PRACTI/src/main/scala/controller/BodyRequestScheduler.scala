package controller

import core.Node

/**
  * This requests bodies every x seconds for all items that are invalid
  * @param node
  */
class BodyRequestScheduler(node: Node) extends Runnable {
  override def run(): Unit = {
    // The maximum number of bodies that can be updated in one run.
    // Each node should be able to determine its own parameter.
    val MAX_REQUESTS = 5
    var requestCount = 0
    for ((key, item) <- node.checkpoint.getAllItems()) {
      if (item.invalid) {
        requestCount += 1
        node.controller.requestBody(key)
      }
      if (requestCount > MAX_REQUESTS) {
        return
      }
    }
  }
}
