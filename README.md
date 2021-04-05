# Graph-CRDT : Distributed Systems Group 10

## Running the project with sbt

To run the app on a specific port, such as 8083, first change your directory to the crdt-graph directory, then run:

```
sh ./sbt_run.sh 8083
```

When not giving an argument, the default port is 8080. When you want to use multiple instances, first add all the addresses to hardcodedTargets variabel in crdt-graph/src/main/scala/org/tudelft/crdtgraph/Synchronizer. 
THis way the syncrhonizer knows which addresses to look for. Then call the command above multiple times from different terminals, or with & as follows:
```
sh ./sbt_run.sh 8080 &
sh ./sbt_run.sh 8081 &
sh ./sbt_run.sh 8082 &
```


## Running the project with Kubernetes

To run the app with kubernetes, change your directory to the crdt-graph directory, and run the following command:
```
sh ./kubernetes_run.sh
```

This will automatically start kubernetes with 5 pods, on which on 3 of them the app will be running. When the script asks for it, give the container names of the 3 pods you would like to run the app on. 

To change the number of pods/amount of pods on which the app will be running ..........




## Running the project with a dockerfile (Extra)

To publish the dockerfile locally, start of by running the following command in the terminal in the `crdt-graph` folder of the project:

```
sbt reload plugins clean compile
```

This makes sure all the dependency plugins are set and cleans the project before compiling it. 
Now to create a local Docker image run the command 

```
sbt docker:publishLocal
```

This generates a directory with the Dockerfile and environment prepared for creating a Docker image.
Next it  builds an image using the local Docker server.
Now that this is all set up, you can run the docker image by running the following command:

```
docker run --rm -p 8080:8080 -i crdt-graph:0.1.0-SNAPSHOT
```

If this command does not work, please run 

```
docker image ls
```
Where the last created docker image would be on top and should contain the name "graph-crdt"

The docker image should run on port 8080, navigate to localhost:8080 to access the service in the browser. 

When the image is stopped, the container is automatically removed.


# Project Setup

This project was build in Scala, and therefore follows the normal project structure of a Scala project. The scripts are located in `crdt-graph/`, the code is located in `crdt-graph/src/main/scala/org/tudelft/crdtgraph` and tests are located in `crdt-graph/src/test`. 



## Main
### `QuickstartApp.scala`

This is the main file of the project. It is the HTTP server of the system. 
The HTTP server that is being used in Akka HTTP. 
This file specifies all of the routes for the server. Furthermore it contains to (de)serialization logic of the JSON objects. 
The routes that exist in the system are the following:
* "/addvertex": this route expects a HTTP POST request with a JSON body including a single vertex in the following format: \{"vertexName": "abc"\}. If successful the server will return with the string "true", otherwise the server will return "false" with HTTP status code 400.
* "/addarc": this route expects a HTTP POST request with a JSON body including a single sourceVertex, and a single targetVertex in the following format: \{"sourceVertex": "abc", "targetVertex": "xyz"\}. If successful the server will return with the string "true", otherwise the server will return "false" with HTTP status code 400.
* "/removevertex": this route expects a HTTP DELETE request with a JSON body including a single vertex in the following format: \{"vertexName": "abc"\}. If successful the server will return with the string "true", otherwise the server will return "false" with HTTP status code 400.
* "/removearc": this route expects a HTTP DELETE request with a JSON body including a single sourceVertex, and a single targetVertex in the following format: \{"sourceVertex": "abc", "targetVertex": "xyz"\}. If successful the server will return with the string "true", otherwise the server will return "false" with HTTP status code 400.
* "/applychanges": this route expects a HTTP POST request with a JSON body with a serialized array of OperationLog objects. If successful the server will return with the string "true", otherwise the server will return "false" with HTTP status code 400. This route is called by the Synchronizer.
* "/lookupvertex": this route expects a HTTP GET request with a query parameter in the format "vertexName=abc". If the vertex exists in the graph, the server will return "true" and "false" otherwise.
* "/lookuparc": this route expects a HTTP GET request with query parameters in the format "sourceVertex=abc\&targetVertex=xyz". If the arc exists in the graph, the server will return "true" and "false" otherwise.
* "/debug-get-changes": this route expects a HTTP GET request with no parameters. It will return a JSON array with all operations performed on this instance. This method should be used only for development purposes.
* "/address": this route expects a HTTP GET request with no parameters. It will return a text message containing the address of this instance and other instances within the Kubernetes cluster. This method will fail if it is not executed in a Kubernetes environment and should be used only for development purposes. If the system is not in a Kubernetes environment it will return "false" with HTTP status code 400.


