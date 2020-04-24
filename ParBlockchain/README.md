# Scala ParBlockchain Implementation

Contains ckite library https://github.com/pablosmedina/ckite.
It is imported as jar because the maven central image is outdated.
All dependencies of ckite are added to the dependencies of this project.

ParBlockChain link: https://conferences.computer.org/icdcs/2019/pdfs/ICDCS2019-49XpIlu3rRtYi2T0qVYnNX/7HWNYlSCJyhOPbSIeCEqEo/56ABgEH0UlKTsiHHam4TmZ.pdf

## To run
1. Install sbt
2. Run "sbt" in terminal. This opens sbt shell.
3. Run "run" in sbt shell to run.

## Running tests
CKite does not stop correctly because it does not release the port.
This means the e2e tests can not be executed in sequence and have to be individually run with the "sbt testOnly e2e.<name of test file>" command.

The experiments are also contained in the test folder

## Ckite-Core-Structure ##

* CKiteBuilder
	* configuration (bootstrap)
	* **stateMachine(customize)** (e.g. KVStore)
		* applyWrite
		* applyRead
		* restoreSnapshot
		* takeSnapshot
	* rpc (FinagleThriftRpc)
		* **FinagleThriftServer(customize)**
			* start
			* stop
			* ckiteService
			* toTwitterFuture
		* **FinagleThriftClient(customize)**
			* send (message type)
	* storage
		* latestVote
		* latestSnapshot
	* build => apply Raft

* Raft
	* consensus
		* state
		* becomeFollower
		* becomeJoiner
		* isLeader
	* membership
		* val quorum = (members.size / 2) + 1
		* isReachQuorum
	* log (Rlog)


## OXII:
### Components:
#### Nodes (N)
 - Clients send transactions
 - Executors (E) validate and execute transactions
 - Orderers (O) agree on order of transactions

For each application a program code including the logic of that application (smart contract) is installed on executors, called agents.
Set A = {A1, A2, ...} is set of agents.
Every peer know agents and orderers.
Each pair of peers is connected by point-to-point bi-directional channel.
Pairwise authenticated so no forgery.

### Orderers:
Check access, order requests, construct blocks, generate dependency graph, multicast blocks.
Orderers are trusted entitities.
Discard unauthorized requests. Also check signature for validity.
Use asynchronous fault-tolerant protocol to establish consensus. State machine replication algorithm where replicas agree on ordering.
Consensus protocol is pluggable. (Pick one and implement or allow pluggability. PBFT as it is also used in paper, Raft better option)
Amount of orderers is determined by utilized protocol.
Orderers do not have access to smart contracts.

Orderers determine order of transactions.
Orderers batch transactions into blocks. This is deterministic and based on block size or cut-block message.
Orderers generate dependency graph for transactions within a blok. Knowledge of read and write sets is needed. Each transaction contains this information or it can be determined through static analysis.
Transaction T consists of w(T) and p(T) representing write and read sets. Each T also has timestamp ts(T) where each two transactions within a block where i before j: ts(Ti) < ts (Tj)
Ordering dependencies show conflicts. Two transactions conflict if they access same data and one of them writes.
Dependency graph of block is directed graph G = (T, E) where T is transactions in block and E = {(Ti, Tj) | Ti conflicts Tj, ts(Ti) < ts(Tj)}
The dependency graph generator is independent module. Block as input, graph as output.
Multicast block and dependency graph to executors.

### Executors:
Execute and validate transactions, update ledger and blockchain state, multicast blockchain state after executing transactions.
Maintain three components: Blockchain ledger, blockchain state, some smart contracts.
Each executor is an agent for one or more applications where for each application a smart contract is installed.
When an executor receives a block from orderers, it checks the applications of the transactions within the block. If the executor is agent for one of the transactions, it executes those transactions on the smart contract following the depenedency graph. It confirms order of dependent transactions and exeutes independent transactions in parallel. Finally multicast updated blockchain state to all other peers.
For each transaction for which an executor is not an agent, wait for matching updates from agents of those transactions, before committing the update. The required number of matching results from executors is decided by the system and knows all executors.

