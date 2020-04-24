package Beehive

import com.google.gson.Gson

class DataItem (value: Int, id: Int, versionId: Int, var replicationLevel: Int, homeNode: Boolean, var aggregatePopularity: Int = 0) {
  var accessFrequency: Int = 0
  var aggregateFrequencyCount: Int = 0
  var oldAggregatePopularity: Int = 0

  def resetAccessFrequency(): Unit = {
    accessFrequency = 0
  }

  def incrementAccessFrequency(): Unit = {
    accessFrequency += 1
  }

  def increaseAccessFrequency(v: Int): Unit = {
    accessFrequency += v
  }

  def increaseAggregateFrequencyCount(v: Int): Unit = {
    aggregateFrequencyCount += v
  }

  def resetAggregateFrequencyCount(): Unit = {
    aggregateFrequencyCount = 0
  }

  def getAccessFrequency: Int = {
    accessFrequency
  }

  def setAggregatePopularity(v: Int): Unit = {
    aggregatePopularity = v
  }

  def increaseAggregatePopularity(v: Int): Unit = {
    aggregatePopularity += v
  }

  def getAggregatePopularity: Int = {
    aggregatePopularity
  }

  def setOldAggregatePopularity(): Unit = {
    oldAggregatePopularity = aggregatePopularity
  }

  def getOldAggregatePopularity: Int = {
    oldAggregatePopularity
  }

  def getId: Int = {
    id
  }

  def getVersionId: Int = {
    versionId
  }

  def getReplicationLevel: Int = {
    replicationLevel
  }

  def setReplicationLevel(v: Int): Unit = {
    replicationLevel = v
  }

  def isHomeNode: Boolean = {
    homeNode
  }

  def getValue: Int = {
    value
  }

}

object DataItem {
  def toJson(dataItem: DataItem, gson: Gson = new Gson): String = {
    gson.toJson(dataItem)
  }

  def fromJson(jsonString: String, gson: Gson = new Gson): DataItem = {
    gson.fromJson(jsonString, classOf[DataItem])
  }
}