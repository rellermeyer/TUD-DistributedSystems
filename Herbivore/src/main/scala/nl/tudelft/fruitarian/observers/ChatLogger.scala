package nl.tudelft.fruitarian.observers


import java.io.{BufferedWriter, File, FileWriter}
import java.time.LocalDate

import nl.tudelft.fruitarian.p2p.messages.{AnnounceMessage, EntryRequest, FruitarianMessage, ResultMessage}
import nl.tudelft.fruitarian.patterns.Observer

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn.readLine


/**
 * The Chat logger is used for when the application is in chat mode.
 * This mode is implemented for demonstration purposes.
 */
class ChatLogger(transmissionObserver: TransmissionObserver) extends Observer[FruitarianMessage] {
  protected implicit val context: ExecutionContextExecutor = ExecutionContext.global
  case class ChatMessage(datetime: LocalDate, msg: String)
  def stripNonReadableBytes(msg: String): String = BasicLogger.stripNonReadableBytes(msg)

  val inboxFile = new File("inbox.log")
  val inboxWriter = new BufferedWriter(new FileWriter(inboxFile))

  var msgHistory: List[ChatMessage] = Nil
  var inputFuture: Future[String] = _

  private def renderMessage(msg: ChatMessage): Unit =
    println(msg.msg)
  private def requestMessage(): Future[String] = Future {
    readLine(" > ")
  }
  private def writeMessageToInboxFile(msg: ChatMessage): Unit = {
    inboxWriter.write(msg.msg)
    inboxWriter.newLine()
    inboxWriter.flush()
  }
  private def onReceiveMessage(message: String): Unit = {
    val msg = ChatMessage(LocalDate.now(), message)
    msgHistory = msgHistory :+ msg
    writeMessageToInboxFile(msg)
  }

  /**
   * Render a simple menu that allows
   */
  def renderMenu(): Unit = {
    inputFuture = requestMessage()
    inputFuture.onComplete(msg => {
      if (msg.isSuccess) {
        msg.get match {
          case "/debug" => println(transmissionObserver.roundId)
          case "/inbox" => renderInbox()
          case "/inbox clear" => clearInbox()
          case "/help" => renderHelp()
          case message if message.startsWith("/send ") =>
            val actualMessage = message.replace("/send ", "")
            transmissionObserver.queueMessage(actualMessage)
          case _ => renderHelp()
        }
      }
      renderMenu()
    })
  }

  def renderInbox(): Unit = {
    if (msgHistory.isEmpty) {
      println("Nothing here.")
    } else {
      println(s"Found ${msgHistory.length} message(s)")
      msgHistory.foreach(msg => renderMessage(msg))
    }
  }

  def renderHelp(): Unit = {
    println("Help Page for Fruitarian")
    println("  /inbox\t\t\tShows your inbox")
    println("  /inbox clear\t\tClears your inbox")
    println("  /help\t\t\tShows this page")
    println("  /send <msg>\t\tSends the <msg> to the clique")
  }

  def clearInbox(): Unit = {
    msgHistory = Nil
    println("Inbox cleared!")
  }


  // Initial render with no message received.
  renderMenu()

  override def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case EntryRequest(_, _, _) | AnnounceMessage(_, _, _) =>
      onReceiveMessage(s"<Clique>: Node joined!")
    case ResultMessage(_, _, message) => stripNonReadableBytes(message) match {
      /* In case we get a non-empty message print it. */
      case "TIMEOUT" =>
      case s if !s.isEmpty =>
        onReceiveMessage(s"<Clique>: $s")
      case _ =>
    }
    case _ =>
  }
}