### `DataStore.scala`

Datastore handles all of the logic of the Graph-based CRDT that was explained in the paper by Shapiro et al. these operations consist of:

* Add vertex or arc
* Remove vertex or arc
* Lookup vertex or arc
* Apply changes (used during synchronization with other instances)
*getLastChanges (called by synchronizer to acquire the operations performed on the datastore since the last synchronization)

### `Vertex.scala`

The vertex object is used by the Datastore and incorporates some functions about the specific vertex. These include:

* Adding or removing arcs
* Gettting arcs 
* Adding IDs or removing IDs

### `ClusterListener.scala`

Needs to be documented 

This module is used as an interface with the kubernetes clusters. These functions include:
* Starting a manager
* Getting your own address
* Getting (available) members
* Getting broadcast addresses
* Waiting for the system being up

### `Synchronizer.scala`

Handles the synchronization between nodes. 
Running in a separate thread, it gets the operations from the operation log and 
broadcasts these to all of the other nodes. Keeping track of which nodes are unresponsive.



### OperationLogs
#### `OperationLog.scala`

Handles the store of the operation logs, which is later used to check by the client what operations has been done. 

#### `OperationType.scala`

Enumeration of the different types of operations. These consist of:
* `addVertex`
* `addArc`
* `removeVertex`
* `removeArc`


## Test
There are two different test directories. One directory is used to run the tests with kubernetes, and the other directory is used to run the tests with sbt.
The tests are integration tests, and are simple python scripts. To run tests on a kubernetes instance, first change your directory to `crdt-graph` and start a kubernetes instance with the following command:

```
sh ./kubernetes_run.sh
```

Then after the script has finished running, and you gave all container IDs, open up a new terminal, move to the kubernetes test directory `crdt-graph/src/test/src/kubernetes`, and call one of the scripts, e.g. testcase1.py as follows:

```
python testcase1.py
```

Read the comments in testcase1.py, and look at the terminal output if it matches the what the comment in the file says.


To run tests without kubernetes, but just with sbt instances, change your directory to `crdt-graph/src/test/src/sbt`, and run the following command:

```
python run_servers.py
```

This will start up three nodes, running on localhost with the following ports: 8080, 8081 and 8082. Then similar to the kubernetes test, run the following command to run testcase1.py:

```
python testcase1.py
```
Read the comments in testcase1.py, and look at the terminal output if it matches what the comment in the file says.

Because each test case leaves the nodes in a different state, you need to re-run the run_servers.py or kubernetes_run.sh scripts to get passing tests.

The system comes with the following integration tests:

