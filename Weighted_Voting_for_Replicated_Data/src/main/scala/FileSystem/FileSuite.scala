package FileSystem

class FileSuite (suiteId: Int, fileSystem: DistributedSystem){

  /**
   * constructor
   */
  private val _suiteId: Int = suiteId
  private val _fileSystem: DistributedSystem = fileSystem
  private var _fsResp:  FileSystemResponse = fileSystem.collectSuite(_suiteId)

  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId
  def suiteR: Int = _fsResp.findLatest().prefix.r
  def suiteW: Int = _fsResp.findLatest().prefix.w

  def createFSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Unit = {
    fileSystem.createSuite(suiteId, suiteR, suiteW, repWeights)
  }

  def suitRead(): Int = {
    var prefix = _fsResp.findLatest().prefix
    var cId = _fsResp.findReadQuorum(prefix.r, prefix.versionNumber)

    fileSystem.readSuite(cId, _suiteId)
  }

  def suitWrite(newContent: Int): Unit = {
    var prefix = _fsResp.findLatest().prefix
    var cIds = _fsResp.findWriteQuorum(prefix.r, prefix.versionNumber)

    fileSystem.writeSuite(cIds, _suiteId, newContent)
  }







}
