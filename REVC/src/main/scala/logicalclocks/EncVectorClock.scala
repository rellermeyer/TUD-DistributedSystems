package logicalclocks

/** The timestamp of EncVectorClock is represented by 
 *  a Big Integer. 
 */
class EVCTimestamp(scalar: BigInt) extends LCTimestamp  {
    def getScalar() : BigInt = {
        return scalar
    }
}

/** EncVectorClock is an implementation of the encoded 
 *  vector clock. (See 
 *  https://dl.acm.org/doi/abs/10.1145/3154273.3154305)
 */
class EncVectorClock(me: Int, n : Int) extends LogicalClock {
    
    /** The unique prime given to this instance of 
     *  EncVectorClock.
     */
    protected val myPrime : Int = Primes.getPrime(me)

    /** Current clock value.
     */
    protected var scalar : BigInt = 1

    /** Returns a timestamp of the current state.
     *  Note: scalar is passed by value, so we 
     *  can not encounter referencing issues.
     * 
     *  @param receiver the receiver of the timestamp, which 
     *  is not relevant for the EVC implementation
     *  @return the current scalar wrapped in EVCTimestamp
     */
    override def getTimestamp(receiver: Int = -1): LCTimestamp = {
        return new EVCTimestamp(scalar)
    }

    /** Increments the value of this clock in the 
     *  scalar by multiplying by the assigned prime.
     */
    override def localTick(): Unit = {
        scalar = scalar * myPrime
    }

    /** Updates the scalar by merging the timestamp 
     *  into the clock.
     * 
     *  @param mergeTimestamp the timestamp to be merged into the clock
     */ 
    override def mergeWith(mergeTimestamp: LCTimestamp) : Unit = {
        val otherScalar = mergeTimestamp.asInstanceOf[EVCTimestamp].getScalar()
        scalar = getLCM(scalar, otherScalar)
    }

    /** Returns the LCM of the two scalars.
     * 
     *  @param s1 the first scalar 
     *  @param s2 the second scalar 
     *  @return the LCM of the two scalars
     */
    protected def getLCM(s1: BigInt, s2: BigInt) : BigInt = {
        // Returns LCM of two scalars
        (s1 * s2) / s1.gcd(s2)
    }

    /** Returns the number of bits needed to represent the timestamp of the clock.
     * 
     *  @return the number of bits needed to represent the timestamp of this clock
     */  
    override def getSizeBits: Int = {
        return scalar.bitLength
    }
}

/** EncVectorClock is the companion object of EncVectorClock that defines 
 *  the comparison functionality of the encoded vector clock. 
 */
object EncVectorClock extends LogicalClockComparator {
    /** Returns whether timestamp1 is logically before, 
     *  beforeEqual or equal to timestamp2. 
     * 
     *  @param timestamp1 the first timestamp to be compared
     *  @param timestamp2 the second timestamp to be compared 
     *  @return whether the timestamp1 happened before timestamp 2
     */ 
    def happenedBefore(timestamp1: LCTimestamp, timestamp2: LCTimestamp) : Boolean = {
        val scalar1 = timestamp1.asInstanceOf[EVCTimestamp].getScalar()
        val scalar2 = timestamp2.asInstanceOf[EVCTimestamp].getScalar()
        return (scalar1 <= scalar2 && scalar2 % scalar1 == 0)
    }
}