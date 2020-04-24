package nl.tudelft.fruitarian

import java.net.InetSocketAddress

import nl.tudelft.fruitarian.models.NetworkInfo
import nl.tudelft.fruitarian.observers._
import nl.tudelft.fruitarian.p2p.messages.EntryRequest
import nl.tudelft.fruitarian.p2p.{Address, TCPHandler}

object Main extends App {
  // Define behaviour with program arguments.
  val experimentNode = args.contains("-e")
  val chatNode = args.contains("--chat")
  val experimentStartingNode = args.length == 1 && experimentNode
  val chatStartingNode = args.length == 1 && chatNode
  val startingNode = args.length == 0 || experimentStartingNode || chatStartingNode

  // Use port 5000 as default server port.
  val serverPort = if (startingNode) 5000 else args(0).toInt

  val networkInfo = new NetworkInfo(serverPort)
  val handler = new TCPHandler(serverPort)

  handler.addMessageObserver(BasicLogger)
  handler.addMessageObserver(new Greeter(handler))
  handler.addMessageObserver(new EntryObserver(handler, networkInfo))
  var transmissionObserver = new TransmissionObserver(handler, networkInfo)
  handler.addMessageObserver(transmissionObserver)

  // Give the TCP Handler some time to start up.
  Thread.sleep(1000)

  if (chatNode) {
    // Log to file instead of console in chat mode.
    Logger.setLogToFile()
    networkInfo.chatMode = true
    handler.addMessageObserver(new ChatLogger(transmissionObserver))
  }


  if (experimentNode) {
    val experimentObserver = new ExperimentObserver(handler, transmissionObserver)
    handler.addMessageObserver(experimentObserver)
    // val utilizationSenderObserver = new UtilizationObserver(handler, transmissionObserver)
    // handler.addMessageObserver(utilizationSenderObserver)
  }

  if (startingNode) {
    // If we are a startingNode, start first round.
    transmissionObserver.startMessageRound()
  } else {
    // If we are a client node, send EntryRequest to known node given in the
    // arguments.
    handler.sendMessage(EntryRequest(
      Address(networkInfo.ownAddress.socket),
      Address(new InetSocketAddress(args(1), args(2).toInt)),
      networkInfo.nodeId))
  }

  // On application shutdown, shutdown the handler.
  sys.addShutdownHook(handler.shutdown())
}
