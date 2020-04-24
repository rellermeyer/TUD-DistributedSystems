package core

import core.applications.Application
import core.communication.CommunicationType
import core.data_structures.{BlockChainBlock, Transaction}
import core.operations.Operation

import scala.collection.immutable

object Config {
    // Max time in milliseconds
    var maxTime: Long = 60 * 1000
    var maxNrTransactions: Int = 10
    var genesisBlock = new BlockChainBlock("f3f919946ed4320de8c2b304368e568a", 0)

    var communicationType = CommunicationType.rpc

    val cutBlockMessage = "cut-block"

    // Dummy transaction sent with a cut block, since ckite requires a transaction with a write
    // The value field of the transaction can be used to indicate the sequence number of the block that needs to be cut
    def cutBlockTransaction(seq: Long): Transaction = {
        new Transaction(0, Operation.set, "", None, immutable.Seq[String](), immutable.Seq[String](), seq, Application.A)
    }
}
