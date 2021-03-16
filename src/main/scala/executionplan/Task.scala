package executionplan

case class Task(jobID: Int, taskID: Int, from: Array[Int], to: Array[Int], operator: String)
