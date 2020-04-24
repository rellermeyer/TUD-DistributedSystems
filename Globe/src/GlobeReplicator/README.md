## Globe Replicator
Experimental implementation of replication mechanism in Globe distributed system

### Required Configuration
In order to deploy an EC2 instance with Gradle, the following need to be configured:
- Your credentials must be available from a [default location](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html) (e.g. ~/.aws/credentials) with a profile named "default"
- You must have `ssh` and `scp` installed on your local machine
- The public key for your local SSH instance must be [imported into AWS EC2 configuration](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#how-to-generate-your-own-key-and-import-it-to-aws) as KeyPair with the name "globeReplicator"
- An EC2 Security Group with name "default" must be available and configured to allow inbound TCP traffic on port 22 (for SSH) and port 8080 (for RPC communication)

Since we use system tools with Gradle, this will work best on a Linux system with these tools installed.

### Usage
In order to deploy and start the Lookup Service, use the following Gradle task:
```
./gradlew deployLookupService
```
This will build the project, create the executable jar file, create an EC2 instance, upload the jar file to the EC2 instance and launch the Lookup Service application.

In order to deploy and start a Distributed Object, use the following Gradle task:
```
./gradlew deployDistributedObject
```
This will build the project, create the executable jar file, create an EC2 instance, upload the jar file to the EC2 instance and launch the Distributed Object application.

The EC2 instances can be terminated through Gradle with:
```
./gradlew terminateEC2Instances
```
This will terminate all EC2 instances that were created. The instance IDs of the created EC2 instances are written to a file named `instanceIds`. This file is removed after the EC2 instances are terminated.

#### Quick Start
There is a Linux shell script to deploy the entire distributed system with a configurable amount of replicas for the Distributed Object. This can be called with:
```
./deploySystem <number of replicas for Distributed Object>
``` 
This will deploy the Lookup Service and deploy the configured amount of Distributed Object instances. For example, `./deploySystem 3` will deploy 1 Lookup Service instance and 3 Distributed Object instances for a total of 4 EC2 instances. 
Since this is a Bash-based script, it requires `bash` to be installed on the local machine.

### Lookup Service
You can run this locally with `./gradlew :LookupService:run` from the GlobeReplicator root directory or `./gradlew run` from the LookupService subdirectory. 

Register a new location for a (possibly new) distributed object. 
Send a POST request with a JSON body containing the name and location for this new replica of the distributed object.

Example:
```
$ curl --data "{\"name\": \"test\", \"location\": \"http://localhost:8081\"}" http:/localhost:8080/register
This replica of the distributed object has been registered successfully
```

Retrieve the ID for the distributed object based on its name:
Send a GET request to the Lookup Service with path `getId/<name of distributed object>`.

Example:
```
$ curl http://localhost:8080/getId/test
5deb8ac1-d396-467c-8f64-3d5d53914280
```

Retrieve the locations for the distributed object based on its ID:
Send a GET request to the Lookup Service with path `getLocations/<id of distributed object>`

Example:
```
$ curl http://localhost:8080/getLocations/5deb8ac1-d396-467c-8f64-3d5d53914280
["http://localhost:8081"]
```

### Distributed Object
You can run this locally with `./gradlew :DistributedObject:run` from the GlobeReplicator directory or `./gradlew run` from the DistributedObject subdirectory.

Retrieve the current number stored in the distributed object:
Send a GET request to the Distributed Object with path `getNumber`.

Example:
```
$ curl http://localhost:8080/getNumber
0
```

Store a different number in the distributed object:
Send a POST request to the Distributed Object with path `setNumber/<new number>`

Example:
```
$ curl --data "" http://localhost:8080/setNumber/2
The number of this distributed object has been updated from 0 to 2
```
