package Beehive

import java.security.MessageDigest
import java.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, TimeoutException, http}
import com.twitter.util.{Await, Duration, Future}

import scala.collection.{immutable, mutable}
import scala.collection.mutable.HashMap
import scala.util.Random
import scala.util.control.Breaks._

class BeehiveService(id: Int, portEnding: Int, nodesCount: Int, client: BeehiveClient, others: List[Int]) extends Service[Request, Response] {

  var dataMap = new HashMap[Int, DataItem]

  val numNodes: Int = nodesCount
  val nodeId: Int = id
  val otherNodes: List[Int] = others.filter(_ != nodeId)
  val nodeIdSeq: immutable.IndexedSeq[Int] = nodeId.toString.map(_.asDigit)

  // Add initial items in the nodes
  dataMap(id) = new DataItem(this.nodeId * 1000, id, 1, 3, true)
  dataMap(id+5) = new DataItem((this.nodeId + 5) * 1000, id+5, 1, 3, true)
  dataMap(id-5) = new DataItem((this.nodeId - 5) * 1000, id-5, 1, 3, true)

  // Thread to run aggregation in
  val aggregateThread: Thread = new Thread {
    override def run() {
      val rand = new Random()
      rand.setSeed(nodeId);

      while(true) {
        Thread.sleep(10000 + 200 * rand.nextInt(10))
        aggregate()
      }
    }
  }
  aggregateThread.start()

  // Thread to run dissemination
  val disseminateThread: Thread = new Thread {
    override def run() {
      val rand = new Random()
      rand.setSeed(nodeId);

      while(true) {
        Thread.sleep(10000 + 200 * rand.nextInt(10))
        disseminate()
      }
    }
  }
  disseminateThread.start()

  // Thread to run replication in
  val replicateThread: Thread = new Thread {
    override def run() {
      val rand = new Random()
      rand.setSeed(nodeId);

      while(true) {
        Thread.sleep(10000 + 200 * rand.nextInt(10))
        replicate()
      }
    }
  }
  replicateThread.start()

