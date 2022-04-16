import logicalclocks.{LCTimestamp, LogicalClockComparator, DMTResEncVectorClock, EncVectorClock, LogicalClock, ResEncVectorClock, VectorClock}

/**
 * A class that wraps all the clocks of a ChildActor, to collectively perform clock operations on them.
 * @param index the index of the ChildActor using this ClocksWrapper
 * @param numPeers number of total ChildActors in the system
 * @param selectedClocks a list specifying which types of clocks will be used
 */
class ClocksWrapper(index: Int, numPeers: Int, selectedClocks: List[String]) {
    var clocks: List[(LogicalClockComparator, LogicalClock)] = List()

    // Create respective clock from the selectedClocks list
    if (selectedClocks.contains("VC"))
        clocks = clocks:+(VectorClock, new VectorClock(index, numPeers))
    if (selectedClocks.contains("EVC"))
        clocks = clocks:+(EncVectorClock, new EncVectorClock(index, numPeers))
    if (selectedClocks.contains("REVC"))
        clocks = clocks:+(ResEncVectorClock, new ResEncVectorClock(index, numPeers))
    if (selectedClocks.contains("DMTREVC"))
        clocks = clocks:+(ResEncVectorClock, new DMTResEncVectorClock(index, numPeers))

    /**
     * Tick the clocks
     */
    def tick(): Unit = {
        clocks.foreach{ case (_, clock) => clock.localTick() }
    }

    /**
     * Get timestamps for the clocks
     * @param receiver the index of the receiving peer (needed by the DMTREVC)
     * @return the timestamps
     */
    def getTimestamps(receiver: Int): List[LCTimestamp] = {
        return clocks.map{ case (_, clock) => clock.getTimestamp(receiver) }.toList
    }

    /**
     * Merge the clocks
     * @param timestamps of the clocks to be merged
     */
    def merge(timestamps: List[LCTimestamp]): Unit = {
        clocks.zip(timestamps).foreach{ case ((_, clock), timestamp) => clock.mergeWith(timestamp) }
    }

    /**
     * Check that all the clocks are consistent
     * @param timestamps the timestamps of the clocks
     * @return true if all the happened-before relations checks are consistent (all true or all false), else false
     */
    def allConsistent(timestamps: List[LCTimestamp]): Boolean = {
        val checks = clocks.zip(timestamps).map{ case ((comparator, clock), timestamp) => comparator.happenedBefore(clock.getTimestamp(), timestamp) }.toList
        val all_true = checks.reduce((i, j) => i && j)
        val all_false = ! checks.reduce((i, j) => i || j)
        return all_true || all_false
    }

    /**
     * @return sizes that the clocks take up in bits
     */
    def getMemSizes: List[Int] = {
        return clocks.map{ case (_, clock) => clock.getSizeBits }.toList
    }
}
