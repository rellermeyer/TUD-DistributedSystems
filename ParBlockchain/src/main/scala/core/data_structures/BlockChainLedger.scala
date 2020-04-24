package core.data_structures

import core.Config

class BlockChainLedger {
    private var ledger: Array[BlockChainBlock] = Array[BlockChainBlock](Config.genesisBlock)

    def addBlock(b: BlockChainBlock): Unit = {
        ledger = ledger :+ b
    }

    def validate(): Boolean = {
        var res = true;
        for (i <- ledger.length - 1 to 0 by -1) {
            if (i == 0) {
                ledger(i).equals(Config.genesisBlock)
            }
            else if (!ledger(i).getPreviousHash.equals(ledger(i-1).hash())) {
                res = false
            }
            else if (ledger(i).getSequenceId != ledger(i-1).getSequenceId + 1) {
                res = false
            }
        }
        res
    }

    def getLedger: Array[BlockChainBlock] = ledger

    def validateNewBlock(b: BlockChainBlock): Boolean = {
        b.getPreviousHash.equals(ledger.last.hash()) && b.getSequenceId == ledger.last.getSequenceId + 1
    }
}
