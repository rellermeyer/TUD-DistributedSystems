package VotingSystem

import FileSystem.{DistributedSystem, FileSuite}

class FileSuiteManager (newFileSystem: DistributedSystem){

  private val _fileSystem = newFileSystem

  /*def read(fileId: Int): Int = {
    var newSuite: FileSuite = new FileSuite(_fileSuite, fileId)
    val result = newSuite.read()
    newSuite = null
    result
  }

  def write(fileId: Int, newContent: Int): Unit = {
    var newSuite: FileSuite = new FileSuite(_fileSuite, fileId)
    newSuite.write(newContent)
    newSuite = null
  }

  def create(fileId: Int, r: Int, w: Int, weights: Seq[Int]): Unit = {
    var newSuite: FileSuite = new FileSuite(_fileSuite, fileId)
    newSuite.create(r, w, weights)
    newSuite = null
  }*/

}
