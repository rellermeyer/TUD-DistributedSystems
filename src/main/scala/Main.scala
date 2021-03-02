object Main {
  def main(args: Array[String]): Unit = {
    val no_task_managers = 3;
    val nodes = new Array[Node](no_task_managers)

    for (i <- nodes.indices) {
      nodes(i) = new Node(i)
    }
  }
}
