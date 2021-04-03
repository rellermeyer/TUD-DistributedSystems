package VotingSystem

import FileSystem.FileSystem

class FileSuiteManager (newFileSystem: FileSystem){

  /**
   * Case class for handling failed method calls
   * @param reason a textual explanation of the reason for failure
   */
  case class FailResult(reason:String)

  /**
   * Private class fields
   */
  private val _fileSystem = newFileSystem
  private var _fileSuiteMonitors = Seq.empty[FileSuite]

  /**
   * Begin a new transaction by setting the tentative state of the file system
   * @return either failure or nothing
   */
  def begin(): Either[FailResult, Unit] = {
    val result = _fileSystem.initTentativeSystem()
    result match {
      case Left(f) => Left(FailResult("begin failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }

  /**
   * Abort a transaction by removing all existing FileSuite monitors
   * @return either failure or nothing
   */
  def abort(): Unit = {
    _fileSuiteMonitors = Seq.empty[FileSuite]
  }

  /**
   * Commit a transaction by setting the definitve state of the file system and removing all FileSuite monitors
   * @return either failure or nothing
   */
  def commit(): Either[FailResult, Unit] = {
    val result = _fileSystem.commitTentativeSystem()
    result match {
      case Left(f) => Left(FailResult("commit failed:\n" + f.reason))
      case Right(r) => {
        _fileSuiteMonitors = Seq.empty[FileSuite]
        Right()
      }
    }
  }

  /**
   * Return a FileSuite monitor if it exists
   * @param suiteId ID of the corresponding file suite
   * @return either a FileSuite monitor or None
   */
  def findMonitor(suiteId: Int): Option[FileSuite] = {
    _fileSuiteMonitors.find(element => element.suiteId == suiteId)
  }

  /**
   * Read from a file suite through a FileSuite monitor
   * First check if a monitor exists already, if not create a new one
   * @param fileId ID of the suite to be read
   * @return either failure or a tuple of the content and read latency
   */
  def read(fileId: Int): Either[FailResult, (Int, Int)] = {
    var fileSuiteMonitor: FileSuite = null
    val findResult = findMonitor(fileId)
    if (findResult.isEmpty) {
      fileSuiteMonitor = new FileSuite(_fileSystem, fileId)
      _fileSuiteMonitors = _fileSuiteMonitors :+ fileSuiteMonitor
    }
    else {
      fileSuiteMonitor = findResult.get
    }

    val result = fileSuiteMonitor.read()

    result match {
      case Left(f) => Left(FailResult("read failed:\n" + f.reason))
      case Right(r) => Right(r)
    }
  }

  /**
   * Write to a file suite through a FileSuite monitor
   * First check if a monitor exists already, if not create a new one
   * @param fileId ID of the suite to be written to
   * @param newContent the new integer content to be written
   * @return either failure or the write latency
   */
  def write(fileId: Int, newContent: Int): Either[FailResult, Int] = {
    var fileSuiteMonitor: FileSuite = null
    val findResult = findMonitor(fileId)
    if (findResult.isEmpty) {
      fileSuiteMonitor = new FileSuite(_fileSystem, fileId)
      _fileSuiteMonitors = _fileSuiteMonitors :+ fileSuiteMonitor
    }
    else {
      fileSuiteMonitor = findResult.get
    }
    val result = fileSuiteMonitor.write(newContent)

    result match {
      case Left(f) => {
        abort()
        Left(FailResult("write failed:\n" + f.reason))
      }
      case Right(r) => Right(r)
    }
  }

  /**
   * Instatiate a new file suite through a FileSuite monitor
   * First check if a monitor exists already, if not create a new one
   * @param fileId ID of the new suite
   * @param r r value of the new suite
   * @param w w value of the new suite
   * @param weights voting weights of the reps. of the new suite
   * @return either failure or nothing
   */
  def create(fileId: Int, r: Int, w: Int, weights: Seq[Int]): Either[FailResult, Unit] = {
    var fileSuiteMonitor: FileSuite = null
    val findResult = findMonitor(fileId)
    if (findResult.isEmpty) {
      fileSuiteMonitor = new FileSuite(_fileSystem, fileId)
      _fileSuiteMonitors = _fileSuiteMonitors :+ fileSuiteMonitor
    }
    else {
      fileSuiteMonitor = findResult.get
    }
    val result = fileSuiteMonitor.createFileSuite(fileId, r, w, weights)

    result match {
      case Left(f) => {
        abort()
        Left(FailResult("create failed:\n" + f.reason))
      }
      case Right(r) => Right()
    }
  }

  /**
   * Delete a file suite through a FileSuite monitor
   * First check if a monitor exists already, if not create a new one
   * @param fileId ID of the suite
   * @return either failure or nothing
   */
  def delete(fileId: Int): Either[FailResult, Unit] = {
    var fileSuiteMonitor: FileSuite = null
    val findResult = findMonitor(fileId)
    if (findResult.isEmpty) {
      fileSuiteMonitor = new FileSuite(_fileSystem, fileId)
      _fileSuiteMonitors = _fileSuiteMonitors :+ fileSuiteMonitor
    }
    else {
      fileSuiteMonitor = findResult.get
    }
    val result = fileSuiteMonitor.deleteFileSuite(fileId)

    result match {
      case Left(f) => {
        abort()
        Left(FailResult("delete failed:\n" + f.reason))
      }
      case Right(r) => Right()
    }
  }
}

/**
 * Companion class
 */
object FileSuiteManager {
  def apply(newFileSystem: FileSystem): FileSuiteManager = {
    val newManager = new FileSuiteManager(newFileSystem)
    newManager
  }
}
