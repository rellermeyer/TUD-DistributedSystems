# Graph-CRDT : Distributed Systems Group 10

## Running the project

To run the app on a specific port, such as 8083, run:

```
sbt "run 8083"
```

When not giving the argument, default port is 8080., mind that the quotes are needed for the previous example.
To check the synchronization. For now, the user has to run multiple instances of the app:

```
sbt "run 8080" &
sbt "run 8081" &
sbt "run 8082" &
```
And change these addresses in the arraybuffer of file `QuickstartApp.Scala` line 80, so the synchronizer knows which addresses to look for. 
In the future, a Kubernetes deployment will handle this automatically.

### Running the dockerfile (Extra)

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

This project was build in Scala, and therefore follows the normal project structure of a Scala project. The scripts are located in `crdt-graph/src/main` and tests  in `crdt-graph/src/test`. 

## Main
### `QuickstartApp.scala`

This is the main file of the project. It specifies all of the routes for the Server and specifies some utils for the operationlog which is used in the operations that are accessed through the routes. 

### `DataStore.scala`

Datastore handles all of the logic of the Graph-based CRDT that was explained in the paper by Shapiro et al. these operations consist of:

* Add vertex or arc
* Remove vertex or arc
* Lookup vertex or arc

### `Vertex.scala`

The vertex object is used by the Datastore and incorporates some functions about the specific vertex. These include:

* Adding or removing arcs
* Gettting arcs 
* Adding Ids (when it is added multiple times)

### OperationLogs
#### `OperationLog.scala`

Handles the store of the operation logs, which is later used to check by the client what operations has been done. 

#### `OperationType.scala`

Enumeration of the different types of operations. These consist of:
* `addVertex`
* `addArc`
* `removeVertex`
* `removeArc`

### WebServer
#### `Synchronizer.scala`

Handles the synchronization between nodes. Running in a separate thread, it gets the operations from the operation log and broadcasts these to all of the other nodes. Keeping track of which nodes are unresponsive. 

## Test

This folder contains the testcases, which consist of a Python script emulating client behavior to test the system. The script `run_servers.py` starts a couple of instances to run the tests on and the other python files are the test cases that are listed below with the expected behavior:

* Add v1 on one node, do lookup on v1 on other nodes (expected true)
* Add v1 on any node, add arc (v1, v2), lookup arc (v1, v2)(exp. false), Add v2, lookup arc (v1, v2)(exp. true)
* Add v1 on one node, remove v1 on other node, lookup v1 on first node (expected true, after waiting expected false)
* Add v1,v2 on any node, add arc(v1,v2), remove v1, add v1, lookup (v1,v2) (expected false, shows cascade delete)
* Add v1, Add v2, add arc (v1, v2), add arc (v2, v1), remove v1, lookup arc(v1,v2) (expected false), lookup arc(v2, v1) (expected false)
* Add v1 on node 1, Add v1 on node 2 + Remove on node 1, lookup on node v1(expected true)
* Add v1, v2 on node 1, add arc (v1, v2) on node 2, add same arc on another node (expected true)
* Add v1 on one node, remove v1 on another node, remove v1 on third node (expected: first true, then false)
* Add v1, v2 on node 1, wait, Add arc (v1, v2) on node 1 + remove v1 on node 2, lookup v1 on node 1
expected false (case described in the paper, removeVertex takes precedence)
* Add v1 on node 1, add v1 on node 2, wait, remove v1 on node 1 + remove v1 on node 2, wait, lookup v1 on node 3 (expected false, show removes commune)
* Add v1 on one node, add v1 on another node (expected true)
* Add v1, v2 on one node, add arc (v1, v2) on same node, add same arc on another node (expected true)
* Remove v1 on one node, add v1 on another node, remove v1 on first node (expected false, true, true)
* Add v1, v2 on one node, remove arc (v1, v2) on another node, add arc (v1, v2) on third node, remove arc (v1, v2) on first node, remove arc on second node (expected true, true, false, true, true, false)




