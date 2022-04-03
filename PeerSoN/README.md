# PeerSon - P2P Online social network

## IN4391 Distributed Systems Group 3

### Authors:

- Klabér, Rahim
- Morstøl, Hallvard Molin
- Qiu, Jason
- Samardžić, Mariana

## About PeerSoN

PeerSoN is a distributed Online Social Network (OSN). It is using the all-peers decentralized system architecture. PeerSoN is doing this by combining peer-to-peer (P2P) infrastructure, encryption and a distributed hash table (DHT).

## Requirements

These are the versions of tools we used. We have not tested if the system works on earlier or later versions.

- Open JDK: 17.0.2
- Scala version: 2.13.8
- SBT: 1.6.2

## Running the project

### Running with Intellij

In IntelliJ, the `src/main/resources` folder needs to be marked as a resource folder. To do this, right-click
the folder and at the bottom there should be an option called "Mark directory as" and then "Resources root" should be selected.

### Running the project

Two environment variables need to be set. First `HOST` which is the computer's IP or the router's IP in the case that you are behind a NAT and want to use the application over the internet (This can be found by running `curl ifconfig.me`) . 

Second `BOOTSTRAP` needs to be set to the IP of a TOMP2P DHT which will bootstrap our DHT.

To run a bootstrap node, use a dummy `HOST` value and set `BOOTSTRAP` to the computer's IP. Then login with a dummy name such as `BOOTSTRAPNODE`. The bootstrap node should now be ready.


Depending on your system build/compile:

- `sbt compile` or build from inside your IDE.

After building the project.
Run the main object in ./src/main/scala/main.scala.

- `sbt run` or run from inside your IDE.

You can then run some possible commands to send to the guardian, which works as the interface for the application:

- `login`
  - Guardian asks for mail and location to log user in. User gets any messages he received while offline.
- `logout`
  - Guardian logs user out by asking for mail and location.
- `send-message`
  - Guardian asks for sender, receiver and text to send and sends the message.
- `add-to-wall`
  - Guardian asks for sender, receiver and text to send and adds the text to the users wall.
- `request-wall`
  - Guardian asks for the user requesting, the file name and sends the file back.
  - To be able to fetch entries of some user's wall, a user should first request the wall index file of that user which contains all the file names of the individual entries.
  - The file name of a user's wall index is in the form email@wi.
- `exit`
  - Exit the interface