  def respond(request: Request): Response = {
    val event = request.getParam("event")

    // Match on the incoming message type
    event match {
      case "lookup" =>
        var result = makeResponse("-1")
        breakable {
          val key = request.getIntParam("key")
          val fromId = request.getIntParam("fromId")
          var hops = request.getIntParam("hops")
          if (hops > 3) {
            result = makeResponse((-1).toString)
            break
          }

          // Check whether we have or have replicate the queried item
          if (this.dataMap.contains(key)) {
            // If we have the value, return it
            val value = this.dataMap(key).getValue
            this.dataMap(key).incrementAccessFrequency()

            result = makeResponse(s"${this.dataMap(key).getValue.toString} ${hops}")
            break
          }

          // If we are at the limit of hops where there is no option of finding it, terminate with a notfound (-1)
          hops += 1
          if (hops > 3) {
            result = makeResponse((-1).toString)
            break
          }

          // If we don't have the item, check which node with one more matching prefix could maybe have it
          var nextNode = this.otherNodes.find((other) => {
            idPrefixEqual(key, hops, other)
          }).getOrElse(-1)

          // If we cannot find a node which could or should have it, find the node that is closest to the item we're looking for
          if (nextNode == -1) {
            val closest: Int = this.otherNodes.reduce((closestId, otherId) =>{
              val closestDist = Math.abs(closestId - key)
              val otherDist = Math.abs(otherId - key)
              if  (closestDist < otherDist) {
                closestId
              } else {
                otherId
              }
            })

            // If the closest node is this node, we haven't got it, terminate with a notfound (-1)
            if (closest == this.nodeId) {
              result = makeResponse((-1).toString)
              break
            } else {
              nextNode = closest
            }
          }

          // If we could find the node that should have the item, query that node for the item
          try {
            val resp = client.lookup(key, nextNode, fromId, hops)
            result = Await.result(resp.onSuccess({ rep: Response =>
              val content = getResponse(rep)
              result = makeResponse(content)
            }), Duration.fromSeconds(4 - hops))
          } catch {
            case _: TimeoutException => result = makeResponse((-1).toString)
            case e: Throwable => System.err.println(s"${e.toString} exception!")
          }
        }
        result

      case "getaggregate" =>
        val responseItems = new util.ArrayList[DataItem]()
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(request.getParam("dataItems"), new TypeToken[util.ArrayList[DataItem]](){}.getType)

        // Reply to the node asking for our specific items counts what our counts are.
        // For the items we're sending, reset the items counts such that they're not counted twice
        dataItems.forEach(item => {
            if (!dataMap.contains(item.getId)) {
              //println(s"item[${item.getId}] was not found for aggregate in $nodeId at repLevel ${item.getReplicationLevel}")
            } else {
              responseItems.add(dataMap(item.getId))
              dataMap(item.getId).resetAccessFrequency()
            }
        })

        val gson = new Gson
        makeResponse(gson.toJson(responseItems))

      case "aggregateup" =>
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(request.getParam("dataItems"), new TypeToken[util.ArrayList[DataItem]](){}.getType)

        // For all items we're receiving from a node lower in the tree, add their count to our intermediate count.
        // If we are the homenode for an item, add it to the aggregatePopularity
        dataItems.forEach(item => {
          if (dataMap.contains(item.getId)) {
            if (dataMap(item.getId).isHomeNode) {
              dataMap(item.getId).increaseAggregatePopularity(item.getAccessFrequency)
            } else {
              dataMap(item.getId).increaseAggregateFrequencyCount(item.getAccessFrequency)
            }
          }
        })

        Response(http.Status.Ok)

      case "disseminate" =>
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(request.getParam("dataItems"), new TypeToken[util.ArrayList[DataItem]](){}.getType)

        // On receiving a disseminate message, update all aggregatePopularities for the items.
        dataItems.forEach(item => {
          if (dataMap.contains(item.getId)) {
            dataMap(item.getId).setAggregatePopularity(item.getAggregatePopularity)
          }
        })

        Response(http.Status.Ok)

      case "update" =>
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(request.getParam("dataItems"), new TypeToken[util.ArrayList[DataItem]](){}.getType)

        // For all items in the update message, update the replicationlevel of the item.
        // If we don't have the item yet, the message was used as a message to indicate that we should replicate the item.
        dataItems.forEach(item => {
          if (dataMap.contains(item.getId)) {
            dataMap(item.getId).setReplicationLevel(item.getReplicationLevel)
          } else {
            dataMap.update(item.getId, new DataItem(item.getValue, item.getId, item.getVersionId, item.getReplicationLevel, false))
          }
        })

        Response(http.Status.Ok)

      case "remove" =>
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(request.getParam("dataItems"), new TypeToken[util.ArrayList[DataItem]](){}.getType)
        val routingMap = new mutable.HashMap[Int, util.ArrayList[DataItem]]

        for (id <- otherNodes) {
          routingMap(id) = new util.ArrayList[DataItem]()
        }

        // For all items in the remove message, find out which other nodes have it replicated, add it to the list to
        // notify other nodes to remove it, too
        dataItems.forEach(item => {
          if (idPrefixEqual(item.getId, item.getReplicationLevel - 1, nodeId)) {
            for (id <- otherNodes) {
              if (idPrefixEqual(item.getId, item.getReplicationLevel - 2, id)) {
                routingMap(id).add(item)
              }
            }
          }
          dataMap.remove(item.getId)
        })

        // Send remove for the other nodes that they'll also be notified
        val gson: Gson = new Gson
        routingMap.foreachEntry((key, value) => {
          val jsonArray: String = gson.toJson(value)

          Await.result(client.remove(key, jsonArray))
        })

        Response(http.Status.Ok)
      case _ =>
        println(s"Default case in chordNode, ${event}")
        Response(http.Status.NotImplemented)
    }
  }

