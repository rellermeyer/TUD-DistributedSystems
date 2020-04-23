---
title: "Final Report: Byzantine-CP"
author: [Douwe Brinkhorst, Patrik Kron, Michael Leichtfried, Miguel Lucas]
date: "2020-04-23"
subject: "Distributed Systems (Group 7)"
coursecode: "IN4391"
keywords: [distributed, commit protocol, atomic commit, byzantine]
lang: "en"
titlepage: "true"
titlepageTUDelft: "true"
papersize: "a4"
---

## Introduction

In the past weeks we have implemented the system described in *"A Byzantine Fault Tolerant Distributed Commit Protocol"* by Wenbing Zhao[^1].

In the paper the author describes a commit protocol for transactions which run over untrusted networks.
The protocol consist of duplicating the coordinator and running a Byzantine agreement algorithm among the coordinator replicas. This protocol tolerates byzantine coordinator and faulty participant behaviour.
The Two Phase Commit (2PC) protocol addresses the issue of implementing a distributed commit protocol for distributed transactions, and different approaches have been conducted in order to make it support byzantine behaviours.
This paper's motivation was to improve these 2PC byzantine behaviour approaches.
A distributed transaction is a transaction that is performed in multiple machines across a computer network.
The transaction is only committed if all operations succeed, and it is aborted if any operation fails.

[^1]: "A Byzantine Fault Tolerant Distributed Commit Protocol"* by Wenbing Zhao (Department of Electrical and Computer Engineering, Cleveland State University), <https://ieeexplore.ieee.org/document/4351387>

## Objectives

We set objectives from the beginning in order to figure out and organise the work that had to be done.
The objective list was divided into categories to state the priority of each objective.

- **Must have:** features the project must have in order to fulfill the basic requirements.
  - Implementation for coordinators.
  - Implementation for participants/initiators.
  - System testing infrastructure, including coordinator byzantine behaviours testing.

- **Should have/Could have**: features that might be implemented depending on time constraints.
  - Distributed deployment: test the system in multiple interconnected machines to simulate a realistic environment.
  - Message signing and signature checking.

- **Could have/Will not have**:
  - View change mechanism (this feature was not implemented by the paper authors either).

## The Protocol

### Problem

In the basic 2PC protocol there is a single coordinator, and multiple participants.
This means that the coordinator is a single point of failure and that the coordinator is trusted by the participants.
If the coordinator expresses byzantine behaviour, by for example telling one participant to commit and another to abort the participants will trust the coordinator and therefore do as it says.
This would then lead to the participants having different views on what transactions are done, which defeats the purpose of the protocol, to reach an agreement.

### Why a byzantine fault tolerant commit protocol

There are multiple reasons to choose a byzantine fault tolerant distributed commit protocol. By having multiple coordinators one or more servers can fail without any downtime (or a low downtime, since a view change may need to happen, and commiting of transactions restart).
The coordinators can be distributed over multiple datacenters or even countries, making sure the system continues to work even if some datacenter expecinces problems or even a country (assuming most servers are in other countries).
Since the protocol is byzantine fault tolerant the system will even withstands compromiced coorinators (that expresses byzantine behaviour).
<!-- The byzantine fault tolerant protocol does also detect participants that sends different chooses wether to commit or abort a transaction to different coordinators, making sure that participants can not lie. -->

### Distributed commit protocol

In the distributed commit protocol presented in the paper they address the problem with a byzantine coordinator and solves it by distributing the coordinator into multiple coordinators that does a byzantine agreement on firstly witch participants are part in the voting on a transaction, and secondly on what the participants voted.
The resulting system works so long as it has *"3f + 1"* coordinator replicas where  at most "*f*" coordinators are byzantine.

