package chatapp.ui

import cats.implicits._
import com.monovore.decline._

class ChatCLI {

}

// TODO: Add client and server subcommands
object ChatCLI extends CommandApp(
  name = "rover-chat",
  header = "Says hello!",
  main = {
    val userOpt =
      Opts.option[String]("target", help = "Person to greet.") .withDefault("world")

    val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.").orFalse

    (userOpt, quietOpt).mapN { (user, quiet) =>
      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