  def sha1Hash(s: String): String = {
    MessageDigest.getInstance("SHA-1").digest(s.getBytes).toString
  }

  // Create a response from a string
  def makeResponse(content: String): Response = {
    val response = http.Response(http.Status.Ok)
    response.clearContent()
    response.setContentString(content)
    response
  }

  // Get the response from a message sent
  def getResponse(resp: Response): String = {
    if (resp.statusCode != http.Status.Ok.code) {
      println(s"RESPONSE WAS NOT 200 OK, WAS: ${resp.statusCode}")
      return "-1"
    }
    resp.getContentString()
  }

  // Simple internal error wrapper
  def internalServerErrorResponse(): Response = {
    http.Response(http.Status.InternalServerError)
  }

  // Check whether a prefix and item match for k length
  def idPrefixEqual(itemId: Int, k: Int, otherNode: Int): Boolean = {
    val itemIdSeq: immutable.IndexedSeq[Int] = itemId.toString.map(_.asDigit)
    val otherNodeIdSeq: immutable.IndexedSeq[Int] = otherNode.toString.map(_.asDigit)

    var result: Boolean = true
    breakable {
      for (i <- 0 until math.min(k, itemIdSeq.length)) {
        if (itemIdSeq(i) != otherNodeIdSeq(i)) {
          result = false
          break
        }
      }
    }

    result
  }

  // Run aggregation phase for this node
  def aggregate(): Unit = {
    val routingMap = new mutable.HashMap[Int, util.ArrayList[DataItem]]

    for (id <- otherNodes) {
      routingMap(id) = new util.ArrayList[DataItem]()
    }

    // Find out which nodes we have to ask for access counts
    for (obj <- dataMap.values) {
      if (!obj.isHomeNode) {
        for (id <- otherNodes) {
          if (idPrefixEqual(obj.getId, obj.getReplicationLevel, id)) {
            routingMap(id).add(obj)
          }
        }
      }
    }

    // Ask each node that should have the item what the count is and add that to the aggregateFrequencyCount
    val gson: Gson = new Gson
    routingMap.foreachEntry((key, value) => {
      val items = new Array[DataItem](value.size())
      value.toArray(items)

      val jsonArray: String = gson.toJson(items)
      val resp = client.getAggregate(key, this.nodeId, jsonArray);
      Await.result(resp.onSuccess { rep: Response =>
        val content = getResponse(rep)
        val dataItems: util.ArrayList[DataItem] = new Gson().fromJson(content, new TypeToken[util.ArrayList[DataItem]](){}.getType)
        dataItems.forEach(i => {
          if (dataMap.contains(i.getId)) {
            dataMap(i.getId).increaseAggregateFrequencyCount(i.getAccessFrequency)
          }
        })
      })
    })

    // Add your own access frequencies to the aggregateFrequencyCount and reset them
    dataMap.values.foreach(item => {
      item.increaseAggregateFrequencyCount(item.getAccessFrequency)
      item.resetAccessFrequency()
    })

    val upMap = new mutable.HashMap[Int, util.ArrayList[DataItem]]

    for (id <- otherNodes) {
      upMap(id) = new util.ArrayList[DataItem]()
    }

    // Find out to where to we need to aggregate up to the root node for items which we are not the homenode for
    for (obj <- dataMap.values) {
      for (id <- otherNodes) {
        if (idPrefixEqual(obj.getId, obj.getReplicationLevel + 1, id) && !obj.isHomeNode) {
          upMap(id).add(obj)
        }
      }
    }

    // Send the accumulated aggregates for the items replicated at each level to one higher
    upMap.foreachEntry((key, value) => {
      val jsonArray: String = gson.toJson(value)
      Await.result(client.aggregateUp(key, this.nodeId, jsonArray))
    })

    // We have propogated up, we can reset the counts
    dataMap.values.foreach(item => item.resetAggregateFrequencyCount())
  }

