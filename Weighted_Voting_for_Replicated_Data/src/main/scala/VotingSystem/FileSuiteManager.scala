package VotingSystem

import FileSystem.DistributedSystem

class FileSuiteManager (newFileSystem: DistributedSystem){

  private val _fileSystem = newFileSystem

  def read(fileId: Int): Int = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    val result = newSuite.suiteRead()
    newSuite = null
    result
  }

  def write(fileId: Int, newContent: Int): Unit = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    newSuite.suiteWrite(newContent)
    newSuite = null
  }

  def create(fileId: Int, r: Int, w: Int, weights: Seq[Int]): Unit = {
    var newSuite: FileSuite = new FileSuite(_fileSystem, fileId)
    newSuite.createFSuite(fileId, r, w, weights)
    newSuite = null
  }
}

object FileSuiteManager {
  def apply(newFileSystem: DistributedSystem): FileSuiteManager = {
    val newManager = new FileSuiteManager(newFileSystem)
    newManager
  }
}
