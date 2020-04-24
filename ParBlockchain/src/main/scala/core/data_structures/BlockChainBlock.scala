package core.data_structures

import java.nio.ByteBuffer
import java.security.MessageDigest

class BlockChainBlock(prev: String, seq: Int) {
    private val prevHash: String = prev
    private val sequenceId: Int = seq
    private var transactions: Array[Transaction] = Array[Transaction]()

    def addTransaction(t: Transaction): Unit = {
        transactions = transactions :+ t
    }

    def hash(): String = {
        val res = this.getMD5
        new String(res)
    }

    def getMD5: Array[Byte] = {
        val md = MessageDigest.getInstance("MD5")
        md.digest(this.toString().getBytes())
    }

    def getPreviousHash: String = prevHash
    def getSequenceId: Int = sequenceId
    def getTransactions: Array[Transaction] = transactions

    def transactionIndex(trId: String): Int = {
        var res = -1
        for ((t, i) <- transactions.zipWithIndex) {
            if (t.getId.equals(trId)) {
                res = i
            }
        }
        res
    }

    override def toString: String = {
        var tString = ""
        for (i <- transactions.indices) {
            tString = tString + transactions(i).toString() + " "
        }
        prevHash + " " + sequenceId + " " + tString
    }

    override def equals(other: Any): Boolean = {
        other match {
            case otherBlock: BlockChainBlock =>
                if (otherBlock.transactions.length == transactions.length) {
                    var equal = true;
                    for (i <- transactions.indices) {
                        if (!transactions(i).equals(otherBlock.transactions(i))) {
                            equal = false
                        }
                    }

                    equal && prevHash.equals(otherBlock.prevHash) && sequenceId == otherBlock.sequenceId
                } else {
                    false
                }
            case _ =>
                false
        }
    }

    override def hashCode(): Int = {
        val res = this.getMD5
        ByteBuffer.wrap(res).getInt
    }
}