  // Disseminate method to propogate updates aggregatePopularities for other nodes
  def disseminate(): Unit = {
    val routingMap = new mutable.HashMap[Int, util.ArrayList[DataItem]]

    for (id <- otherNodes) {
      routingMap(id) = new util.ArrayList[DataItem]()
    }

    // Find out for which items we have to update the aggregatePopularities
    for (obj <- dataMap.values) {
      if (obj.getAggregatePopularity != obj.getOldAggregatePopularity) {
        for (id <- otherNodes) {
          if (idPrefixEqual(obj.getId, obj.getReplicationLevel - 1, id)) {
            routingMap(id).add(obj)
          }
        }
      }
    }

    // Disseminate values to the other nodes
    val gson: Gson = new Gson
    routingMap.foreachEntry((key, value) => {
      val jsonArray: String = gson.toJson(value)

      Await.result(client.disseminate(key, this.nodeId, jsonArray))
    })

    // Set old aggregatePopularity such that we know that we have sent updates values
    for (obj <- dataMap.values) {
      if (obj.isHomeNode) {
        obj.setOldAggregatePopularity()
      }
    }
  }

  // Replicate to other nodes
  def replicate(): Unit = {
    val routingMapReplicate = new mutable.HashMap[Int, util.ArrayList[DataItem]]
    val routingMapRemove = new mutable.HashMap[Int, util.ArrayList[DataItem]]
    // All our items on decreasing aggregatePopularity
    val sortedMap = dataMap.values.toList.sortBy(_.aggregatePopularity)
    val firstBound = sortedMap.length / 8.0
    val secondBound = firstBound * 2
    val thirdBound = secondBound * 2

    for (id <- otherNodes) {
      routingMapReplicate(id) = new util.ArrayList[DataItem]()
      routingMapRemove(id) = new util.ArrayList[DataItem]()
    }

    // for each item, determine what the new replicationlevel is.
    // If the replicationlevel has decreased, notify nodes to remove it.
    // If the replicationlevel has increased, notify nodes to replicate it.
    for (i <- sortedMap.indices) {
      val item = sortedMap(i)
      val oldReplicationLevel = item.getReplicationLevel

      if (i <= firstBound) {
        item.setReplicationLevel(0)
        for (id <- otherNodes) {
          routingMapReplicate(id).add(item)
        }
      } else if (i <= secondBound) {
        item.setReplicationLevel(1)
        for (id <- otherNodes) {
          if (oldReplicationLevel < 1) {
            if (idPrefixEqual(item.getId, oldReplicationLevel, id)) {
              routingMapRemove(id).add(item)
            }
          } else {
            if (idPrefixEqual(item.getId, 1, id)) {
              routingMapReplicate(id).add(item)
            }
          }
        }
      } else if (i <= thirdBound) {
        item.setReplicationLevel(2)
        for (id <- otherNodes) {
          if (oldReplicationLevel < 2) {
            if (idPrefixEqual(item.getId, oldReplicationLevel, id)) {
              routingMapRemove(id).add(item)
            }
          } else {
            if (idPrefixEqual(item.getId, 2, id)) {
              routingMapReplicate(id).add(item)
            }
          }
        }
      } else {
        item.setReplicationLevel(3)
        for (id <- otherNodes) {
          if (oldReplicationLevel < 3) {
            if (idPrefixEqual(item.getId, oldReplicationLevel, id)) {
              routingMapRemove(id).add(item)
            }
          } else {
            if (idPrefixEqual(item.getId, 3, id)) {
              routingMapReplicate(id).add(item)
            }
          }
        }
      }
    }

    // Do the notification for replication
    val gson: Gson = new Gson
    routingMapReplicate.foreachEntry((key, value) => {
      val jsonArray: String = gson.toJson(value)

      Await.result(client.update(key, jsonArray))
    })

    // Do the notification for removal
    routingMapRemove.foreachEntry((key, value) => {
      val jsonArray: String = gson.toJson(value)

      Await.result(client.remove(key, jsonArray))
    })
  }

  override def apply(request: http.Request): Future[Response] = {
    Future.value(respond(request))
  }
}
