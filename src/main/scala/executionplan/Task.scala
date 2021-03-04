package executionplan

case class Task[A, B](from: String, to: String, operator: String, fun: A => B)
