package executor

import core.{Config, Node}
import core.applications.Application
import core.applications.Application._
import core.data_structures.{BlockChainBlock, BlockChainLedger, BlockChainState, DependencyGraph, Record, Transaction}
import core.messages.{BlockMessage, CommitMessage, Message}

import scala.collection.{immutable, mutable}
import scala.collection.parallel.mutable.ParArray
import scala.util.control.Breaks._

/**
 * An Executor Node (as described in the ParBlockchain paper).
 *
 * @constructor Create a new Executor
 * @param apps The Applications of which the Executor is an agent
 * @param age A Map mapping the other Executor Ids to their assigned Applications
 */
class Executor(apps: mutable.Set[Application], age: mutable.Map[String, mutable.Set[Application]], id: String,
               executors: immutable.Seq[String], orderers: immutable.Seq[String]) extends Node(id, executors, orderers) {
  var ledger: BlockChainLedger = new BlockChainLedger()
  var state: BlockChainState = new BlockChainState()

  var lastSequenceId = 0
  var blockFinished = true
  var currentBlock: BlockChainBlock = Config.genesisBlock
  val blockQueue = new mutable.Queue[BlockMessage]()

  val transactions: mutable.Map[String, Transaction] = mutable.Map[String, Transaction]()
  val executionSet: mutable.Set[Transaction] = mutable.Set[Transaction]()
  val executedTransactions: mutable.Set[(String, mutable.Set[Record])] = mutable.Set[(String, mutable.Set[Record])]()
  val committedTransactions: mutable.Set[String] = mutable.Set[String]()
  val transactionResults: mutable.Map[String, mutable.Set[(mutable.Set[Record], String)]] =
    mutable.Map[String, mutable.Set[(mutable.Set[Record], String)]]()
  val threadSleepTime = 50 // in ms

  val applications: mutable.Set[Application] = apps
  val agents: mutable.Map[String, mutable.Set[Application]] = age
  val smartContract = new SimpleSmartContract()

  /**
   * Broadcasts the executed transactions.
   */
  def broadcastCommit(): Unit = {
    for (e <- executors) {
      if (!e.equals(this.id)) {
        val msg = CommitMessage(immutable.Set(executedTransactions.toSeq: _*), this.id, e)
        communication.sendMessage(msg)
      }
    }
    executedTransactions.clear()
  }

  /**
   * Dequeue a block and execute it
   */
  def dequeueBlock(): Unit = {
    if (blockQueue.nonEmpty) {
      val blockMessage = blockQueue.dequeue()
      if (isValidBlock(blockMessage.sequenceNumber, blockMessage.block, blockMessage.hash)) {
        handleBlock(blockMessage.sequenceNumber, blockMessage.block, blockMessage.dependencyGraph)
      } else {
        dequeueBlock()
      }
    }
  }

  /**
   * This function executes a given Transaction and Multicasts the results.
   * The Multicasting follows Algorithm 2 from the ParBlockchain paper.
   *
   * @param transaction The Transaction that needs to be executed
   * @param graph The Dependency Graph of the block to which the transaction is assigned
   */
  def execute(transaction: Transaction, graph: DependencyGraph): Unit = {
    val res = smartContract.execute(transaction, state)
    state.addEntry(res._1.getId, res._2)
    executedTransactions.add((res._1.getId, res._2))
    var cut = false
    breakable {
      for (e: Transaction <- graph.getGraph(transaction)) {
        if (transaction.getApplication != e.getApplication) {
          cut = true
          break
        }
      }
    }
    if (cut) {
      broadcastCommit()
    }
  }

  @scala.annotation.tailrec
  private def executeUtil(t: Transaction, graph: DependencyGraph): Unit = {
    // if all Pre(x) are in Ce ∪ Xe -> execute the transaction
    // Retrieve the predecessors from the Dependency graph
    val predecessors: mutable.Set[Transaction] = graph.getPredecessors(t)
    if (predecessors.map(f => f.getId).subsetOf(executedTransactions.map(f => f._1).union(committedTransactions))) {
      execute(t, graph)
    } else {
      Thread.sleep(threadSleepTime)
      executeUtil(t, graph)
    }
  }

  /**
   * This function returns the count of matching results regarding a specific transaction.
   *
   * @param transactionId The Id of the transaction
   * @param records The records that should be counted
   * @return An integer equal to the amount of records in transactionResults matching the passed parameter records
   */
  def getMatchingResults(transactionId: String, records: mutable.Set[Record]): Int = {
    val results = transactionResults(transactionId)
    val res = results.count(r => recordsEqual(r._1, records))
    res
  }

  def recordsEqual(set1: mutable.Set[Record], set2: mutable.Set[Record]): Boolean = {
    var res = true
    for (r1 <- set1) {
      var found = false
      for (r2 <- set2) {
        if (r1.equals(r2)) {
          found = true
        }
      }
      if (!found) res = false
    }
    res
  }

  /**
   * This function executes a valid block.
   * The execution follows Algorithm 1 from the ParBlockchain paper.
   *
   * @param n The current sequenceId
   * @param block The block that is being executed
   * @param graph The Dependency Graph corresponding to said block
   */
  def handleBlock(n: Int, block: BlockChainBlock, graph: DependencyGraph): Unit = {
    initialiseResultSet(block)
    committedTransactions.clear()
    currentBlock = block
    lastSequenceId = n
    blockFinished = false
    ledger.addBlock(block)
    for (t <- block.getTransactions) {
      if (applications.contains(t.getApplication)) {
        executionSet.add(t)
      }
    }

    val parExecutionSet: ParArray[Transaction] = executionSet.toParArray
    parExecutionSet.par.foreach { t =>
      executeUtil(t, graph)
    }
    executionSet.clear()
    broadcastCommit()

    blockFinished = true
    dequeueBlock()
  }

  /**
   * This function is called when a commit is received from another executor.
   * This function follows Algorithm 3 from the ParBlockchain paper.
   *
   * @param s The BlockChainState received from the other executor
   * @param execId The Id of the other executor
   */
  def handleCommit(s: immutable.Set[(String, mutable.Set[Record])], execId: String): Unit = {
    val sorted = s.toArray.sortWith((x, y) => currentBlock.transactionIndex(x._1) < currentBlock.transactionIndex(y._1))
    for (r <- sorted.filter(p => isValid(p, execId))) {
      transactionResults.put(r._1, mutable.Set((r._2, execId)))

      if (getMatchingResults(r._1, r._2) >= Application.commitThreshold(transactions(r._1).getApplication)) {
        state.addEntry(r._1, r._2)
        committedTransactions.add(r._1)
      }
    }
  }

  /**
   * This function initialises transactionResults as well as transactions.
   *
   * @param block The block that is being executed
   */
  def initialiseResultSet(block: BlockChainBlock): Unit = {
    transactionResults.clear()
    transactions.clear()
    for (t <- block.getTransactions) {
      transactionResults.put(t.getId, mutable.Set[(mutable.Set[Record], String)]())
      transactions.put(t.getId, t)
    }
  }

  /**
   * This functions checks whether an executor is indeed an agent of the Application containing a given transaction.
   * It is called when receiving a commit message from another executor.
   *
   * @param tuple A tuple containing the transactionId and records that have been sent by another executor
   * @param execId The Id of the executor that sent the commit message
   * @return A Boolean indicating whether the executor is indeed an agent of the Application
   */
  def isValid(tuple: (String, mutable.Set[Record]), execId: String): Boolean = {
    if (transactions.contains(tuple._1)) {
      val transactionApplication = transactions(tuple._1).getApplication
      agents(execId).contains(transactionApplication)
    } else {
      false
    }
  }

  /**
   * Checks whether a Block is valid. A block is considered valid iff:
   * 1. The hash is equal to the given hash.
   * 2. The block is the next block in the sequence.
   * 3. The previous hash in the block corresponds to the hash of the block with the previous sequenceId.
   *
   * @param n The current sequenceId
   * @param block The block that needs to be checked
   * @param hash The hash of the block that needs to be checked
   * @return a Boolean to indicate whether the block is valid
   */
  def isValidBlock(n: Int, block: BlockChainBlock, hash: String): Boolean = {
    block.hash().equals(hash) && block.getSequenceId == n && n == lastSequenceId + 1 && ledger.validateNewBlock(block)
  }

  /**
   * This function is called when a new block is received by the executor.
   * It checks whether the block is valid and calls a function to execute it if this is the case.
   *
   * @param n The current sequenceId
   * @param block The block that is received
   * @param graph The Dependency Graph corresponding to said block
   * @param apps The applications of which transactions are part of said block
   * @param ordererId The orderer from whom the block has been received
   * @param hash The hash of the current block
   */
  def newBlock(n: Int, block: BlockChainBlock, graph: DependencyGraph, apps: immutable.Set[Application], ordererId: String, hash: String): Unit = {
    // Confirm block was not corrupted and is valid
    val blockValid = isValidBlock(n, block, hash)
    if (blockValid && blockFinished) {
      handleBlock(n, block, graph)
    } else {
      blockQueue.enqueue(BlockMessage(n, block, graph, hash, apps, ordererId, this.id))
    }
  }


  override def onNewBlockMessage(msg: BlockMessage): Unit = {
    val newBlockMessage = msg.asInstanceOf[BlockMessage]
    newBlock(newBlockMessage.sequenceNumber, newBlockMessage.block, newBlockMessage.dependencyGraph,
      newBlockMessage.applicationSet, newBlockMessage.sender, newBlockMessage.hash)
  }

  override def onCommitMessage(msg: CommitMessage): Unit = {
    val commitMessage = msg.asInstanceOf[CommitMessage]
    handleCommit(commitMessage.changedState, commitMessage.sender)
  }
}
