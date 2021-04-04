package invalidationlog

import core.Body
import helper.fileHelper

/**
  * Data holder class for @Checkpoint.
  * @param id of an object
  * @param body
  * @param invalid
  * @param timestamp
  */
case class CheckpointItem(var id: String, body: Body, invalid: Boolean, timestamp: Long) {
  id = fileHelper.makeUnix(id)
}
