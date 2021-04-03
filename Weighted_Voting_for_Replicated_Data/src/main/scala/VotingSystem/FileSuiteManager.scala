package VotingSystem

import FileSystem.FileSystem

class FileSuiteManager (newFileSystem: FileSystem){

  case class FailResult(reason:String)

  private val _fileSystem = newFileSystem
  private var _fileSuiteMonitors = Seq.empty[FileSuite]

  def begin(): Either[FailResult, Unit] = {
    val result = _fileSystem.initTentativeSystem()
    result match {
      case Left(f) => Left(FailResult("begin failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }

  def abort(): Unit = {
    _fileSuiteMonitors = Seq.empty[FileSuite]
  }

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

  def findMonitor(suiteId: Int): Option[FileSuite] = {
    _fileSuiteMonitors.find(element => element.suiteId == suiteId)
  }

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

object FileSuiteManager {
  def apply(newFileSystem: FileSystem): FileSuiteManager = {
    val newManager = new FileSuiteManager(newFileSystem)
    newManager
  }
}
