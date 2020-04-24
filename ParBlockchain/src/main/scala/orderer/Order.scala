package orderer

import core.applications.Application.Application
import core.Config
import core.data_structures.BlockChainBlock
import core.data_structures.DependencyGraph
import core.data_structures.Transaction
import core.messages.{BlockMessage, Message, RequestMessage}
import core.Node

import ckite._
import ckite.rpc._
import scala.collection.immutable
import scala.collection.mutable

/**
 * An Orderer Node (as described in the ParBlockchain paper).
 *
 * @constructor Create a new Orderer
 * @param id The id of the Orderer
 * @param ckiteAddress The Ckite address of this Ckite instance
 * @param ckiteMembers The Ckite addresses of the Ckite instances related to all Orderers
 * @param bootstrap Indicates whether to bootstrap a new cluster. Only needed just the first time for the very first node
 * @param executors The Id's of all Executors
 * @param orderers The Id's of all Orderers
 */
class Order(id: String, ckiteAddress: String, ckiteMembers: immutable.Seq[String], bootstrap: Boolean,
            executors: immutable.Seq[String], orderers: immutable.Seq[String]) extends Node(id, executors, orderers) {
  val ckite: CKite = CKiteBuilder().listenAddress(ckiteAddress).rpc(FinagleThriftRpc) //Finagle based transport
    .members(ckiteMembers)
    .stateMachine(new ParBlockStore(this)) //ParBlockStore is an implementation of the StateMachine trait
    .bootstrap(bootstrap) //bootstraps a new cluster if set to true. only needed just the first time for the very first node
    .build

  var timeSinceLastBlock: Long = System.currentTimeMillis

  var currSequenceId: Int = 0 // Reset when new block is created, necessary for the DependencyGraph
  val transactionOrder: mutable.Map[Transaction, Int] = mutable.Map[Transaction, Int]() // Should also be emptied

  // Initialize previous and current block after genesis
  var previousBlock: BlockChainBlock = Config.genesisBlock
  var currentBlock: BlockChainBlock = new BlockChainBlock(Config.genesisBlock.hash(), Config.genesisBlock.getSequenceId + 1)
  // Holds all the applications targeted by transactions currently in the block
  val currentApplications: mutable.Set[Application] = mutable.Set[Application]()

  /**
   * Add a transaction to the BlockChainBlock that is currently being worked on.
   *
   * @param transaction The Transaction that should be added
   */
  def addTransactionToBlock(transaction: Transaction): Unit = {
    currentBlock.addTransaction(transaction)
    currentApplications.add(transaction.getApplication)
    assignSequenceId(transaction)

    if (currentBlock.getTransactions.length >= Config.maxNrTransactions) {
      multicastCurrentBlock()
    } else if (System.currentTimeMillis() - timeSinceLastBlock > Config.maxTime) {
      initCutBlock()
    }
  }

  /**
   * This function cuts the current block with a dummy Transaction containing the current sequence number.
   */
  def initCutBlock(): Unit = {
    ckite.write(Put(Config.cutBlockMessage, Config.cutBlockTransaction(currentBlock.getSequenceId)))
  }

  /**
   * This function assigns the next sequential sequenceId to a given Transaction.
   *
   * @param t The Transaction that should be assigned the next sequenceId
   */
  def assignSequenceId(t: Transaction): Unit = {
    transactionOrder.put(t, currSequenceId)
    currSequenceId += 1
  }

  /**
   * This function generates the DependencyGraph based on the BlockChainBlock passed to the function.
   *
   * @param block The BlockChainBlock of which the DependencyGraph should be generated
   * @return A DependencyGraph corresponding to the Block passed to the function
   */
  def generateDependencyGraph(block: BlockChainBlock): DependencyGraph = {
    val ts = block.getTransactions.sortWith(transactionOrder(_) > transactionOrder(_))
    val graph = new DependencyGraph(currentBlock.getSequenceId)
    var visited: Array[Transaction] = new Array[Transaction](0)

    // Don't visit the last transaction, as it can not have any dependencies
    for (i <- 0 until (ts.length - 1)) {
        if (!visited.contains(ts(i))) {
          val vis = generateDGUtil(ts(i), ts.slice(i + 1, ts.length), visited, graph)
          // Add the nodes visited recursively to 'visited'
          for (x <- vis) {
            visited :+= x
          }
        }
    }
    graph
  }

  def generateDGUtil(t: Transaction, ts: Array[Transaction],
                     visited: Array[Transaction], g: DependencyGraph): Array[Transaction] = {
    // Keep track of a list of all transactions under this path
    var recStack: Array[Transaction] = new Array[Transaction](0)
    var vis: Array[Transaction] = visited.clone()

    for (transaction <- ts) {
      if (transactionOrder(t) > transactionOrder(transaction) &&
          !recStack.contains(transaction)) {
        if (t.getReadSet.intersect(transaction.getWriteSet).nonEmpty ||
          t.getWriteSet.intersect(transaction.getReadSet).nonEmpty ||
          t.getWriteSet.intersect(transaction.getWriteSet).nonEmpty) {
          g.addEdge(transaction, t)

          if (!vis.contains(transaction)) {
            val stack = generateDGUtil(transaction, ts, vis, g)

            // Add the results to the recursive stack
            recStack :+= transaction
            vis :+= transaction
            for (x <- stack) {
              recStack :+= x
              vis :+= x
            }
          }
        }
      }
    }
    recStack
  }

  /**
   * This function multicasts the current BlockChainBlock to all executors.
   */
  def multicastCurrentBlock(): Unit = {
    val dependencyGraph = generateDependencyGraph(currentBlock)
    for (e <- executors) {
      val msg = BlockMessage(currentBlock.getSequenceId, currentBlock, dependencyGraph, currentBlock.hash(),
                             immutable.Set(currentApplications.toSeq: _*), this.id, e)
      communication.sendMessage(msg)
    }

    startNewBlock()
  }

  /**
   * This function is called when a request message is received from a Client.
   *
   * @param msg The message that was received
   */
  override def onRequestMessage(msg: RequestMessage): Unit = {
    val requestMessage = msg.asInstanceOf[RequestMessage]
    if (onRequestMessageValid(requestMessage)) {
      ckite.write(Put(requestMessage.transaction.hash(), requestMessage.transaction))
    }
  }

  /**
   * Checks if a request message is valid. System does not allow spaces in account identifiers.
   * @param msg message that was received
   * @return whether the message is valid
   */
  def onRequestMessageValid(msg: RequestMessage): Boolean = {
    val acc1Valid = !msg.transaction.getAcc1.contains(" ")
    val acc2Valid = msg.transaction.getAcc2 match {
      case Some(x) => !x.contains(" ")
      case None => true
    }
    acc1Valid && acc2Valid
  }

  /**
   * This function is called when a cutBlockMessage is received.
   * When this message is received the current block is mutlicast to all executors.
   *
   * @param seq The sequenceId of the block that has been received
   */
  def receiveCutBlock(seq: Int): Unit = {
    if (currentBlock.getSequenceId == seq) {
      multicastCurrentBlock()
    }
  }

  /**
   * This function clears the information related to the previously processed BlockChainBlock.
   * It is called after a block has been cut and multicast in order to start processing a new BlockChainBlock.
   */
  def startNewBlock(): Unit = {
    previousBlock = currentBlock
    currentBlock = new BlockChainBlock(previousBlock.hash(), previousBlock.getSequenceId + 1)
    timeSinceLastBlock = System.currentTimeMillis()
    transactionOrder.clear()
    currentApplications.clear()
  }

  /**
   * This function starts Ckite.
   */
  def startCkite(): Unit = {
    ckite.start()
  }

  /**
   * This function stops Ckite.
   */
  def stopCkite(): Unit = {
    ckite.stop()
  }
}
