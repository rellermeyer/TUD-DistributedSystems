package helper

import java.io.File

import Exceptions.Exceptions
import akka.http.scaladsl.server.directives.FileInfo

import scala.util.matching.Regex

object fileHelper {


  val numberPattern: Regex = "(?:^|/)\\.\\.(/|$)".r

  def checkSandbox(path: String): Unit = {

    val m = numberPattern.findFirstMatchIn(path)
    if (m.isDefined)
      throw Exceptions.SecurityException("SANDBOX_ERROR", "Body with potential sandbox injection detected.")
  }

  def tempDestination(fileInfo: FileInfo): File =
    File.createTempFile(fileInfo.fileName + "temp", ".tmp")

  def makeUnix(str: String): String = {
    var s = if (str.charAt(0) == '/') str.substring(1) else str
    s = s.replace("\\", "/")
    s = s.replace("//", "/")

    s
  }

}
