# jeweldc

This project implements the concept presented in the "Monitoring Distributed Real-Time Activies in DCOM" paper in Scala using Java RMI.

## Features

The code presents a distributed system with four different nodes, communicating through Java RMI. The system is able to compute simple arithmetic expressions (additions, subtractions, and multiplications). Each of these operations is done in a separate node, which combined allow for much more complicated expressions to be evaluated. The concept of JewelDC is in in the monitor, which keeps track of any events logged in the system for a particular activity.

## Build Instructions (without an IDE)

### Prerequisites

* [Java](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html)
* [Scala](https://www.scala-lang.org/download/) (don't install it using IntelliJ or SBT, but rather get the binaries)
* [sbt](https://www.scala-sbt.org/download.html?_ga=2.50290918.1328500312.1551994828-528202303.1551976991)

Make sure that the Java, Scala and sbt bin folders are added to your path (try the commands `java`, `scala` and `sbt` on console).

### Building using sbt

Open a command terminal and navigate to the root of this repository you should have cloned. Open the sbt shell in your terminal using the command `sbt`. Now you can compile the project using the command `compile`. If everything webt right, a bunch of `.class` file have been generated in the `/target/scala-x.y/classes` folder. If that is the case, then it should have been built correctly.

## Run Instructions for the example

1. Navigate to the `/target/scala-x.y/classes` folder and open **five** command terminals.
2. In the first terminal, open the Java RMI registry using the command `start rmiregistry`. If this went well it should have opened a new console that represents the RMI registry. You can now close the first terminal you opened.
3. In the second terminal, run the command `scala adder.AdderClient`. This starts the node is able to add two expressions. You should get the message `adder.Adder started`.
4. In the third terminal, run the command `scala subtractor.SubtractorClient`. This starts the node is able to subtract two expressions. You should get the message `subtractor.Subtractor started`.
5. In the fourth terminal, run the command `scala multiplier.MultiplierClient`. This starts the node is able to multiply two expressions. You should get the message `multiplier.Multiplier started`.
6. In the fifth terminal, run the command `scala monitor.MonitorExampleClient`. This node creates an Arithmetic Expression, displays it and then uses the three other nodes you opened to print the result, together with the complete event log.

## Some remarks

* Java RMI relies heavily on reflection, which makes building a general Monitoring in Scala extremely difficult. Therefore we've chosen to only build a Monitor tool for the simple example of a distributed calculator.
* The implementation relies on a scalar clock (or Lamport timestamps), therefore it assumes that an activity performs sequentally if the Monitor needs to give the exact order of events.
* The code contains five packages: `common`, `adder`, `multiplier`, `subtractor` and `monitor`. Each of these packages represent code specific to one node in the distributed system. The exception is the `common` package, which contains code shared by all the nodes.