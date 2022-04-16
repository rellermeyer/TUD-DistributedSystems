package logicalclocks

/** LogicalClock is the trait that defines the functions 
 *  that a logical clock must have. 
 */
trait LogicalClock {
    /** Returns an immutable deep-copy of the current state
     *  in the form of LCTimestampRepr to be used for comparing 
     *  and merging with other clocks. 
     *  
     *  @param receiver the receiver of the timestamp, which 
     *  might be necessary for some logical clocks
     *  @return an immutable deep-copy of the current state
     */
    def getTimestamp(receiver: Int = -1) : LCTimestamp

    /** Increments the value of the clock.
     */
    def localTick(): Unit

    /** Updates the clock by merging timestamp x into the clock.
     * 
     *  @param mergeTimestamp the timestamp to be merged into the clock
     */ 
    def mergeWith(mergeTimestamp: LCTimestamp): Unit

    /** Returns the number of bits needed to represent the timestamp of the clock.
     *   
     *  @return the number of bits needed to represent the timestamp of this clock
     */ 
    def getSizeBits: Int
}

/** LogicalClockComparator is the trait that defines the functions 
 *  that the companion object of a logical clock must have. 
 *  In this case, a function to compare timestamps.
 */
trait LogicalClockComparator {
    /** Returns whether the current clock value is logically before, 
     *  beforeEqual or equal to the passed timestamp compareTimestamp. 
     * 
     *  @param timestamp1 the first timestamp to be compared
     *  @param timestamp2 the second timestamp to be compared 
     *  @return whether the timestamp1 happened before timestamp2
     */ 
    def happenedBefore(timestamp1: LCTimestamp, timestamp2: LCTimestamp): Boolean
}
