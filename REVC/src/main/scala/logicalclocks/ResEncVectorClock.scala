package logicalclocks

/** The timestamp of ResEncVectorClock is represented by 
 *  an REVCTimestamp, which consists of a BigInt for the 
 *  scalar, a integer for the frame number, and a mapping 
 *  from integers to BigIntegers for the frame history.
 */
class REVCTimestamp(scalar: BigInt, frame: Int, frameHistory: Map[Int, BigInt]) 
        extends EVCTimestamp(scalar) {

    def getFrame() : Int = {
        return frame
    }

    def getFrameHistory() : Map[Int, BigInt] = {
        return frameHistory
    }
}

/** ResEncVectorClock is an implementation of the resettable 
 *  encoded vector clock. (See 
 *  https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=9234035)
 */
class ResEncVectorClock(me: Int, n : Int) extends EncVectorClock(me, n) {

    /** Additional variables to represent the current clock value.
     *  We inherit the scalar from the EVC implementation.
     */
    protected var frame : Int = 0 
    protected val frameHistory = scala.collection.mutable.Map[Int, BigInt]()

    /** Max size for scalar to indicate overflows.
     *  The minimum value for this number is 
     */
    protected val bitsToOverflow : Int = 16 

    /** Returns a timestamp of the current state.
     *  Note: we have to copy frameHistory to avoid 
     *  referencing issues.
     *  
     *  @param receiver the receiver of the timestamp, which 
     *  is not relevant for the REVC implementation
     *  @return the REVCTimestamp of the current clock state
     */
    override def getTimestamp(receiver: Int = -1): LCTimestamp = {
        // Copy frameHistory to an immutable Map
        var frameHistoryCopy = Map[Int, BigInt](frameHistory.toSeq: _*)

        // Create and return new timestamp
        return new REVCTimestamp(scalar, frame, frameHistoryCopy)
    }

    /** Increments the value of this clock in the 
     *  scalar by multiplying by the assigned prime.
     *  In case this multiplication causes an overflow, 
     *  we reset the scalar. 
     */
    override def localTick(): Unit = {
        val temp = scalar * myPrime
        if (causesOverflow(temp)) {
            reset(myPrime)
        } else {
            scalar = temp
        }
    }

    /** Updates the scalar, frame and frameHistory by 
     *  merging the timestamp into the clock.
     * 
     *  @param mergeTimestamp the timestamp to be merged into the clock
     */ 
    override def mergeWith(mergeTimestamp: LCTimestamp) : Unit = {
        val mergeREVCTimestamp : REVCTimestamp = mergeTimestamp.asInstanceOf[REVCTimestamp]
        scalarFrameMerge(mergeREVCTimestamp)
        historyMerge(mergeREVCTimestamp)
    }

    /** Updates the scalar and frame by merging the timestamp
     *  into the clock.
     * 
     *  @param otherTimestamp the timestamp to be merged into the clock
     */ 
    protected def scalarFrameMerge(otherTimestamp : REVCTimestamp) : Unit = {
        val otherScalar = otherTimestamp.getScalar() 
        val otherFrame = otherTimestamp.getFrame()

        if (frame > otherFrame) {
            addToFrameHistory(otherFrame, getLCM(frameHistory.getOrElse(otherFrame, 0), otherScalar))
        } else if (otherFrame > frame) {
            addToFrameHistory(frame, scalar)
            frame = otherFrame
            scalar = otherScalar
        } else {
            val temp = getLCM(scalar, otherScalar)
            if (causesOverflow(temp)) {
                reset(1)
            } else {
                scalar = temp
            }
        }
    }
    
    /** Updates the frame history by merging the timestamp
     *  into the clock.
     * 
     *  @param otherTimestamp the timestamp to be merged into the clock
     */ 
    protected def historyMerge(otherTimestamp : REVCTimestamp) : Unit = {
        val otherFrameHistory = otherTimestamp.getFrameHistory()

        for ((tempFrame, tempScalar) <- otherFrameHistory) {
            if (frameHistory.contains(tempFrame)) {
                // Update tempFrame value in frameHistory
                addToFrameHistory(tempFrame, getLCM(frameHistory.getOrElse(tempFrame, 0), tempScalar))
            } else {
                // Add tempFrame to frameHistory
                addToFrameHistory(tempFrame, tempScalar)
            }
        }
    }

    /** Adds the frameIndex and value to the frameHistory map.
     * 
     *  @param frameIndex the key for the new frameHistory entry 
     *  @param value the value for the new frameHistory entry
     */
    protected def addToFrameHistory(frameIndex: Int, value : BigInt): Unit = {
        frameHistory += (frameIndex -> value)
    }

    /** Returns whether the number of bits needed to represent 
     *  the passed scalar value exceeds the predefined maximum 
     *  amount of bits (bitsToOverflow).
     * 
     *  @param toCheck the scalar value to check for overflow
     *  @return true if toCheck needs more than bitsToOverflow bits
     */
    protected def causesOverflow(toCheck: BigInt) : Boolean = {
        // Returns whether toCheck causes an overflow
        return (toCheck.bitLength > bitsToOverflow)
    }
    
    /** Resets the REVC.
     * 
     *  The current scalar gets moved to the frame history, the frame 
     *  number gets incremented, and the passed scalar becomes the 
     *  new current scalar.
     * 
     *  @param newScalarValue the starting value for the new scalar
     */ 
    protected def reset(newScalarValue: BigInt) : Unit = {
        // Resets current representation of REVC 
        addToFrameHistory(frame, scalar)
        frame += 1 
        scalar = newScalarValue
    }

    /** Returns the number of bits needed to represent the timestamp of the clock. 
     *  
     *  Note that in the REVC we use the scalar in the EVC, with an additional frame 
     *  and frameHistory defined in the REVC. Hence, we use the getSizeBits of the 
     *  EVC, and add it to the number of bits needed.
     * 
     *  @return the number of bits needed to represent the timestamp of this clock
     */ 
    override def getSizeBits: Int = {
        val evcSize = super.getSizeBits
        val frameSize = 32
        val frameHistSize = frameHistory.map{ case (_, bigint) => 32 + bigint.bitLength }.sum
        return evcSize + frameSize + frameHistSize
    }
}


/** ResEncVectorClock is the companion object of ResEncVectorClock that defines 
 *  the comparison functionality of the resettable encoded vector clock. 
 */
object ResEncVectorClock extends LogicalClockComparator {
    /** Returns whether timestamp1 is logically before, 
     *  beforeEqual or equal to timestamp2. 
     * 
     *  @param timestamp1 the first timestamp to be compared
     *  @param timestamp2 the second timestamp to be compared 
     *  @return whether the timestamp1 happened before timestamp 2
     */ 
    def happenedBefore(timestamp1: LCTimestamp, timestamp2: LCTimestamp) : Boolean = {
        val revcTimestamp1 : REVCTimestamp = timestamp1.asInstanceOf[REVCTimestamp]
        val revcTimestamp2 : REVCTimestamp = timestamp2.asInstanceOf[REVCTimestamp]

        // Compare frames
        if (revcTimestamp1.getFrame() > revcTimestamp2.getFrame()) {
            return false 
        } else {
            // Compare scalars
            val scalarToCompare = revcTimestamp2.getFrameHistory().getOrElse(revcTimestamp1.getFrame(), revcTimestamp2.getScalar())
            return (revcTimestamp1.getScalar() <= scalarToCompare && scalarToCompare % revcTimestamp1.getScalar() == 0)
        }
    }
}