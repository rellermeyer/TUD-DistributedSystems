# IN4391 - Distributed Systems - Orleans

#### Authors 

- Diego Albo Martínez
- Joris Quist
- Tomasz Motyka
- Wouter Zorgdrager



## Basic System Structure

With Orleans our biggest goal is to provide developers with an easy-to-use framework that allows for implementing highly concurrent systems and deploy those systems with a minimum effort while achieving good performance.

The system is centered around the concept of **Grains**, which are the minimum computing unit, which developers can extend and implement their own functionality in an easy manner. These grains reside in **Silos** or servers, who are responsible from making Grains accessible and executable by clients by delivering them the messages sent.

Grains can also communicate among them in a straightforward way by means of fire-and-forget messages or asynchronous messages in which they wait for a response from other party.

### Grains

We provide Grains as an abstract class that developers can extend and implement their functionality with. Grains provide one method to developers, the `receive` method, to which all messages destined for that grain are passed and have to be matched by the developer and implement its logic.

```scala
// Define the type of function that the Grains have
object Grain{
  type Receive = PartialFunction[Any, Unit]
}

// Grain abstract class extended by the users
abstract class Grain(val _id: String) extends Serializable  {
  def receive : Grain.Receive
  def store() = {
    println(s"Executing store function in grain with id ${_id}")
  }
}
```

As an example, a developer could program a grain to answer to the user in case they receive a certain message, below we provide a possible use a developer could do of that Grain abstract class to implement their own logic.

```scala
class GreeterGrain(_id: String) 
	extends Grain(_id)
  	with LazyLogging {

  /**
   *Receive method of the grain
   */
  def receive = {
    case ("hello", sender: Sender) =>
      logger.info("Replying to the sender!")
      sender ! "Hello World!"
    case _ => logger.error("Unexpected message")
  }
}
```

As can be seen, Grains and clients communicate through two different methods. Drawing a parallel with the akka framework, grains can make use of the `! (fire-and-forget)` and `? (asynchronous response)` methods to send a message to another grain. This makes the communication straightforward and simple and concise to program.

The *sender* of the message is also facilitated alongside the message itself so the grain always has a way of answering to the originator of the message.

### Silos

Silos are the servers that store the grains and that make sure that clients can make reference to a grain, and are also in charge of load balancing and grain replication and storage. Silos can be differentiated into two different kinds.

- **Master Silo**. In charge of distributing the load between the slaves as well as storing the index of which grain is stored in which server. It is also the server who the clients address when wanting to perform control operations such as creating, deleting or searching for a grain.

- **Slave Silos**. In charge of holding the grains and delivering to them their corresponding messages. They also hold multiple **Dispatchers** (one per grain type), which can be created at runtime to deliver messages sent to a particular type of grain. This means that the system doesn't have to be restarted when trying to add a different kind of grain; just by calling the `CreateGrain` method in the Master, a new grain will be created given that class' definition and a new Dispatcher created to deliver messages to those grains.

Both Master and Silos have a *Control Grain* in charge of performing these cluster management operations.

Once the Silos are initiated, a client can connect to the master silo and create and start its own grains simply in no more than a couple lines of code.

```scala
// Create reference to the runtime and register the grain
val runtime = OrleansRuntime()
      .registerGrain[GreeterGrain]
      .setHost("localhost")
      .setPort(1400)
      .build()

// Create grain and wait for a response with that grain's ID 
val createGrainFuture: Future[GrainRef] =
	runtime.createGrain[GreeterGrain]()
val grainRef = Await.result(createGrainFuture, 5 seconds)

// Send a fire and forget message to the newly created grain
grainref ! "hello"

// or
// Send an async message and register a callback
grainref ? "how are you doing?" onComplete {
      case Success(response) =>
        println(response)
      case _ => println("got no response")
    }
```



### Scaling Properties

In order to prevent concurrency issues, each grain is executed in a single thread, which guarantees that its inner variables will not be accessed concurrently. Furthermore, many grains can receive messages concurrently in a Silo, to increase throughput of the system.

Moreover, if a grain is under heavy load, the silos will be able to detect that through the gathered metrics of the dispatcher and replicate that grain so load can be distributed. This grain might be duplicated to the same silo or to a different silo altogether, and requests split between the two.

Also, if a grain is passive for a long time, that grain with its state will be persisted to persistent storage in a MongoDB database. Once that grain wants to be referenced by a client, the master and the server silo will activate the grain and load it in memory so it can receive messages again from the user.



 ## Low-level communication

For each silo we have to notion of a 'master' and a 'slave'. Currently only one master is supported.
Both master and slave run using two threads: 1) control thread to verify slaves/master is still alive and send heartbeats, 2) packet-manager thread which receives packets.

Each silo is configured using a `host` and a `port` for UDP commmunication. Keep in mind that if run on the same computer, different ports per silo need to be used!
A packet has the following form:

```scala
 case class Packet(packetType: String,
                    uuid: String,
                    timestamp: Long,
                    data: List[String] = List())
```
In the table below you can see the packets and its usages:

| Type               | Required data                  | Receiving (master)                                                                                                                                                                                                                                                                                                                                                                                                     | Sending (master)                                                                                                                | Receiving (slave)                                                                                                              | Sending (slave)                                                                                          |
|--------------------|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `HEARTBEAT`        | UUID and timestamp             | Heartbeats are received from slaves and slave info will be updated accordingly.                                                                                                                                                                                                                                                                                                                                        | Heartbeats are send to slaves.                                                                                                  | Heartbeats are received from the master and master info will be updated accordingly                                            | Heartbeats are send to the master.                                                                       |
| `HANDSHAKE`        | UUID and timestamp             | If a handshake is received and the slave is not yet in the cluster it will be added and send a `WELCOME` packet. This means that the master is now aware of this slave and will get heartbeats. It will send other slaves a `SLAVE_CONNECT` packet with details of the newly connected slave. Finally, it will send the new slave a `SLAVE_CONNECT` from all other slaves so that the new slave is aware of the other. | -                                                                                                                               | -                                                                                                                              | When a slave is started it will send the (pre-configured) master a handshake to be added to the cluster. |
| `WELCOME`          | UUID and timestamp             | -                                                                                                                                                                                                                                                                                                                                                                                                                      | Send to a slave when added to the cluster. Afterwards the master will send heartbeats to this slave (and the other way around). | Slave is considered connected to the master and will start sending heartbeats as well as recording heartbeats from the master. | -                                                                                                        |
| `SHUTDOWN`         | UUID and timestamp             | Receives this packets from slaves, so its removed from the slave table. Also other slaves are send a `SLAVE_DISCONNECT` for this slave.                                                                                                                                                                                                                                                                                | If a master goes in shutdown it will send this packet to its slaves so that they shutdown first.                                | This means a master will shutdown and therefore the slave will shutdown (and will then send the same packet to the master).    | Will be send if a slave shuts down.                                                                      |
| `SLAVE_CONNECT`    | UUID, timestamp, host and port | -                                                                                                                                                                                                                                                                                                                                                                                                                      | Will send to a slave when a new slave is added to the cluster (and to make the newly added slave aware of the others).          | This will make the slave aware of another slave.                                                                               | -                                                                                                        |
| `SLAVE_DISCONNECT` | UUID, timestamp                | -                                                                                                                                                                                                                                                                                                                                                                                                                      | Will send to a slave when another slave is disconnecting from the cluster.                                                      | This will make the slave remove the disconnected slave.                                                                        | -                                                                                                        |

**Note:** All communication goes through the master. Although they are aware, slaves won't directly communicate to each other.