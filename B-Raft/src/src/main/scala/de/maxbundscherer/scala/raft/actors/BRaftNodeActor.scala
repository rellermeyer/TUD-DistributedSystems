package de.maxbundscherer.scala.raft.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import de.maxbundscherer.scala.raft.aggregates.Aggregate.BehaviorEnum.BehaviorEnum
import de.maxbundscherer.scala.raft.aggregates.Aggregate._
import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate.GrantVote.GrantVoteSigned
import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate.LogEntry
import de.maxbundscherer.scala.raft.schnorr.Schnorr.{string_sign, string_verify}
import de.maxbundscherer.scala.raft.utils.{Configuration, Hasher, RaftScheduler}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext

// TODO verify/sign more (or all?) messages?
object BRaftNodeActor {

  import akka.actor.Props

  val prefix: String = "BraftNodeActor"

  def props()(implicit executionContext: ExecutionContext): Props = Props(new BRaftNodeActor())

  /**
    * Internal (mutable) actor state
    *
    */

  /**
    *
    * @param lastHashCode          Int (last hashcode from data) (used in FOLLOWER and LEADER behavior)
    * @param entryLog              ArrayBuffer[LogEntry] to keep track of all entries, in the order they arrive, and whether they
    *                              are committed yet
    * @param appendEntryResponseMap    Map (Int ->Set[String]) that links LogEntry indices to a set of nodes that have already
    *                              written that log entry. Used to control when an entry is committed by the node.
    * @param publicKey             PublicKey used by other nodes to verify messages
    * @param privateKey            Private Key used by this node to sign messages
    * @param hasher                Hasher (sha256)
    * @param publicKeyStorage      Map(String -> BigInt) stores the public keys of all other nodes, used for verifying messages
    *                              were not tampered with.
    * @param term                  current Leader term
    * @param byzantineActor        ??? TODO
    * @param behaviour             Current behaviour this node is exhibiting (one of :
    *                              UNINITIALIZED, FOLLOWER, CANDIDATE, LEADER, SLEEP)
    * @param forceIamNotConsistent boolean to force an IamNotConsistent RPC to be sent.
    *                              Used when a majority of nodes committed a LogEntry, but this node
    *                              has not received it yet.
    * @param voteRequestResponses  Map to track the Votes of each node in a Leader election (only for Leader state)
    *                              Maps the node ID to GrantVoteSigned object
    */
  case class BRaftNodeState(
                             var lastHashCode: BigInt = -1,
                             var entryLog: ArrayBuffer[LogEntry] = ArrayBuffer(),
                             var appendEntryResponseMap: Map[Int, Set[String]] = Map.empty,
                             var publicKey: BigInt = -1,
                             var privateKey: BigInt = -1,
                             var hasher: Hasher = Hasher(),
                             var publicKeyStorage: Map[String, BigInt] = Map.empty,
                             var term: Int = 0,
                             var byzantineActor: Option[ActorRef] = None,
                             var behaviour: BehaviorEnum = BehaviorEnum.UNINITIALIZED,
                             var forceIamNotConsistent: Boolean = false,
                             var voteRequestResponses: Map[String, GrantVoteSigned] = Map.empty,
                             var lastHeartBeatTimestamp: Long = 0
                           ) extends NodeState

}

/**
  * ------------------
  * --- BRaft Node ----
  * ------------------
  *
  * # 5 Behaviors (Finite-state machine / FSM)
  *
  * !!! SEE PROJECT README !!!
  *
  * - (UNINITIALIZED)
  * - FOLLOWER (Default - after init)
  * - CANDIDATE (after election timeout)
  * - LEADER
  * - (SLEEP) (after simulated crash in LEADER)
  */
