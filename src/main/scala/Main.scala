
object Main {
  def main(args: Array[String]): Unit = {
    println("Started!")

    // Initialize TaskManagers
    val no_task_managers = 3;
    val nodes = new Array[TaskManager](no_task_managers)

    for (i <- nodes.indices) {
      nodes(i) = new TaskManager(i)
      new Thread(nodes(i)).start()
    }

     val tm = new TaskManager(6);
     println(tm.id);
  }
}
