This is the repository for the Hyperdex group project of the 2020 Distributed Systems course at TU Delft.  
We implemented a subset of the Hyperdex system as specified in the 2012 paper (https://doi.org/10.1145/2342356.2342360).

## Implemented features

- Hyperspace hashing
- key subspace
- basic create, put, get, search API
- configurable amount of datanodes
- in-memory storage

## API

note: we only implemented integer values and keys, attribute names are strings  
example requests can be found in the postman collections

### Create
POST /create/"table_name" with attribute names as json list

creating a table that already exists overwrites the old one

### Lookup
GET /get/"table_name"/"key"

looking up a key that does not exist returns an empty 200  

### Put
Post /put/"table_name"/"key" with value specified as json dictionary

putting an item with a key that already exists overwrites the old one  
if an invalid attribute is specified an error with the invalid attributes is returned  
if an attribute is missing an error with the missing attributes is returned

### Search
GET /search/"table_name"/"key" with attributes values specified as json dictionary

if an invalid attribute is specified an error with the invalid attributes is returned

### common errors

if a table does not exist and error is returned  
if there are internal timeouts an error is returned

## Run instructions

### locally with single datanode
in two terminals:

`sbt "runMain hyperdex.Main gateway 25251"`

`sbt "runMain hyperdex.Main data 25252"`


### Docker and `docker-compose`
First set the `NUM_DATANODES` environment variable:
- Powershell: `$env:NUM_DATANODES=X` (not persistent)
- Bash: `export NUM_DATANODES=X`
 
Then, run: 
- Powershell: `docker-compose up --build --scale datanode=$env:NUM_DATANODES`
- Bash: `docker-compose up --build --scale datanode=$NUM_DATANODES`

You can now GET/POST as described above.
NOTE: Don't forget that if you want to rebuild the images (because you might have changed something in the source code,
for example) you have to run `docker-compose` with the `--build` flag.

---
In the rare case you need it here are the commands to build and run the docker containers separately:

First build the container (mind the `.` at the end):
1. `docker build -t hyperdex:latest -f Dockerfile .`

Create a docker network: `docker network create --subnet=172.18.0.0/16 akka-network` \
_(make sure to remove the network again if you want to run docker-compose, otherwise it will overlap: `docker network rm akka-network` and possibly `docker network prune`)_

Then, in separate terminals, run:
1. `docker run --net=akka-network --ip 172.18.0.22 -p 8080:8080 -it hyperdex:latest java -jar hyperdex.jar gateway 25251 1`
2. `docker run --net=akka-network --ip 172.18.0.23 -it hyperdex:latest java -jar hyperdex.jar data 25252`


### Running gatling tests 
First start the `hyperdex` system as described above. Then, in another terminal, run `sbt gatling:test`. It will run the gatling test as described in the `GatlingTest` folder. The output (including html file) is written to `target/gatling/gatlingtest-*`