class BRaftNodeActor()(implicit val executionContext: ExecutionContext)
  extends Actor
    with ActorLogging
    with RaftScheduler
    with Configuration {

  import BRaftNodeActor._
  import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate._

  val notHandled = new Object

  val notHandledFun: Any => Object = (_: Any) => notHandled

  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    log.info(s"Received message:${msg.getClass.toGenericString},${this.state.behaviour},${this.state.term}")
    super.aroundReceive(receive, msg)
  }

  /**
    * Mutable actor state
    */
  override val state: BRaftNodeState = BRaftNodeState()

  log.info("Actor online (uninitialized)")

  /**
    * Uninitialized behavior
    */
  override def receive: Receive = {

    case InitActor(neighbours, keyPair, clientPublicKey, keys) =>
      log.info("Initializing actor")

      state.neighbours = neighbours
      state.majority = ((neighbours.size + 1) / 2) + 1
      state.privateKey = keyPair._1
      state.publicKey = keyPair._2
      state.publicKeyStorage = keys
      state.publicKeyStorage += (CLIENT_NAME -> clientPublicKey)

      log.debug(s"State: ${state.publicKeyStorage.toString()}")
      log.debug(s"pubkey: ${state.publicKey}, keypair: ${keyPair}")

      changeBehavior(
        fromBehavior = BehaviorEnum.UNINITIALIZED,
        toBehavior = BehaviorEnum.FOLLOWER,
        loggerMessage = s"Got ${state.neighbours.size} neighbours (majority=${state.majority})"
      )

    case any: Any => log.error(s"Node is not initialized but got message $any")

  }

  def verify_votes(term: Int, voteRequestResponses: Map[String, GrantVoteSigned]): Boolean = {
    // For each voteRequestReponse verify signature, and count number of granted votes
    val validVotes = voteRequestResponses.filter(entry => {
      val node: String = entry._1
      val grantVoteSigned: GrantVoteSigned = entry._2
      string_verify(state.publicKeyStorage(node), grantVoteSigned.vote.toString, grantVoteSigned.signature)
    })

    validVotes.count(entry => entry._2.granted) >= state.majority && term > state.term
  }

  /**
    * Raft FOLLOWER
    */
  def followerBehavior: Receive = {

    case GrantVoteSigned => //Ignore message

    case InitiateLeaderElection =>

      changeBehavior(fromBehavior = BehaviorEnum.FOLLOWER,
        toBehavior = BehaviorEnum.CANDIDATE,
        loggerMessage = "No heartbeat from leader (InitiateLeaderElection case")

    case SchedulerTrigger.ElectionTimeout =>

      changeBehavior(fromBehavior = BehaviorEnum.FOLLOWER,
        toBehavior = BehaviorEnum.CANDIDATE,
        loggerMessage = "No heartbeat from leader, ElectionTimeOut")

    case BroadcastTerm(term, voteRequestResponses) =>
      if (verify_votes(term, voteRequestResponses)) {
        log.info(s"Updated term from: ${state.term}, to new term: ${term}, got enough valid votes")
        state.term = term

        restartElectionTimer()
      } else {
        log.info(s"Node ${sender().path.name} says it is the new leader, but not enough votes are valid or granted, or term was lower")
        changeBehavior(fromBehavior = BehaviorEnum.FOLLOWER,
          toBehavior = BehaviorEnum.CANDIDATE,
          loggerMessage = "Leader did not get enough valid votes")
      }


    case BroadcastKey(actorID, publicKey) =>

      state.publicKeyStorage.get(actorID) match {
        case Some(_) => // Already in the map
        case None =>
          state.publicKeyStorage += (actorID -> publicKey) // Not in the map yet, therefore add it
          log.debug(s"I received a BroadcastKey from: ${sender.path.name} and append it to my storage")
      }


    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData => sender ! IamNotTheLeader(actorName = self.path.name)

    case GetActualData =>

      log.info(s"Returning actual data: ${state.data}")
      sender ! ActualData(data = state.data)

    case GetState =>

      log.info("I have sent my state")
      sender ! MyStateIs(state)

    case Heartbeat(lastHashCode, publicKeysStorage, term) =>

      // TODO : we have to take care of the scenario when a leader crash is initiated and the leader changes behavior from leader
      //  to sleep. In this period a new leader is elected and the publicKeyStorage and term will be updated accordingly. Only, as
      //  as the node is asleep it does not receive these updates. Therefore we need to make sure that this node will become eventually
      //  consistent i.e. has for the current term the right termID and publicKeyStorage to prevent inconsistent states.
      //
      //  Do we want to send the publicKeyStorage everytime in a heartbeat? Does seem a bit cumbersome.
      //  Alternatively, we can change the behavior for a sleeping node as soon as it wakes up? But it is actually unaware who the
      //  new leader is therefore we can't ask or send IAmInconsistent to the leader. We have to wait for a heartbeat.

      log.debug(s"Got heartbeat from (${sender().path.name}), own entrylog: ${state.entryLog}")
      log.debug(s"Got heartbeat from (${sender().path.name}), own data: ${state.data}")

      // One case in which this is called is when we had a sleeper node that just woken up and is unaware of the new
      // term and key storage that were sent by the new leader.
      val hashCodeEqual = lastHashCode.equals(state.lastHashCode)
      val termEqual = this.state.term == term
      val pubKeyStorageEqual = this.state.publicKeyStorage.equals(publicKeysStorage)
//      val waitingForAERs = this.state.entryLog.count(logEntry => !logEntry.committed) > 0

      if (!hashCodeEqual || !pubKeyStorageEqual || this.state.forceIamNotConsistent || !termEqual
//        || waitingForAERs
      ) {
        this.state.forceIamNotConsistent = false
        var reason = ""
        if (!hashCodeEqual) reason += s"HashCode was not equal (was ${state.lastHashCode} expected $lastHashCode),"
        if (!termEqual) reason += s"Term was not equal(was ${term} expected ${this.state.term}), "
        if (!pubKeyStorageEqual) reason += s"PubKeyStorage was not equal(was ${this.state.publicKeyStorage.keys} expected ${publicKeysStorage.keys}), "
        if (this.state.forceIamNotConsistent) reason += s"Forcing Inconsistent to update values"
//        if (waitingForAERs) reason += s"Waiting for Append Entries Responses"
        log.info(s"I am not consistent - request data from leader (reason: $reason)")
        sender ! IamNotConsistent
      }

      restartElectionTimer()

    /**
      * AppendEntries happens here, but only to the log because we don't know if enough nodes have written it.
      */
    case OverrideData(entryLog: ArrayBuffer[LogEntry], publicKeysStorage, term) =>
      log.info(s"Received OverrideData: entryLog: $entryLog, term: $term")
      this.state.publicKeyStorage = publicKeysStorage
      this.state.term = term

      val startIndex: Int = if (state.entryLog.nonEmpty) state.entryLog.length else 0
      // Only replay log if we got new entries
      if (entryLog.nonEmpty && startIndex < entryLog.length) {
        log.debug(s"Overriding data from index $startIndex, received log len: ${entryLog.length}, " +
          s"entries: ${entryLog.toString()} \nslice: ${entryLog.slice(startIndex, entryLog.length)}")
        // Replay all entries from latest index we have logged upwards
        entryLog.slice(startIndex, entryLog.length).foreach(entry => handleReceivedEntry(entry))
        log.debug(s"finished replaying, own log: ${state.entryLog.toString()}")
        log.info(s"Follower is writing data (newHashCode = ${state.entryLog.last.hash})")
      }

    case RequestVote(term) =>

      val heartBeatPeriodPassed = state.lastHeartBeatTimestamp < (System.currentTimeMillis() - electionTimeout)
      log.info(s"Incoming VR from: ${sender.path.name}, state.alreadyVoted should be false," +
        s"is ${state.alreadyVoted}, heartBeatPeriodPassed=$heartBeatPeriodPassed +, state.electionTimer.isDefined " +
        s"should be true, is ${state.electionTimer.isDefined}, received term is $term, own term is ${state.term}")

      if (!state.alreadyVoted && term > state.term && heartBeatPeriodPassed) {
        log.info(s"I voted for actor: ${sender.path.name}")
        // We send the along with the signature, also the public key to the leader so that
        // he can store all the public keys of all the actors that are currently participating
        // in this term.
        log.info(s"Granting vote to ${sender().path.name}")
        val vote = Vote(granted = true, from = this.self.path.name, to = sender().path.name, term = term)
        sender ! GrantVoteSigned(string_sign(this.state.privateKey, vote.toString), granted = true, vote)
        state.alreadyVoted = true
      } else {
        log.info(s"I did not vote granted for actor: ${sender.path.name}")
        val vote = Vote(granted = false, from = this.self.path.name, to = sender().path.name, term = state.term + 1)
        sender ! GrantVoteSigned(string_sign(this.state.privateKey, vote.toString), granted = false, vote)
      }

    case appendEntriesResponse: AppendEntriesResponse =>
      handleAppendEntriesResponse(appendEntriesResponse, sender().path.name)


    case any: Any =>

      log.warning(s"Got unhandled message in followerBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")

  }

  /**
    * Raft CANDIDATE
    */
  def candidateBehavior: Receive = {

    case GetActualData =>

      log.info(s"Returning actual data: ${state.data}")
      sender ! ActualData(data = state.data)

    case appendEntriesResponse: AppendEntriesResponse =>
      handleAppendEntriesResponse(appendEntriesResponse, sender().path.name)

    case BroadcastKey(_, _) => // Ignore this case here, a new node can not join during an election

    case GetState =>

      log.info("I have sent my state")
      sender ! MyStateIs(state)

    case SchedulerTrigger.ElectionTimeout =>

      state.term = state.term - 1

      changeBehavior(
        fromBehavior = BehaviorEnum.CANDIDATE,
        toBehavior = BehaviorEnum.FOLLOWER,
        loggerMessage = s"Not enough votes (${state.voteCounter}/${state.majority})")

    case BroadcastTerm(_, _) => //Ignore

    case Heartbeat(_, _, term) =>

    //      if (state.term < term) {
    //        state.term = state.term - 1
    //
    //        changeBehavior(
    //          fromBehavior = BehaviorEnum.CANDIDATE,
    //          toBehavior = BehaviorEnum.FOLLOWER,
    //          loggerMessage = s"Not enough votes (${state.voteCounter}/${state.majority})")
    //      }

    case RequestVote =>
      val vote = Vote(granted = false, from = this.self.path.name, to = sender().path.name, term = this.state.term + 1)
      sender ! GrantVoteSigned(string_sign(this.state.privateKey, vote.toString), granted = false, vote)

    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData => sender ! IamNotTheLeader(actorName = self.path.name)

    case grantVote: GrantVoteSigned =>
      if (grantVote.granted && string_verify(state.publicKeyStorage(sender().path.name), grantVote.vote.toString, grantVote.signature)) {
        state.voteCounter = state.voteCounter + 1

        log.debug(s"Got vote ${state.voteCounter}/${state.majority} from (${sender().path.name})")
        state.voteRequestResponses += sender().path.name -> grantVote
        if (state.voteCounter >= state.majority) {

          log.info(s"The new term is ${state.term}")

          changeBehavior(
            fromBehavior = BehaviorEnum.CANDIDATE,
            toBehavior = BehaviorEnum.LEADER,
            loggerMessage = s"Become leader - enough votes (${state.voteCounter}/${state.majority})"
          )

          // TODO : as the leader is elected we want to broadcast all the publicKeys it has received during the
          //  leader election of the nodes that have participated.


          log.info("Became leader, broadcasting term to all neighbours")
          state.neighbours.foreach({ neighbour =>
            neighbour ! BroadcastTerm(state.term, state.voteRequestResponses)
          })

        }
      }

    case any: Any =>
      log.warning(s"Got unhandled message in candidateBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")
  }

  /**
    * Raft LEADER
    */
  def leaderBehavior: Receive = {
    case msg: Any =>
      leaderMatch(msg)
  }

  /**
    * Default leader behavior
    *
    * @param msg received message
    */
  def leaderMatch(msg: Any): Unit = msg match {
    case BroadcastKey(actorID, publicKey) =>
      log.info("BroadCastKeys")
      state.publicKeyStorage.get(actorID) match {
        case Some(_) => // Already in the map
        case None => state.publicKeyStorage += (actorID -> publicKey) // Not in the map yet, therefore add it
      }

    case BroadcastTerm(term, voteRequestResponses) =>
      if (verify_votes(term, voteRequestResponses)) {
        changeBehavior(fromBehavior = BehaviorEnum.LEADER,
          toBehavior = BehaviorEnum.FOLLOWER,
          loggerMessage = "there is a new leader with higher term and majority of the votes, stepping down")
      }

    case SchedulerTrigger.Heartbeat =>

      log.info(s"My state is: ${state.behaviour}, and I am going to send a heartbeat! (counter = ${state.heartbeatCounter})")

      state.neighbours.foreach(neighbour => neighbour ! Heartbeat(lastHashCode = state.lastHashCode, state.publicKeyStorage, state.term))

      state.heartbeatCounter = state.heartbeatCounter + 1

      if (state.heartbeatCounter >= Config.crashIntervalHeartbeats) {
        changeBehavior(
          fromBehavior = BehaviorEnum.LEADER,
          toBehavior = BehaviorEnum.SLEEP,
          loggerMessage = s"Simulated test crash (crashIntervalHeartbeats) - sleep ${Config.sleepDowntime} seconds now"
        )
      }

    case BroadcastTerm => //Ignore

    case GrantVoteSigned => //Ignore message

    case GrantVote => //Ignore message

    case RequestVote => //Ignore message

    case appendEntriesResponse: AppendEntriesResponse =>
      handleAppendEntriesResponse(appendEntriesResponse, sender().path.name)

    case SimulateLeaderCrash =>

      sender ! LeaderIsSimulatingCrash(actorName = self.path.name)

      changeBehavior(
        fromBehavior = BehaviorEnum.LEADER,
        toBehavior = BehaviorEnum.SLEEP,
        loggerMessage = s"Simulated test crash (externalTrigger) - sleep ${Config.sleepDowntime} seconds now"
      )

    case WhoIsLeader =>

      sender ! IamTheLeader(actorName = self.path.name)

    case AppendData(key, value, signature) =>
      log.info(s"Appending new data in Leader Node: ($key -> $value)")

      // Create the hash and find the relevant public key
      val newHash = createIncrementalHash(key, value, state.lastHashCode)
      val publicKey = state.publicKeyStorage(CLIENT_NAME)

      // Check if the signature is correct with verify
      if (!string_verify(publicKey, s"$key,$value", signature)) {
        log.warning("Signature from client was invalid. Ignoring this message")
        sender ! WriteResponse(actorName = self.path.name, success = false, reason = "Signature from client was Invalid")
      } else {
        sender ! WriteResponse(actorName = self.path.name, success = true, "Write successful in leader")
        handleReceivedEntry(LogEntry(key, value, newHash, signature))
        log.info(s"[SEND APR], ${System.currentTimeMillis()}, (key/value = $key->$value), (newHashCode = ${state.lastHashCode}), Leader is writing data")

        // AppendEntriesResponse:
        // TODO what to do here, entry is not committed/replicated yet
        //      sender ! WriteSuccess(actorName = self.path.name)
      }

    case GetActualData =>

      log.info(s"Returning actual data: ${state.data}")
      sender ! ActualData(data = state.data)

    case GetState =>

      log.info("I have sent my state")
      sender ! MyStateIs(state)

    case BroadcastKey(actorID, publicKey) =>

      // First add the public key of the new node in the map, if it isn't already. Of course
      // it is not always the case that an inconsistent node is a new node (such as a sleeper)

      // Check if the key is already in the store, if not we add it and broadcast the new key to all the followers as a heartbeat

      state.publicKeyStorage.get(actorID) match {
        case Some(_) => // Hooray we already have this actor in our storage
        case None => // This actor is unknown to us as of now, we are broadcasting the new key

          state.publicKeyStorage += (actorID -> publicKey) // Not in the map yet, therefore add it
          log.debug(s"I received a BroadcastKey from: ${sender.path.name} and append it to my storage")

          val followers = state.neighbours.filter(neighbour => neighbour != sender)

          followers.foreach(follower => {
            follower ! BroadcastKey(actorID, publicKey)
          })
      }

    case IamNotConsistent =>
      // AppendEntries
      log.info(s"Leader received IamNotConsistent from ${sender().path.name}")
      sender ! OverrideData(data = state.entryLog, publicKeysStorage = this.state.publicKeyStorage, term = this.state.term)

    case Heartbeat(_, _, _) =>
      if (sender().path.name != this.self.path.name) {
        log.debug(s"Received heartbeat from ${sender().path.name}")
      }

    case BecomeByzantine =>
      changeBehavior(
        fromBehavior = BehaviorEnum.LEADER,
        toBehavior = BehaviorEnum.BYZANTINELEADER,
        loggerMessage = s"Received BecomeByzantine RPC, transitioning to Byzantine Leader"
      )

    case any: Any =>
      log.warning(s"Got unhandled message in leaderBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")
  }

  /**
    * Byzantine leader behavior
    *
    * @param msg received message
    */
  def byzantineLeaderMatch(msg: Any): Unit = msg match {

    case AppendData(key, value, signature) =>
      log.info(s"Appending new data in Byzantine Leader Node: ($key -> $value)")

      // Create the hash and find the relevant public key
      val newHash = createIncrementalHash(key, value, state.lastHashCode)
      val publicKey = state.publicKeyStorage(CLIENT_NAME)

      handleReceivedEntry(LogEntry(key, value, newHash, signature))
      log.info(s"Byzantine Leader is writing data ($key->$value) (newHashCode = ${newHash})\n " +
        s"Waiting for enough nodes to write before committing")

      // AppendEntriesResponse:
      // TODO what to do here, entry is not committed/replicated yet
      //      sender ! WriteSuccess(actorName = self.path.name)
      sender ! WriteResponse(actorName = self.path.name, success = true, "Write successful in leader")

    case any =>
      leaderMatch(any)
  }

  /**
    * Byzantine Raft LEADER
    */
  def byzantineLeaderBehavior: Receive = {
    case msg: Any =>
      log.debug("ByzantineBehavior")
      byzantineLeaderMatch(msg)
  }

  /**
    * Sleep behavior
    */
  def sleepBehavior: Receive = {

    case GetActualData =>

      log.info(s"Returning actual data: ${state.data}")
      sender ! ActualData(data = state.data)

    case SchedulerTrigger.Awake =>

      changeBehavior(fromBehavior = BehaviorEnum.SLEEP,
        toBehavior = BehaviorEnum.FOLLOWER,
        loggerMessage = s"Awake after ${Config.sleepDowntime} seconds downtime")

    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData => sender ! IamNotTheLeader(actorName = self.path.name)

    case appendEntriesResponse: AppendEntriesResponse =>
      handleAppendEntriesResponse(appendEntriesResponse, sender.path.name)

  }

  /**
    * Store a newly received entry until we are confident more
    * than half the nodes have written it.
    */
  private def handleReceivedEntry(logEntry: LogEntry): Unit = {
    log.info(s"Entry received: $logEntry")
    if (addEntryToLog(logEntry)) {
      val idx = state.entryLog.length - 1
      val s = this.state.appendEntryResponseMap.getOrElse(idx, Set.empty)
      this.state.appendEntryResponseMap += (idx -> s)

      // Broadcast AppendEntriesResponse to all neighbours
      val appendEntriesResponse = AppendEntriesResponse(idx, state.entryLog.last.hash)
      appendEntriesResponse.sign(state.privateKey)

      log.debug(s"Signing entry at ${appendEntriesResponse.index} with hash ${appendEntriesResponse.hash}, " +
        s"pubkey: ${state.publicKey}, private key: ${state.privateKey}: " +
        s"signature: ${appendEntriesResponse.signature}")

      this.state.neighbours.foreach(node => node ! appendEntriesResponse)
    } else {
      // TODO inconsistent hash, but how to handle?
    }
  }

  /**
    * Handle an AppendEntriesResponse received from another node. This message states
    * that the sender has written the entry to their log.
    *
    * @param appendEntriesResponse Message sent by the node to notify the cluster that it has written an entry
    * @param senderName            Name of the node that sent this AppendEntriesResponse
    */
  private def handleAppendEntriesResponse(appendEntriesResponse: AppendEntriesResponse, senderName: String): Unit = {
    // verify signature
    val index = appendEntriesResponse.index
    val hash = appendEntriesResponse.hash
    val signature = appendEntriesResponse.signature
    // We only care about this message if
    //  1. we have not received the message to write this entry
    //  2. or we have not committed this entry ourselves.
    if (state.entryLog.length <= index || (!state.entryLog(index).committed)) {
      if (string_verify(state.publicKeyStorage(senderName), appendEntriesResponse.toString, signature)) {

        log.debug(s"Signature verified, updating uncommittedEntries at index $index to include $senderName")
        var newCommittedNeighboursSet: Set[String] = state.appendEntryResponseMap.getOrElse(index, Set.empty)
        newCommittedNeighboursSet += senderName
        state.appendEntryResponseMap += (index -> newCommittedNeighboursSet)

        // Check if we need to write an uncommitted entry to data
        if (newCommittedNeighboursSet.size >= state.majority && this.state.entryLog.size > index && hash == this.state.entryLog(index).hash) {
          // Check if we have 'old' entries that also need to be committed (as they inductively got enough
          // append entries responses)
          val indicesToCommit = Range(0, index+1).filter(i => state.entryLog.size > i && !state.entryLog(i).committed)
          log.info(s"enough nodes committed entry/entries at index $indicesToCommit, I am also committing this entry")

          indicesToCommit.foreach(f = commitEntry)
        }
      } else {
        log.warning(s"Signature from $senderName was invalid, ignoring msg")
        log.debug(s"AppendEntriesResponse at $index from $senderName, verifiedMessage = false, " +
          s"pubkey of sender:\t ${state.publicKeyStorage(senderName)}, with msg: \t${appendEntriesResponse.toString}, " +
          s"signature:\t $signature")
      }
    } else {
      log.debug(s"Entry at $index already committed")
    }
  }

  /**
    * Write a LogEntry from entryLog to state.data.
    * Called when a majority of nodes has written an entry
    *
    * @param entryLogIndex Index of the entry to write.
    */
  private def commitEntry(entryLogIndex: Int): Unit = {
    if (state.entryLog.length <= entryLogIndex) {
      // inconsistent, we don't have access to the leader here so we wait for
      // a heartbeat and then respond with IamNotConsistent.
      log.warning("Trying to commit log which is not in my entryLog, forcing IamNotConsistent")
      this.state.forceIamNotConsistent = true
    } else {
      val entry = state.entryLog(entryLogIndex)
      state.data += (entry.key -> entry.value)
      log.info(s"[FOLLOWER WRITING DATA], ${System.currentTimeMillis()}, (data = ${state.data}), (newHashCode = ${state.lastHashCode}), The follower is writing the new data")
      entry.committed = true
    }
  }

  private def clientEntryToString(key: String, value: String) = {
    s"$key,$value"
  }

  /**
    * Write a LogEntry to the entryLog, also check the hash to verify the order.
    *
    * @param logEntry to write
    * @return whether the write was successful
    */
  private def addEntryToLog(logEntry: LogEntry): Boolean = {
    val (key, value, expectedHash, clientSignature) = (logEntry.key, logEntry.value, logEntry.hash, logEntry.clientSignature)

    // Verify this message actually came from the client
    if (string_verify(state.publicKeyStorage(CLIENT_NAME), clientEntryToString(key, value), clientSignature)) {

      log.debug(s"addEntryToLog(key=$key,value=$value), prevhash=${state.lastHashCode}")
      val hashCode = createIncrementalHash(key, value, state.lastHashCode)
      if (hashCode == expectedHash) {
        this.state.lastHashCode = hashCode
        state.entryLog.addOne(LogEntry(key, value, hashCode, clientSignature))
        log.debug(s"addEntryToLog finished successfuly, new hash=${state.lastHashCode}")
        true
      } else {
        log.debug(s"Inconsistent hash in addEntryToLog, own computed hash=\n:$hashCode, but received hash was:" +
          s"\n:$expectedHash, \n:${hashCode - expectedHash}")
        false
      }
    } else {
      log.warning(s"Received LogEntry with invalid client signature (was $clientSignature), discarding")
      false
    }
  }

  /**
    * Create the incremental hash used to verify data lineage.
    * It's composed of the key and value of the current entry, and the hash of the
    * previous entry. This ensures that if two nodes have equal hashes
    * at index i, then all entries before index i were equal.
    *
    * @param key      key
    * @param value    value
    * @param lastHash previous  incremental hash
    * @return hash
    */
  private def createIncrementalHash(key: String, value: String, lastHash: BigInt): BigInt = {
    val hashstr = s"key=$key value=$value lasthash=$lastHash"
    log.debug(s"Hashing: $hashstr")
    val hash = this.state.hasher.hash(hashstr)
    log.debug(s"create Incremental Hash: $hash")
    hash
  }

  /**
    * Change actor behavior
    *
    * @param fromBehavior  Behavior
    * @param toBehavior    Behavior
    * @param loggerMessage String (logging)
    */
  private def changeBehavior(fromBehavior: BehaviorEnum,
                             toBehavior: BehaviorEnum,
                             loggerMessage: String): Unit = {

    log.info(s"Change behavior from '$fromBehavior' to '$toBehavior' ($loggerMessage)")

    /**
      * Before change of behavior
      */
    val newBehavior: Receive = toBehavior match {

      case BehaviorEnum.FOLLOWER =>
        restartElectionTimer()
        stopHeartbeatTimer()
        followerBehavior

      case BehaviorEnum.CANDIDATE =>
        restartElectionTimer()
        stopHeartbeatTimer()
        candidateBehavior

      case BehaviorEnum.LEADER =>
        stopElectionTimer()
        restartHeartbeatTimer()
        leaderBehavior

      case BehaviorEnum.BYZANTINELEADER =>
        stopElectionTimer()
        restartHeartbeatTimer()
        byzantineLeaderBehavior

      case BehaviorEnum.SLEEP =>
        stopElectionTimer()
        stopHeartbeatTimer()
        sleepBehavior

      case _ =>
        stopElectionTimer()
        stopHeartbeatTimer()
        receive

    }

    /**
      * Change of behavior
      */
    context.become(newBehavior)

    /**
      * After change of behavior
      */
    toBehavior match {

      case BehaviorEnum.FOLLOWER =>

        state.alreadyVoted = false
        state.behaviour = BehaviorEnum.FOLLOWER

      case BehaviorEnum.CANDIDATE =>

        state.voteCounter = 0

        // Send a VR to all neighbours and itself with increased termID
        // If the candidate becomes a leader the term should increased by one, as the leader election has ended
        // Also the followers should be informed about this new term
        state.term = state.term + 1
        state.neighbours.foreach(neighbour => neighbour ! RequestVote(state.term))
        val vote = Vote(granted = true, this.self.path.name, this.self.path.name, state.term)
        self ! GrantVoteSigned(string_sign(this.state.privateKey, vote.toString), granted = true, vote)

        state.behaviour = BehaviorEnum.CANDIDATE

      case BehaviorEnum.LEADER =>

        state.heartbeatCounter = 0
        state.behaviour = BehaviorEnum.LEADER

      case BehaviorEnum.BYZANTINELEADER =>
        log.info("Becoming Byzantine Leader")
        state.heartbeatCounter = 0
        state.behaviour = BehaviorEnum.BYZANTINELEADER

      case BehaviorEnum.SLEEP =>

        scheduleAwake()
        state.behaviour = BehaviorEnum.SLEEP

      case _ =>

        state.behaviour = BehaviorEnum.UNINITIALIZED
    }

  }
}