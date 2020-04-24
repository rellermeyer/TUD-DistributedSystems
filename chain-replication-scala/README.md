# Chain Replication Scala

Implementation of Chain Replication (by Renesse and Schneider) [1] in Scala.

## Setup
The project uses scala-sbt as its build tool. To set-up the project, run:
1. ```sbt update```
2. ```sbt run```

## Commands
**query**
Tells the client to execute a query request. The list of options is optional and specifies which fields in the object should be returned. If no options are specified, all fields will be returned.
- _Syntax_: 
```query <objId: Int> <options?: List[String]>```
```objId``` is required, should be an integer
```options``` is optional, should be a list of strings delimited with spaces
- Examples:
```query 5```
```query 5 name```
```query 5 name age```

**update**
Tells the client to execute an update request. The supplied object will overwrite/create the object with the given ID.
- _Syntax_: 
```update <objId: Int> <newObj: String>```
```objId is required, should be an integer```
```newObj is required, should be a JSON object (not an array e.g. [1, 2, 3])```
- Examples:
```update 5 {"name": "Bob", "age": "35", "city": "Delft"}```

**stresstest**
Tells the client to initiate a stress-test, sending a certain amount of messages to the system and specifying how many of these messages should be update messages, percentage-wise.
-   ```stresstest <totalMessages:Int> <updatePercentage:Int>```
    ```totalMessages is required, should be an integer``` 
    ```updatePercentage is required, should be an integer between 0 and 100``` 
- ```stresstest 5000 33```

## References

[1] Van Renesse, R., & Schneider, F. B. (2004, December). Chain Replication for Supporting High Throughput and Availability. In OSDI (Vol. 4, No. 91–104).