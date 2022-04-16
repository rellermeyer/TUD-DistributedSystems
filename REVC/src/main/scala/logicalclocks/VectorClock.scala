package logicalclocks

/** The timestamp of VectorClock is represented by 
 *  a list of integers, where each integer corresponds
 *  to a vector clock instance.
 */
class VCTimestamp(vector: List[Int]) extends LCTimestamp  {
    def getVector() : List[Int] = {
        return vector
    }
}

/** VectorClock is an implementation of the vector clock.
 */
class VectorClock(me: Int, n: Int) extends LogicalClock {

    /** Current clock value.
     */
    protected val vector = Array.fill(n)(0)

    /** Returns a timestamp of the current state.
     * 
     *  @param receiver the receiver of the timestamp, which 
     *  is not relevant for the VectorClock implementation
     *  @return a copy of the current vector clock list
     */
    override def getTimestamp(receiver: Int = -1) : LCTimestamp = {
        return new VCTimestamp(vector.clone.toList)
    }
    
    /** Increments the value of this clock in the 
     *  vector clock list.
     */
    override def localTick(): Unit = {
        vector(me) = vector(me) + 1
    }

    /** Updates the vector clock list by merging timestamp 
     *  x into the clock.
     * 
     *  @param x the timestamp to be merged into the clock
     */ 
    override def mergeWith(mergeTimestamp: LCTimestamp) : Unit = {
        val mergeVector = mergeTimestamp.asInstanceOf[VCTimestamp].getVector()

        for (i <- 0 until n) {
            // Merge vectors component-wise
            vector(i) = vector(i).max(mergeVector(i))
        }
    }

    /** Converts the current vector clock value to a string.
     *  @return a string representation of the current vector clock value
     */ 
    override def toString: String = {
        return me + ": " + vector.mkString("(", ", ", ")")
    }

    /** Returns the number of bits needed to represent the timestamp of the clock.
     * 
     *  @return the number of bits needed to represent the timestamp of this clock
     */ 
    override def getSizeBits: Int = {
        return vector.length * 32
    }
}

/** VectorClock is the companion object of VectorClock that defines 
 *  the comparison functionality of the vector clock. 
 */
object VectorClock extends LogicalClockComparator {
    /** Returns whether timestamp1 is logically before, 
     *  beforeEqual or equal to timestamp2. 
     * 
     *  @param timestamp1 the first timestamp to be compared
     *  @param timestamp2 the second timestamp to be compared 
     *  @return whether the timestamp1 happened before timestamp 2
     */ 
    override def happenedBefore(timestamp1: LCTimestamp, timestamp2 : LCTimestamp) : Boolean = {
        val vector1 = timestamp1.asInstanceOf[VCTimestamp].getVector()
        val vector2 = timestamp1.asInstanceOf[VCTimestamp].getVector()
        for (i <- 0 until vector2.length) {
            if (vector2(i) > vector1(i)) {
                return false
            }
        }

        return true
    }
}