package main.scala.log

/**
  * WriteLog class
  */
class WriteLog extends Serializable {

  var writeLogItems: List[WriteLogItem] = List[WriteLogItem]()

  /**
    * Add an item to the WriteLog
    *
    * @param writeLogItem a write paul.log item
    */
  def addItem(writeLogItem: WriteLogItem): Unit = {
    writeLogItems = writeLogItems ::: List(writeLogItem)
  }

  /**
    * Get the write paul.log items
    *
    * @param currentTimeVector of type Int
    * @return List[WriteLogItem]
    */
  def partition(currentTimeVector: Long): WriteLog = {
    val writeLog = new WriteLog
    writeLog.writeLogItems = writeLogItems.filter(item => item.timeVector >= currentTimeVector)

    writeLog
  }

  /**
    * Flush the current writeLog.
    */
  def flush(key: Char): Unit = {
    writeLogItems = writeLogItems.filter(item => item.operation.key != key)
  }

  /**
    * Retrieves the summed weights of all items in the list of a certain key
    *
    * @param key The key preferred
    * @return The summed weight of all items with the given key
    */
  def getSummedWeightsForKey(key: Char): Int = {
    writeLogItems.filter(item => item.operation.key == key).map(item => item.operation.value).sum
  }

  /**
    * Retrieves all writeLogItems from a certain key
    *
    * @param key The key
    * @return All writeLogItems for the given key
    */
  def getWriteLogForKey(key: Char): WriteLog = {
    val writeLog = new WriteLog
    writeLog.writeLogItems = writeLogItems.filter(item => item.operation.key == key)

    writeLog
  }
}
