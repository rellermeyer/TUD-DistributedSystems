package main.scala.tact.manager

import java.time.LocalDateTime

import main.scala.log.{WriteLog, WriteLogItem}
import main.scala.tact.TactImpl

class ConsistencyManager(replica: TactImpl) {

  var numericalError: Int = 0
  var orderError: Int = 0
  var stalenessError: Int = 0

  var timeVectors: Map[Char, TimeVector] = Map[Char, TimeVector]()

  /**
    * Get the latest timeVector of another replica.
    *
    * @param replicaId of type Char
    * @return Long
    */
  def getTimeVector(replicaId: Char, key: Char): Long = {
    if (!timeVectors.contains(replicaId)) {
      timeVectors += replicaId -> new TimeVector()
    }

    val timeVector = timeVectors(replicaId)
    timeVector.getByKey(key)
  }

  /**
    * Set the timeVector of another replica
    *
    * @param replicaId  of type Char
    * @param timeVector of type Long
    */
  def setTimeVector(replicaId: Char, key: Char, timeVector: Long): Unit = {
    val item = timeVectors(replicaId)

    item.setByKey(key, timeVector)
  }

  /**
    * Checks if the given key (conit) is in need of an anti entropy session.
    * This is checked by first updating the errors, then comparing them with the bounds in the conit
    *
    * @param key The key for which the check is done
    * @return True if an anti entropy session is needed, otherwise false
    */
  def inNeedOfAntiEntropy(key: Char): Boolean = {
    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] Anti entropy session check")
    numericalError = 0
    orderError = 0
    stalenessError = 0

    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] => Updating errors")
    updateErrors(key, System.currentTimeMillis())

    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] => Check errors out of bound")
    errorsOutOfBound(key)
  }

  /**
    * Checks if the errors found for a key are out of bound with the errorsBounds specified in the conit.
    *
    * @param key The key for which the errors need to be checked
    * @return True if an error exceeds the bound, false if this is not the case
    */
  def errorsOutOfBound(key: Char): Boolean = {
    val conit = replica.getOrCreateConit(key)

    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] \t => Numerical Error " + numericalError)
    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] \t => Order Error " + orderError)
    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] \t => Staleness Error " + stalenessError + " \t " + System.currentTimeMillis())

    if (conit.numericBound <= numericalError) {
      return true
    }
    if (conit.orderBound <= orderError) {
      return true
    }
    if ((System.currentTimeMillis() - conit.stalenessBound) <= stalenessError) {
      return true
    }

    false
  }

  /**
    * Checks all errors for a certain conit to see if they pass the threshold set.
    * I (Paul) think this should be done every time a value is read.
    * Then, you calculate the errors and see if they pass the set threshold.
    * If not, do some forced anti-entropy sessions.
    *
    * @param key The key(which can be used to get the conit) which you try to check errors for
    */
  def updateErrors(key: Char, stime: Long): Unit = {
    val ecgWriteLog: WriteLog = replica.ecgHistory.read()

    numericalError = calculateNumericalRelativeError(ecgWriteLog, key)
    orderError = calculateOrderError(replica.writeLog, ecgWriteLog, key)
    stalenessError = calculateStaleness(replica.writeLog, ecgWriteLog, key, stime)
    // Uncomment if switch to absolute errors is needed
    // numericalErrorAbsolute = calculateNumericalAbsoluteError(writeLog, key)
  }

  /**
    * Calculates the numerical error (relative)
    * num_error_relative = Fi(D_ideal) - Fi(D_observed)
    *
    * @param writeLog The ECG writelog history
    * @param key      The key which has to be checked
    * @return The numerical relative error.
    */
  def calculateNumericalRelativeError(writeLog: WriteLog, key: Char): Int = {
    // Fi(Dideal) - Fi(Dobserved) == The nweight of the conit of the ECG history minus the nweight of the replica conit
    writeLog.getSummedWeightsForKey(key) - replica.getOrCreateConit(key).value
  }

  /**
    * Calculates the numerical error (absolute)
    * num_error_absolute = 1 - (Fi(D_ideal) / Fi(D_observed))
    *
    * @param writeLog The ECG writelog history
    * @param key      The key which has to be checked
    * @return The numerical absolute error.
    */
  def calculateNumericalAbsoluteError(writeLog: WriteLog, key: Char): Double = {
    // 1- (Fi(Dideal) / Fi(Dobserved)) == The nweight of the conit of the ECG history devided by the nweight of the replica conit
    1.0 - (writeLog.getSummedWeightsForKey(key) / replica.getOrCreateConit(key).value)
  }


  /**
    * Calculates the Order Error of the current replica compared to the ECG
    *
    * @param writeLog the write log of the current replica
    * @return the order error for the current replica compared to the ECG
    */
  def calculateOrderError(writeLog: WriteLog, ecgWriteLog: WriteLog, key: Char): Int = {
    // Find longest prefix, count oweight of all reads after that
    val prefix = getPrefix(writeLog, ecgWriteLog, key)
    var orderError = 0
    var i = 0

    for (writeLogItem: WriteLogItem <- writeLog.writeLogItems) {
      if (i >= prefix) {
        orderError += oweight(writeLogItem, key)
      }
      i = i + 1
    }

    orderError
  }


  /**
    * Calculates the staleness error for the given key
    *
    * @param replicaWriteLog The WriteLog of the replica
    * @param ecgWriteLog     The WriteLog of the ECG History
    * @param key             The key of the item
    * @param stime           The time of the submission of the writeLogItem to the TRACK replica
    * @return The staleness error
    */
  def calculateStaleness(replicaWriteLog: WriteLog, ecgWriteLog: WriteLog, key: Char, stime: Long): Int = {
    /* ecgHistory.writeLog retrieves the ecg history for a key. writeLog is for the conit history */
    val idealNotObserved = idealMinusObserved(ecgWriteLog.getWriteLogForKey(key),
      replicaWriteLog.getWriteLogForKey(key))

    var min: Long = Long.MaxValue
    for (writeLogItem: WriteLogItem <- idealNotObserved.writeLogItems) {
      if ((nweight(writeLogItem, key) != 0) && (writeLogItem.timeVector < stime))
        if (writeLogItem.timeVector < min) {
          //find smallest rtime
          min = writeLogItem.timeVector
        }
    }

    (stime - min).toInt
  }

  /**
    * Given two logs, retrieves the writeLog only found in the ideal log
    *
    * @param ideal    The ideal log. Any items found here not found in observed will be returned
    * @param observed The observed log.
    * @return The distinct items in the ideal log
    */
  def idealMinusObserved(ideal: WriteLog, observed: WriteLog): WriteLog = {
    val writeLog = new WriteLog

    for (writeLogItem <- ideal.writeLogItems) {
      if (!observed.writeLogItems.contains(writeLogItem)) {
        writeLog.addItem(writeLogItem)
      }
    }
    writeLog
  }

  /**
    * Determines the OWeight of the function. According to the paper:
    * Order weight: defined to be a mapping from the tuple (W, F, D) to a nonnegative real value.
    * We assume it is either 1 or 0 if the write log item corresponds to the conit key or not.
    *
    * @param writeLogItem The write operation
    * @param key          The conit to which the write is done
    * @return The OWeight of the write operation
    */
  def oweight(writeLogItem: WriteLogItem, key: Char): Int = {
    if (writeLogItem.operation.key == key) 1 else 0
  }

  /**
    * Retrieves the nweight of an key.
    *
    * @param writeLogItem The item to check the nweight for
    * @param key          Not needed right now
    * @return The nweight of an item
    */
  def nweight(writeLogItem: WriteLogItem, key: Char): Int = {
    //    var D_current = replica.getOrCreateConit(W.operation.key).value
    //    var D_ideal = D_current + W.operation.value
    //    D_ideal - D_current
    writeLogItem.operation.value
  }

  /**
    * Finds the size of the shared prefix for a given key of two writeLogs
    *
    * @param writeLog1 The first writeLog
    * @param writeLog2 The second writeLog
    * @param key       The key for which the longest prefix must be determined
    * @return The size of the shared prefix of the two writeLogs for a certain key
    */
  def getPrefix(writeLog1: WriteLog, writeLog2: WriteLog, key: Char): Int = {
    var count = 0
    val hist1 = writeLog1.getWriteLogForKey(key)
    val hist2 = writeLog2.getWriteLogForKey(key)

    for (i <- hist1.writeLogItems.indices) {
      if (hist1.writeLogItems(i) != hist2.writeLogItems(i)) {
        return count
      } else {
        count += 1
      }
    }
    count
  }
}
