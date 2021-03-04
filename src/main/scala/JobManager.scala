
object JobManager {
  var taskManagers: Array[TaskManager] = Array.empty[TaskManager]

  def setTaskManagers(x: Array[TaskManager]) = {
      this.taskManagers = x
  }
}
