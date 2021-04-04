# SGRuB

## Requirements

* Scala 2.12.* (2.12.12+)
* JVM 1.8 compatible JDK
* SBT 1.4.9+
* geth 1.10.1+

## Run

Start the private network and miner, follow the instructions in `geth_private/README.md`.

* Start SBT with a variable pointing to the config file: `sbt -Dconfig.file=application.conf`
* Compile: `compile`
* Run: `run`

The program has four options:
```
1: Deploy Smart Contracts
2: Start DataOwner and StorageProvider
3: Start DataUser
4: Demo: In-memory ADS
```

The first deploys the required smart contracts
to the blockchain and outputs their addresses to the log.
Note their addresses and enter them into `./application.conf`.

"2" starts both the DataOwner and StorageProvider. The former lets you enter
new values into the storage, the latter will actually store these values, and listens
for requests from DataUsers.

You can choose to enable "replication" for the DataOwner, which causes it to store
data in the StorageManager smart contract as well. This requires more Gas, but
also means DataUsers can get values directly from the blockchain, rather than via
the off-chain StorageProvider.

"3" starts a DataUser service, which can request values by their key, and listens
for Deliver events from either the StorageManager smart contract, or the off-chain
StorageProvider.

"4" is a demo of the Authenticated Data Structure without using the blockchain.

Since a full demonstration of SP<->DU communication over the blockchain requires
two processes to be running, you need to either run two instances of the project via
`sbt` (which is not officially supported, but possible if they are sufficiently isolated)
or run the project from a .jar package.

In order to create a single executable .jar file with all dependencies
included, you can use the sbt-assembly plugin.

In `sbt`:
```
clean
compile
assembly
```

Then: 
```
scala target/scala-2.12/GRuB_Scala-assembly-0.1.jar -Dconfig.file=application.conf -J-Xms256m -J-Xmx512
```