## ParBlockchain:
### Ordering phase:
Client c requests operation op for application A by sending {REQUEST,op,A,ts_c, c}signed by c to the orderer p it believes to be the primary. Use client timestamps for total orering of requests for each client and to ensure exactly-once semantics.
On receiving request, the primary checks signature, makes sure the client is llowed to send requests for application A, and then initiates conensus.
Once orderers agree on order, put transactions in block. Based on max size, max number of transactions or max time block production takes since the first transaction of the block. Primary can send cut-block message in consensus step of last request.
Each orderer node multicasts {NEWBLOCK,n,B,G(B),A,o,h}signed by o. A is set of applications that have transactions in block. h = H(B') is hash of previous block.

### Execution phase:
on receiving NEWBLOCK message, check signature and hash and then log the message. Check A to see if block contains transaction it needs to execute.
When received specified number of matching new block messages, f + 1 for PBFT, mark new block as valid and enter execution phase.
Three procedures that are run concurrently:
1) Execute the transaction following the dependency graph.
2) Multicast commit messages and 
3) update blockchain state on receiving commite messages from sufficient others.
If executor not agent of any transaction in block, it becomes passive and only runs third procedure.
Transaction can only be executed when predecessors in dependency graph are committed. Define Pre and Suc to present set of pre and sucs of a node in the graph. Only start execution thread if all in set pre are in committed or executed set.
When multicasting the results, three situations can happen. 
If all the transactions in a block belong to the same application, an agent executes all and multicasts {COMMIT, S, e}signed message to all other executor nodes. S is the state of the blockchain and consists of set of pairs (x, r) where x is transaction id and r is the set of updated records. If x is invalid (x, "abort").
If transactions within block are for different applications but the transactions of each application access different records, agents can still execute their transaction independently.
If there are dependencies between transactions, the agents have to wait for the other applications to execute and send commit message. This might result in deadlock. Solution is to send commit message on finishing transaction if that transaction is required by other agent.
This is checked when execution finishes in the succ set.
Add received commit messages to Re(x).

Block size around 200, mentioned in experiments.

### Libraries:
Scala Akka library for communication
Create rpc bridge for ckite

Creating and sharing smart contract
First version hard-coded behaviour for one application, later find way to make it pluggable.

Use scala MessageDigest class with SHA-256 for hashing.
https://stackoverflow.com/questions/46329956/how-to-correctly-generate-sha-256-checksum-for-a-string-in-scala

Also need to sign message with public/private keys, use java RSA functionality.
https://www.devglan.com/java8/rsa-encryption-decryption-java
Leave for later.

### Consensus algorithm implementations:
Raft - ckite (probably best), riff
PBFT - scala.bft (seems iffy)
Paxos - asd-p2, scala-paxos

### Data storage
Database, mySQL.
Use in-memory for first versions.

### Testing with ScalaTest
Test with byzantine failures

Network structure given on start-up, preferably config file.
Config also options for consensus algorithm, block size.

### Classes:
Message
 - Subclass of message types

OperationTypes
Client
Node
 - Executor
 - Orderer

RSAUtils
CommunicationChannel to introduce delays and simulate network
Transaction
BlockchainBlock
BlockchainState
BlockchainLedger
StateStorage
DependencyGraph
Config

### Sprints:
Sprint 1 (week 2)
Create design
Set up repository and dev environments
Create code skeleton based on initial design

Sprint 2 (week 3)
Get ckite running (with Akka)
Implement first version client, nodes, data structures (no communication)
Hard-code smart contract
Unit test

Sprint 3 (week 4)
Implement communication
Implement configuration

Sprint 4 (week 5)
Implement RSA
Debug

Sprint 5 (week 6/7)
Deadline implementation
Implement database storage
Debug

Sprint 6 (week 7/8)
Experiment

Sprint 7 (week 8/9)
Deadline experiments
Experiment some more

## Testing and data collections
The testing will be mainly performed on the same computer and have 5 replicates to make sure the results are accurate and relaible.

### Number of nodes our implementation supports

This part will change the number of different nodes including clients, orders and executors. 

### Number of submitted transactions per second

This part will change the number of transactions and see the impact

### Computing resources and cost

This part is the only part need to be run in different computer. Be aware the length of the popagration time may vary in different hardwares. You may need to adjust this in the script.

### Speed enhanced by the parallel execution

This part will be compare by executing different in different applciations in different nodes or keep every applications in the same nodes.

### Degree of contentions in the workload
