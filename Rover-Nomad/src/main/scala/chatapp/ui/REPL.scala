package chatapp.ui

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class REPL[A](reader: REPL[A]#Reader, executor: REPL[A]#Executor, printer: REPL[A]#Printer) {
  type Reader = () => String
  type Executor = String => Future[A]
  type Printer = A => String

  def loop(): Future[Unit] = {
    var looping = true
    async {
      while(looping) {
        val input = reader()

        if (input == null || input == "/exit") {
          looping = false
        } else if(input == "") {
          print(s"${REPL.UP}${REPL.ERASE_LINE_AFTER}\r")
        } else {
          val result = await(executor(input))
          val output = printer(result)

          print(output)
        }
      }
    }
  }

}

object REPL {
	val ERASE_SCREEN_AFTER="\u001b[0J"
 	val ERASE_LINE_BEFORE="\u001b[1K"
  val ERASE_LINE_AFTER="\u001b[0K"

	val HOME="\u001b[1H"
	val UP="\u001b[1A"
	val DOWN="\u001b[1B"
	val FORWARD="\u001b[1C"
	val BACKWARD="\u001b[1D"

  val SAVE_CURSOR="\u001b[0s"
  val RESTORE_CURSOR="\u001b[0u"

  def main(args: Array[String]): Unit = {
    val reader = () => {
			print(s"${REPL.ERASE_LINE_BEFORE}${REPL.ERASE_SCREEN_AFTER}\r> ")
			scala.io.StdIn.readLine()
    }
    val executor = (input: String) => {
      async { input }
    }
    val printer = (string: String) => {
 			val cls = s"${string.split("\n").map(c => UP).mkString("")}$ERASE_LINE_BEFORE$ERASE_SCREEN_AFTER\r"
      // Prepend two spaces to match input indentation of "> "
      val text = string.split("\n").map(line => s"  $line").mkString("\n")
			s"$cls$text\n"
    }
    val repl = new REPL(reader, executor, printer)
    repl.loop()
  }
}
