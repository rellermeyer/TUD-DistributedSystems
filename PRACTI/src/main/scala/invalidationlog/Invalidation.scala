package invalidationlog

import helper.fileHelper

/*
POJO class for invalidation
 */
case class Invalidation(var objId: String, var timestamp: Long, nodeId: Int) extends Update(objId, timestamp) with Serializable {
  objId = fileHelper.makeUnix(objId)
}