* testcase1.py: Add v1 on one node, do lookup on v1 on other nodes (expected true)
* testcase2.py: Add v1 on any node, add arc (v1, v2), lookup arc (v1, v2)(exp. false), Add v2, lookup arc (v1, v2)(exp. true)
* testcase3.py: Add v1 on one node, remove v1 on other node, lookup v1 on first node (expected true, after waiting expected false)
* testcase4.py: Add v1,v2 on any node, add arc(v1,v2), remove v1, add v1, lookup (v1,v2) (expected false, shows cascade delete)
* testcase5.py: Add v1, Add v2, add arc (v1, v2), add arc (v2, v1), remove v1, lookup arc(v1,v2) (expected false), lookup arc(v2, v1) (expected false)
* testcase6.py: Add v1 on node 1, Add v1 on node 2 + Remove on node 1, lookup on node v1(expected true)
* testcase7.py: Add v1, v2 on node 1, add arc (v1, v2) on node 2, add same arc on another node (expected true)
* testcase8.py: Add v1 on one node, remove v1 on another node, remove v1 on third node (expected: first true, then false)
* testcase9.py: Add v1, v2 on node 1, wait, Add arc (v1, v2) on node 1 + remove v1 on node 2, lookup v1 on node 1 (expected false, case described in the paper, removeVertex takes precedence)
* testcase10.py: Add v1 on node 1, add v1 on node 2, wait, remove v1 on node 1 + remove v1 on node 2, wait, lookup v1 on node 3 (expected false, show removes commune)
* testcase11.py: Add v1 on one node, add v1 on another node (expected true)
* testcase12.py: Add v1, v2 on one node, add arc (v1, v2) on same node, add same arc on another node (expected true)
* testcase13.py: Remove v1 on one node, add v1 on another node, remove v1 on first node (expected false, true, true)
* testcase14.py: Add v1, v2 on one node, remove arc (v1, v2) on another node, add arc (v1, v2) on third node, remove arc (v1, v2) on first node, remove arc on second node (expected true, true, false, true, true, false)


Both the kubernetes and sbt directory contain the same tests. The only difference between the two directories is the ports they run on.

## Benchmarking

This folder contains the benchmarking that is done in the report, to run the Neo4j tests, please install the corresponding Neo4j implementation on Kubernetes with the command:

```
helm install mygraph https://github.com/neo4j-contrib/neo4j-helm/releases/download/4.2.2-1/neo4j-4.2.2-1.tgz \
    --set acceptLicenseAgreement=yes \
    --set neo4jPassword=mySecretPassword \
    --set core.numberOfServers=4 \
    --set readReplica.numberOfServers=0
```

The folder features the following files:

### `CrdtClient.py` and `Neo4jClient.py`

These are the respective clients that bridge the benchmarking test to the CRDT graph implementation on Kubernetes and the Neo4j project on Kubernetes. 

### Scalability

This folder contains the operations that are run in the scalability tests and the results for both the CRDT-graph implementation and the Neo4j project. 

### time_to_consistency

This folder contains the operations that are run in the time to consistency tests and the results for both the CRDT-graph (with different synchronizer delays) and the Neo4j project. 

### neo4j_test.py

Features a port forwarding script to make connection with a leader node and a follower node. Please make sure the right pod is selected on port 7000 as the leader port. To find out which one is the leader, open a exec to a pod in kubernetes and run:

```
cypher-shell
username: neo4j
password: mySecretPassword
:use system;
:show databases;
```

It will print out:

```
+---------------------------------------------------------------------------------------------------------------------------------------------------+
| name     | address                                                               | role       | requestedStatus | currentStatus | error | default |
+---------------------------------------------------------------------------------------------------------------------------------------------------+
| "neo4j"  | "mygraph-neo4j-core-0.mygraph2-neo4j.default.svc.cluster.local:7687" | "follower" | "online"        | "online"      | ""    | TRUE    |
| "neo4j"  | "mygraph-neo4j-core-1.mygraph2-neo4j.default.svc.cluster.local:7687" | "leader"   | "online"        | "online"      | ""    | TRUE    |
+---------------------------------------------------------------------------------------------------------------------------------------------------+
```

Where one can see that `mygraph-neo4j-core-1` is the leader in the neo4j cluster and should be set accordingly in the `portforwarding.sh` script. 

### `CrdtPreformanceTest.py`

This is the script to run the scalability tests including a read heavy test and a write heavy test. The code needs to be adjusted to run either of the CRDT-graph implementation or Neo4j project tests. 

### `TimeToConsistencyTest.py`

This is the script to run the time to consistency test. The code needs to be adjusted to run either of the CRDT-graph implementation or Neo4j project tests. 

### `GraphGenerator.py` 

Generates a instruction set with operations that can be used in the aforementioned tests. 

