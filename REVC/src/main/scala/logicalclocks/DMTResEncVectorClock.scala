package logicalclocks

import scala.collection.mutable.Set 
import scala.collection.mutable.Buffer

/** The timestamp of DMTResEncVectorClock is represented by 
 *  an DMTREVCTimestamp, which consists of a BigInt for the 
 *  scalar, a integer for the frame number, a mapping 
 *  from integers to BigInts for the frame history, 
 *  and a list of integers representing the frame numbers that 
 *  have been changed since the last merge with the clock 
 *  for which the timestamp has been made. 
 */
class DMTREVCTimestamp(scalar: BigInt, frame: Int, frameHistory: Map[Int, BigInt], differences: Set[Int]) 
        extends REVCTimestamp(scalar, frame, frameHistory) {

    def getDifferences() : Set[Int] = {
        return differences
    }
}

/** DMTResEncVectorClock is an implementation of the differential 
 *  merge resettable encoded vector clock. (See 
 *  https://ieeexplore.ieee.org/stamp/stamp.jsp?tp=&arnumber=9234035)
 */
class DMTResEncVectorClock(me: Int, n : Int) extends ResEncVectorClock(me, n) {

    /** Keep track of latest merged frame from each of the other clocks.
     */
    protected val differencesPerPeer = scala.collection.mutable.Buffer.fill(n)(Set[Int]())

    /** Returns a timestamp of the current state.
     *  Note: if we get a timestamp for a particular receiver
     *  we assume the timestamp will be merged into the receiver, 
     *  so we empty the list in differencesPerPeer for the receiver.
     *  
     *  @param receiver the receiver of the timestamp, where -1 represents no receiver
     *  @return a DMTREVCTimestamp of the current clock state personalized for the receiver
     */
    override def getTimestamp(receiver: Int = -1): LCTimestamp = {
        // Copy differences to immutable map

        val differences = if (receiver != -1) differencesPerPeer(receiver).clone else Set[Int]()
        
        if (receiver != -1) {
            // Reset differencesPerPeer for the receiver
            differencesPerPeer(receiver) = Set[Int]() 
        }

        // Get timestamp created by REVC parent
        val revcTimestamp = super.getTimestamp(receiver).asInstanceOf[REVCTimestamp]

        // Create and return new timestamp
        return new DMTREVCTimestamp(revcTimestamp.getScalar(), revcTimestamp.getFrame(),
            revcTimestamp.getFrameHistory(), differences)
    }

    /** Updates the frame history by merging the timestamp
     *  into the clock.
     *  
     *  Note that we are reusing REVC.mergeWith as a template function, 
     *  so we only have to implement historyMerge.
     *  
     *  @param otherTimestamp the timestamp to be merged into the clock. 
     */ 
    override def historyMerge(otherTimestamp : REVCTimestamp) : Unit = {
        val otherDMTTimestamp : DMTREVCTimestamp = otherTimestamp.asInstanceOf[DMTREVCTimestamp]
        val otherFrameHistory = otherDMTTimestamp.getFrameHistory()
        val otherDifferences = otherDMTTimestamp.getDifferences()

        // Differential merge: only loop through changed frames
        for (tempFrame <- otherDifferences) {
            val tempScalar = otherFrameHistory.getOrElse(tempFrame, BigInt(-1))
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
     *  Also keeps track of what parts of its history have changed 
     *  for each other DMTREVC since the last respective merge. 
     *  Note that this implementation is quite inefficient, but 
     *  it adheres exactly to the description in the paper that 
     *  presents the DMTREVC. 
     * 
     *  @param frameIndex the key for the new frameHistory entry 
     *  @param value the value for the new frameHistory entry
     */
    override protected def addToFrameHistory(frameIndex : Int, value : BigInt) : Unit = {
        val oldValue = frameHistory.getOrElse(frameIndex, -1)
        super.addToFrameHistory(frameIndex, value)
        val newValue = frameHistory.getOrElse(frameIndex, -1)

        if (oldValue != newValue) {
            // Update differencesPerPeer map 
            for (i <- 0 until n) {
                var otherSet = differencesPerPeer(i)
                otherSet += frameIndex
                differencesPerPeer(i) = otherSet
            }
        } 
    }

    /** Returns the number of bits needed to represent the timestamp of the clock. 
     *  
     *  Note that in the REVC we use the scalar in the EVC, with an additional frame 
     *  and frameHistory defined in the REVC. In the DMTREVC we add the differencesPerPeer map. 
     *  Hence, we use the getSizeBits of the REVC, and add it to the number of bits needed
     *  to represent the differencesPerPeer map.
     * 
     *  @return the number of bits needed to represent the timestamp of this clock
     */ 
    override def getSizeBits: Int = {
        val revcSize = super.getSizeBits

        // the size of a list is 32 bits, plus the number of elements * 32 bits
        val diffsSize = differencesPerPeer.map{ case (set) => 32 + set.size * 32 }.sum / n

        return revcSize + diffsSize
    }
}