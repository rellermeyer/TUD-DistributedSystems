package nl.tudelft.fruitarian

import java.io.{BufferedWriter, File, FileWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


object Logger {
  object Level extends Enumeration {
    type level = Value
    val INFO, DEBUG, ERROR = Value
  }
  import Level._

  // The logLevels define which types of logs are acted upon.
  var logLevels: Seq[Level.Value] = List(INFO, DEBUG, ERROR)

  // The logAction defines what happens to each log entry.
  private var logAction: String => Unit = println

  def log(msg: String, lvl: Level.Value): Unit = {
    if (logLevels.contains(lvl)){
      logAction(s"[$lvl] $msg")
    }
  }

  private var logFile: File = _
  private var logFileWriter: BufferedWriter = _

  def setLogToFile(): Unit = {
    logFile = new File("application.log")
    logFileWriter = new BufferedWriter(new FileWriter(logFile))
    logFileWriter.write(s"[START] ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm"))}")
    logFileWriter.newLine()
    logAction = logToFile
  }

  private def logToFile(msg: String): Unit = {
    logFileWriter.write(msg)
    logFileWriter.newLine()
    logFileWriter.flush()
  }
}
