object Main {
  def main(args: Array[String]): Unit = {
    val no_task_managers = 3;
    val nodes = new Array[TaskManager](no_task_managers)

    for (i <- nodes.indices) {
      nodes(i) = new TaskManager(i)
    }
  }
}
