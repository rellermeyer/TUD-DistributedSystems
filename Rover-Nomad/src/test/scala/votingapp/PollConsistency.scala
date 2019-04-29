package votingapp

import rover.rdo.conflict.{CommonAncestor, ConflictedState, DiffWithAncestor}

object testPollConsistency {
  def main(args: Array[String]): Unit = {
    val poll = Poll("Does this work", List(PollChoice("Yes"), PollChoice("No"), PollChoice("I hope so"), PollChoice("Yes")))

    /* Common ancestor state: 1 no vote */
    poll.cast(PollChoice("No"))
    println(s"Common ancestor: $poll \n\n")


    val poll2 = Poll.copyOf(poll)

    /* Vote in Poll 1 */
    poll.cast(PollChoice("Yes"))
    poll.cast(PollChoice("Yes"))
    poll.cast(PollChoice("No"))

    /* Vote in Poll 2 */
    poll2.cast(PollChoice("No"))
    poll2.cast(PollChoice("No"))


    println("Poll:" + poll + "\n\n")
    println("Poll2:" + poll2 + "\n\n")

    val ancestor = CommonAncestor.from(poll, poll2)
    val ancestorState = ancestor.state
    println("Ancestor:" + ancestor.toString)

    val diffPoll1vsCommon = new DiffWithAncestor[Votes](poll.state, ancestorState)
    println("Diff poll1 and common ancestor: " + diffPoll1vsCommon.toString)

    val diffPoll2vsCommon = new DiffWithAncestor[Votes](poll2.state, ancestorState)
    println("Diff poll2 and common ancestor: " + diffPoll2vsCommon.toString)

    val pollMergeConflictResolutionMechanism = new PollAppMergeConflictResolutionMechanism()
    val benchInit = System.nanoTime()
    val resolved = pollMergeConflictResolutionMechanism.resolveConflict(ConflictedState.from(poll, poll2))
    val benchDuration = System.nanoTime() - benchInit

    println(s"\n\nResolved: $resolved")
    print(s"Time elapsed for resolving: $benchDuration")

    println(s"\n    log of resolved: ${resolved.asAtomicObjectState.log.asList.mkString("\n     ")}")
  }
}