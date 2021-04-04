package invalidationlog

import core.{Body, Controller}

/**
  * Class that is responsible for processing invalidations in terms of checkpoint.
  *
  * @param controller
  */
class CheckpointProcessor(controller: Controller) extends Processor {
  /**
    * General method that processes incoming invalidation in terms of checkpoint.
    *
    * @param invalidation
    */
  override def process(inv: Invalidation): Unit = {
    processInvalidation(controller.node.checkpoint.getById(inv.objId), inv)
  }

  /**
    * Function that processes incoming body in terms of checkpoint.
    *
    * @param body
    * @return
    */
  def processUpdate(newBod: Body) = {
    val maybeItem = controller.node.checkpoint.getById(newBod.path)
    maybeItem match {
      case Some(value) => processExistingBod(value, newBod)
      case None => addNewBody(newBod)
    }
  }


  /**
    * Function that updates the existing body entry in @Checkpoint . It contains all the update logic.
    *
    * @param value
    * @param newBod
    * @return
    */
  private def processExistingBod(value: CheckpointItem, newBod: Body) = {
    if (newBod.timestamp <= value.timestamp) Unit

    val newCheckpointItem = new CheckpointItem(newBod.path, newBod, false, newBod.timestamp)
    controller.node.checkpoint.update(newCheckpointItem)
  }


  /**
    * Functional style function, that process null values.
    *
    * @param existingItem
    * @param inv
    */
  private def processInvalidation(existingItem: Option[CheckpointItem], inv: Invalidation): Unit = {
    existingItem match {
      case Some(item) => processExistingInv(inv, item)
      case None => addNewItem(inv)
    }
  }

  private def addNewItem(newItem: Invalidation): Unit = {
    // not sure what to do with invalidations that contains object ID which is not in the checkpoint
  }

  private def addNewBody(body: Body): Unit = {
      controller.node.checkpoint.update(CheckpointItem(body.path, body, false, controller.node.clock.time))
  }
  /**
    * Method that processes invalidation on existing checkpoint item. If timestamp of invalidation is higher, it updates checkpoint, marking the item invalid
    *
    * @param inv  received invalidation
    * @param item existing checkpoint item
    */
  private def processExistingInv(inv: Invalidation, item: CheckpointItem): Unit = {
    //  From the book "Note that checkpoint update for an incoming invalidation is skipped if the checkpoint entry already stores a logical time that is at least as high
    // as the logical time of the incoming invalidation."
    if (inv.timestamp <= item.timestamp) return

    val newItem = new CheckpointItem(item.id, item.body, true, inv.timestamp)
    controller.node.checkpoint.update(newItem)

    controller.node.clock.receiveStamp(inv)
  }
}
