package VotingSystem

import FileSystem.DistributedSystem

class FileSuiteManager (newFileSystem: DistributedSystem){

  case class FailResult(reason:String)

  private val _fileSystem = newFileSystem

  def read(fileId: Int): Either[FailResult, (Int, Int)] = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    val result = newSuite.readSuite()

    result match {
      case Left(f) => Left(FailResult("read failed:\n" + f.reason))
      case Right(r) => Right(r)
    }
  }

  def write(fileId: Int, newContent: Int): Either[FailResult, Int] = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    val result = newSuite.writeSuite(newContent)

    result match {
      case Left(f) => Left(FailResult("write failed:\n" + f.reason))
      case Right(r) => Right(r)
    }
  }

  def create(fileId: Int, r: Int, w: Int, weights: Seq[Int]): Either[FailResult, Unit] = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    val result = newSuite.createFileSuite(fileId, r, w, weights)

    result match {
      case Left(f) => Left(FailResult("create failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }
}

object FileSuiteManager {
  def apply(newFileSystem: DistributedSystem): FileSuiteManager = {
    val newManager = new FileSuiteManager(newFileSystem)
    newManager
  }
}
