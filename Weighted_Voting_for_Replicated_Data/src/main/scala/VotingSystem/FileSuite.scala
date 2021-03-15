package VotingSystem

import FileSystem.{DistributedSystem, FileSystemResponse}

class FileSuite (fileSystem: DistributedSystem, newSuiteId: Int){

  /**
   * constructor
   */
  //private val _fileSystem: DistributedSystem = newFileSystem
  private val _suiteId: Int = newSuiteId
  private val _fsResp:  FileSystemResponse = fileSystem.collectSuite(_suiteId)


  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId
  //def suiteR: Int = _fsResp.findLatest().prefix.r
  //def suiteW: Int = _fsResp.findLatest().prefix.w

  def createFSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Unit = {
    fileSystem.createSuite(suiteId, suiteR, suiteW, repWeights)
  }

  def suiteRead(): Int = {
    val prefix = _fsResp.findLatest().prefix
    val cId = _fsResp.findReadQuorum(prefix.r, prefix.versionNumber)

    fileSystem.readSuite(cId, _suiteId)
  }

  def suiteWrite(newContent: Int): Unit = {
    val prefix = _fsResp.findLatest().prefix
    val cIds = _fsResp.findWriteQuorum(prefix.w, prefix.versionNumber)

    fileSystem.writeSuite(cIds, _suiteId, newContent)
  }
}
