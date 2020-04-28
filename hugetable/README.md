# HugeTable
Open-source implementation of Google's [BigTable](https://research.google/pubs/pub27898/).

## Getting Started

### Obtaining the source code
Download the source code by running the following code in your command prompt:
```shell script
git clone https://github.com/fabianishere/in4391.git
```
or simply 
[grab](https://github.com/fabianishere/in4391/archive/master.zip) 
a copy of the source code as a Zip file.

### Building the project
For building the project, we use Gradle. To perform a build, enter the following in your command prompt in the root
directory of the project:
```shell script
./gradlew build
```
To build the binaries, make sure you also run the following command:
```shell script
./gradlew installDist
```

### Setup the environment
HugeTable requires the presence of a Hadoop HDFS cluster for distributed file storage and a ZooKeeper cluster for 
synchronization between the nodes. To simplify the deployment of HugeTable within a test environment, you may use the
single node test environment we provide in the repository.

To start the test environment, please run the following command:
```shell script
build/install/hugetable/bin/htable-test-env    
```
which will start a HDFS node on port 9000 and a ZooKeeper node on port 2181.

### Start a HugeTable cluster
The next step is to start one or multiple HugeTable nodes, which together will form a cluster. You may start a node
as follows:
```shell script
build/install/hugetable/bin/htable-server-cli --zookeeper localhost:2181 --hadoop hdfs://localhost:9000 --port 8080
```
Make sure you specify the correct ports and don't use duplicate ports for different nodes.

### Communicating with a HugeTable cluster
You may communicate with the HugeTable cluster by using the command-line client (`htable-client-cli`) or the Scala client
library (`htable-client`). To use the command-line interface interactively, enter the following command in your command
prompt:

```shell script
build/install/hugetable/bin/htable-client-cli -i --zookeeper localhost:2181   
```
Make sure again that you specify the correct ZooKeeper port. Then, enter `help` to obtain the possible commands you
can execute in the cluster.

## License
HugeTable is available under the [MIT license](https://github.com/fabianishere/in4391/blob/master/LICENSE.txt).
