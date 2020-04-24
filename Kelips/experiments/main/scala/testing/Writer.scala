package testing

import java.io.{File, PrintWriter}

import akka.actor.Actor
import testing.TestMessageCases.{Close, WriteInsertion, WriteLookup, WriteText}

class Writer(directory: String, fileName: String, testType: String) extends Actor {
  val dir = new File(directory)
  if (!dir.exists()) {
    dir.mkdir()
  }
  val printWriter = new PrintWriter(new File(directory + "/" + fileName))
  println(self)

  def write(text: String): Unit = {
    println(text)
    printWriter.write(text + "\n")
  }

  def close(): Unit = {
    println("Closing writer")
    printWriter.close()
  }

  override def receive: Receive = {
    case WriteInsertion(text) => if (testType.equals("insertion")) write(text)
    case WriteLookup(text) => if (testType.equals("lookup")) write(text)
    case WriteText(text) => write(text)
    case Close() => close()
  }
}
