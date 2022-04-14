package Routing

import Messaging.{Publication, Subscription}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Routing table is used for Reverse Path Forwarding. It stores incoming messages based on the incoming port.
 * It is then capable of routing matching messages to the right destinations.
 */
class RoutingTable {

  private val table = mutable.Map[(Int, Int), (Int, String, (String, Int))]()

  /**
   * Get a copy of the routing table.
   * @return The curren routing table.
   */
  def getTable: mutable.Map[(Int, Int), (Int, String, (String, Int))] = {
    table
  }

  /**
   * Add a route to the routing Table. A route is identified by (SenderID, MessageID) and represents
   * a destination, a class, and attributes.
   */
  def addRoute(ID: (Int, Int), Destination: Int, pClass: String, pAttribute: (String, Int)): Unit = {
    table += (ID -> (Destination, pClass, pAttribute))
  }

  /**
   * Get a route based on a unique message identifier.
   * @return A tuple that contains the destination, a class, and attributes.
   */
  def getRoute(ID: (Int, Int)): (Int, String, (String, Int)) = {
    table(ID)
  }

  /**
   * Check if the routing table stores an entry for a unique message identifier.
   * @return True if the messages identifier is found in the table.
   */
  def hasRoute(ID: (Int, Int)): Boolean = {
    table.contains(ID)
  }

  /**
   * Remove a route based on a unique message identifier.
   */
  def deleteRoute(ID: (Int, Int)): Unit = {
    table -= ID
  }

  /**
   * Find matches for an incoming subscription message. All entries that matches the class and attributes of the
   * subscriptions are added to a matches list.
   * @return The list of found matches.
   */
  def findMatch(subscription: Subscription): List[(Int, Int)] = {
    val matches: ListBuffer[(Int, Int)] = ListBuffer[(Int, Int)]()

    // Loop over all entries of the routing table
    for (key <- table.keys) {

      val routeInfo = getRoute(key)

      // If the class of the subscription matches a table entry, proceed by checking the attributes
      if (routeInfo._2.equals(subscription.pClass))
      {
        var validSubscription = false
        val valueAd = routeInfo._3._2
        val valueSub = subscription.pAttributes._2

        if (routeInfo._3._1.equals(subscription.pAttributes._1)) {
          validSubscription = routeInfo._3._1 match {
            case "gt" => valueAd >= valueSub
            case "lt" => valueAd <= valueSub
            case "e" => valueAd == valueSub
            case "ne" => valueAd == valueSub
          }
        }
        // If the class and attributes matches then store it in the found matches list
        if (validSubscription) {
          matches += key
        }
      }
    }
    matches.toList
  }

  /**
   * Find matches for an incoming publication message. All entries that matches the class and attributes of the
   * publications are added to a matches list.
   * @return The list of found matches.
   */
  def findMatch(publication: Publication): List[(Int, Int)] = {
    val matches: ListBuffer[(Int, Int)] = ListBuffer[(Int, Int)]()

    // Loop over all entries of the routing table
    for (key <- table.keys) {

      val routeInfo = getRoute(key)

      // If the class of the publication matches a table entry, proceed by checking the attributes
      if (routeInfo._2.equals(publication.pClass))
      {
        var validPublication = false
        val valueSub = routeInfo._3._2
        val valuePub = publication.pAttributes._2

        if (routeInfo._3._1.equals(publication.pAttributes._1)) {

          validPublication = routeInfo._3._1 match {
            case "gt" => valueSub <= valuePub
            case "lt" => valueSub >= valuePub
            case "e" => valueSub == valuePub
            case "ne" => valueSub == valuePub
          }
        }
        // If the class and attributes matches then store it in the found matches list
        if (validPublication) {
          matches += key
        }
      }
    }
    matches.toList
  }
}