![An example of the voting part of the BFTDCP protocol.](images/bftdcp.png){#fig:examplevoting width=75%}

<!-- According to 2PC protocol a distributed transaction contains one coordinator and some participants, but in the byzantine distributed commit protocol several coordinators are used. -->

### Implementation <!-- ?? -->

In the byzantine distributed commit protocol the original coordinator is called primary and coordinator copies receive the name of replicas.
Every participant must register with the coordinators before the commit protocol starts. The commit protocol starts when a replica receives a commit request from a participant, which from now on will be called initiator.
Now the coordinator replica sends a *"prepare"* request to every registered participant and waits until enough *"prepared"* messages are received from the participants.
When *"prepared"* messages are received an instance of a *"Byzantine Agreement Algorithm"* is created.
After reaching an agreement, coordinator replicas send the agreement outcome to participants, which will only commit the transaction once *"f + 1"* similar outcomes are received.
This way at least one of the *"f + 1*" outcomes received comes from a non-byzantine replica.

<!-- In the 2PC protocol a single coordinator is used. The distributed commit protocol presented in the paper introduces multiple coordinators in order to remove the single point of failure, and to "accept" byzantine failures in a coordinator, and some byzantine failures in participants.   By making the coordinators do a byzantine agreement on first, who of the participants are involved in a transaction, and later on what the participants voted, the protocol as a whole can now accept coordinators that goes down (increased availability) as well as  byzantine coordinators. In the 2PC commit protocol if the coordinator breaks in such a was as it presents a byzantine behaviour where it sends the decision to commit a transaction to some participants and to abort the transaction to other participants, the participants will believe the coordinator (since there is only one, there is no way to check if it speaks the truth), and therefore do accordingly. That will result in two different views on what is committed. Which was the problem the protocol tried to solve.   -->

### Byzantine Agreement Algorithm

Wenbing Zhao's  algorithm is based on the BFT algorithm by Castro  and Liskov.
Byzantine Agreement Algorithm differs from BFT because BFT aims to agree on the ordering of the requests received while the Byzantine Agreement algorithm's objective is to agree on the outcome of a transaction.
Byzantine Agreement Algorithm has three main phases:

- **Ba-pre-prepare phase**: in this phase the primary sends a "*ba-pre-prepare"* message to all other replicas.
  The *"ba-pre-prepare"* message contains the following information: view id, transaction id,  transaction outcome and decision certificate.
  The decision certificate is a collection of records of each participant's vote for every transaction.
  A new view is created if the *"ba-pre-prepare"* message fails any verification (signed by the primary, coherent transaction and view and has not accepted a *"ba-pre-prepare"* in this view-transaction).
  Once a replica is ba-pre-prepared it multicasts a "pre-prepared" message to all other replicas.

- **Ba-prepare phase**: a *"ba-prepare"* message contains the view id, transaction id, digested decision certificate, transaction outcome and replica id (*"i"*).
  The message is accepted if it is correctly signed by replica *"i"*, the receiving replica's view and transaction id match message view and transaction's id, message's transaction outcome matches receiving replica transaction outcome and decision certificate's digest matches the local decision certificate.
  When a replica collects 2f matching *"ba-prepare"* messages from different replicas it can make a decision for the current transaction and sends a *"ba-commit"* message to all other replicas.

- **Ba-commit phase**: a *"ba-commit"* message contains the view and transaction id, decision certificate's digest, transaction outcome and sender replica id.
  A replica is said to have ba-committed if it receives 2f+1 matching *"ba-commit"* messages from different replicas and the agreed outcome is sent to every participant in the current transaction.
  *"Ba-commit"* messages are verified alike *"ba-prepare"* messages.
  **View changes with timeouts and so missing**

## Design Decisions

We decided to use **Akka** since it proved a actor framework that could be used to avoid implementing the sending of messages.
We created two typed of actors, coordinators and participants.
From the tests we created we initaialize a couple of coordinators and participants (depending on the test case) and send a initalization message from one of the participants (the initiator) to the coordinators.
After that the protocol starts.

## Implementation Details

We have used the **akka** framework to implement coordinators and participants as actors since it simplifies distributed and concurrent application development.
Actors communicate with each other through messages using the akka API.
These messages are signed using public key technology so that no unidentified participant can interfere.

## Evaluation

### Functional requirements

Functional requirements were evaluated using Scala tests (```ScalaTestWithActorTestKit```).
We considered:

- Basic Committing
- Aborting
- Unilateral aborting
- Byzantine behavior tolerance

Along with the development we have built a set of tests which tested every feature we implemented. This way we ensured that every module did its work properly.  
We have built a total of 15 tests through which Coordinators and Participants exchange messages and perform the corresponding message verification and decision making processes.
These tests ensure the implementation correctness by creating protocol instances and making coordinator replicas and participants conduct several distributed commit protocols.
A different number of transactions, coordinator replicas and participants is used to test the system's resilience to multiple message passing.
Further participant behaviour is tested by sending abort messages in the middle of a commit transaction.

The following tests were implemented using tests in/with Scala/Akka:
TODO: check if this list is still up-to-date

- **Test 1:** Initiate the protocol and commit with 1 coordinator replica and 1 participant.

- **Test 2:** Initiate the protocol and commit with 4 coordinator replicas and 1 participant.

- **Test 3:** Initiate the protocol and commit with 1 coordinator replica and 4 participants.

- **Test 4:** Initiate the protocol and abort with 1 coordinator replica and 1 participant.

- **Test 5:** Initiate the protocol and abort with 4 coordinator replicas and 1 participant.

- **Test 6:** Initiate the protocol and abort with 1 coordinator replica and 4 participants.

- **Test 7:** Initiate the protocol with 1 coordinator replica and 1 participant and make the participant abort the transaction.

- **Test 8:** Initiate the protocol with 1 coordinator replica and 5 participants and make one participant unilaterally abort the transaction.

- **Test 9:**  Initiate the protocol with 4 coordinator replicas and 5 participants and make one participant unilaterally abort the transaction.

- **Test 10:** Initiate 2 instances of the protocol and succeed committing in both.

- **Test 11:** Initiate a commit with 1 coordinator replica and 1 participant which is followed by initiating an abort for this transaction, resulting in the in-flight commit being aborted.

- **Test 12:** Initiate the protocol and commit with 4 coordinator replicas and 1 participant, where one of the coordinator replicas is nonresponsive.

- **Test 13:** Initiate the protocol and commit with 4 coordinator replicas and 1 participant, where one of the nonprimary coordinator replicas exhibits some byzantine behaviour.

- **Test 14:** Initiate the protocol and commit with 4 coordinator replicas and 1 participant, where the primary coordinator replica exhibits some byzantine behaviour.

- **Test 15:** Initiate the protocol and force a view change by creating a participant and a slow coordinator which will exceed the timeout.

### Non-Functional Requirements

All tests were performed with 4 coordinators.
The tests were carried out on a laptop with an Intel i3-5005U (dual-core operating at a fixed 2.0 GHz) with 8 GB of RAM.  

The latency was measured both with normal behaving nodes and with a single byzantine nonprimary coordinator replica.
If a nonprimary coordinator replica is byzantine, a small performance reduction could occur since the algorithm might have to depend on other replicas to reach consensus.
However, it was observed that actors would often be running sequentially due to limited parallelism, which limited the benefit of early consensus.
In each test batch, 10 runs were performed of 100 sequential commits each.
This was then repeated 5 times over multiple days.
*@fig:evaluationchart1 shows the latency measured in these test.
The error bars indicate 2 standard deviations.
No performance difference could be discerned.

![Latency comparison between normal operation and a byzantine nonprimary coordinator](./images/latency.png){#fig:evaluationchart1 width=75%}

Figure ...: Latency comparison between normal operation and a byzantine nonprimary coordinator.

|Number of Participants|Average latency (ms)|Throughput (transactions/s)|
|--|---|---------|
|2 |382|2.6156101|
|4 |434|2.3037758|  
|6 |569|1.754817|
|8 |614|1.6273392|
|10|822|1.2165303|

Table: An example table. TODO: remove or update.

## Future Work

- expanding the simulation of byzantine behaviour.
  The current implementation of byzantine behaviour only covers a fraction of the byzantine faulty space.
  Expanding this could be interesting, but simulating more byzantine behaviours would have a large impact on code complexity.
  Ultimately, we believe simulating all possible byzantine behaviours is impossible.
  If we simulate anything less, we can only prove the system is not byzantine fault tolerant, not that it is.
- running the system in a distributed manner: actors on different hosts should be able to communicate with each other.  

## Wrap-Up

Conclusion/Summary
how was the project for us? difficulties (3 exchange students)
did we fulfill our expectations? why did we fail to run and evaluate our implementation in a distributed manner?

One of our team members, Miguel Lucas, was responsible for implementing the system in a distributed fashion, but due to the coronavirus situation he had to return to his country and finish his studies at his home university.
For this reason, he could not work on the project any more so the system could not be implemented in a distributed fashion.

## Conclution

We managed to implement the protocol to the extent that the paper did it also.
We did though not run it in a distributed manner since when it was time to do that we where no longer at the same place and could not run it between out computers, without opening ports to the internet which we did not want to do.
