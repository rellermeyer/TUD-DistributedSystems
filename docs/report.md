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

### Byzantine Fault Tolerance - Why

There are multiple reasons to choose a byzantine fault tolerant distributed commit protocol.

In the basic 2PC protocol, a single coordinator is responsible for multiple participants.
The coordinator is trusted by the participants and represents a single point of failure.
If the coordinator expresses byzantine behaviour, for example by telling one participant to commit and another to abort, the participants will trust the coordinator and therefore do as it says.
This would then lead to the participants having different views on what transactions are done, which defeats the purpose of the protocol, to reach an agreement.

That is the main problem the byzantine fault tolerant commit protocol solves. It can handle compromised/byzantine coordinators that for example sends different messages to different participants. Another problem the byzantine fault tolerant commit protocol solves is that it's able to continue working even if some coordinators fail or become unavailable.
Compare this with the 2PC protocol where the protocol would stop working if the coordinator stop working.
Thus it improves the availability of the system compared to 2PC.
This can be especially useful if the coordinators are distributed over multiple datacenters or even countries, making sure the system continues to work even if some datacenters, or even a country (assuming most servers are in other countries), experiences problems.
Since the protocol introduces multiple coordinator, it becomes possible for the participant to send different messages to the coordinators. The authors have thought of this and made sure that the protocol detects and handle this.

### Distributed commit protocol

In the distributed commit protocol presented in the paper the author address the problem of a byzantine coordinator by replicating the coordinator. One of these replicas is the primary. The resulting system works correctly so long as it has *"3f + 1"* coordinator replicas where at most "*f*" coordinators are byzantine.  

One of the participants initiates the transaction, this is the initiator. The initiator propagates the transaction to the other participants. Then the participants register with the coordinators.
The protocol starts when the initiator has received confirmation that, from all participants that they have registered with, a sufficient and then sends a initiate commit request message to all coordinators.  
The coordinators then sends a prepare message to all registered participants. The participants answer if they can commit or not. If any cannot, an abort will take place, otherwise the protocol proceeds.  
Now the coordinator replica sends a *"prepare"* request to every registered participant and waits until enough *"prepared"* messages are received from the participants.  
When *"prepared"* messages are received an instance of a *"Byzantine Agreement Algorithm"* is created, where a byzantine agreement is attempted on firstly which participants are taking part in the voting on a transaction, and secondly on what the participants voted. This is described in more detail in the "Byzantine Agreement Algorithm" section.  
After reaching an agreement, coordinator replicas send the agreement outcome to participants, which will only commit the transaction once *"f + 1"* similar outcomes are received, to ensure that they reject the answer of byzantine coordinators. Since the protocol works with up to "*f*" byzantine coordinators, when *"f+1"* messages are received the participant knows that it has not received the message from a byzantine coordinator.  

![An example of the voting part of the BFTDCP protocol.](images/bftdcp.png){#fig:examplevoting width=75%}

### Byzantine Agreement Algorithm

Wenbing Zhao's algorithm is based on the BFT algorithm by Castro and Liskov.
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

<!--  By making the coordinators do a byzantine agreement on first, who of the participants are involved in a transaction, and later on what the participants voted, the protocol as a whole can now accept coordinators that goes down (increased availability) as well as  byzantine coordinators. In the 2PC commit protocol if the coordinator breaks in such a was as it presents a byzantine behaviour where it sends the decision to commit a transaction to some participants and to abort the transaction to other participants, the participants will believe the coordinator (since there is only one, there is no way to check if it speaks the truth), and therefore do accordingly. That will result in two different views on what is committed. Which was the problem the protocol tried to solve.-->

### Thoughts on the paper

Although the paper is mostly written in a clear and concise manner, some parts seems to be lacking and not fully clear to me.

The decision certificate contains a list of votes and registrations, both signed by the sender. WHile the signature for the registration contains the sender, the signature of the vote does not. We assume that this is a typo in the paper.

p.39 "Furthermore, we assume that a correct participant *registers with f+1 or more correct coordinator replicas* before it sends a reply to the initiator when the transaction is propagated to this participant with a request coming from the initiator."  
p.42 "Because the participant p is correct and responded to the initiator's request properly, it must have *registered with at lease 2f+1 coordinator replicas* prior to sending its reply to the initiator."  
-- The number of registrations is the *same* as the first specifically mentions *correct* coordinator replicas. Therefore the participant actually has to register with f more replicas.

Initially it was not clear that the initiator propagates the transaction to all participants, as the Introduction specifically mentions the participants-have-to-know-all-other-participants as a drawback of another protocol.

> A backup suspects the primary and initiates a view change immediately if the ba-pre-prepare message fails the verification

Shouldn't the view-changes be voted on?

> When the primary for view v+1 receives 2f+1 valid view change messages [...], it [...] multicasts a new view message for view v+1.

What if the new primary is byzantine (and does not send out the new view), how is it guaranteed that another replica takes over to view v+2

Pseudo-code: The paper never mentions if the functions are thought to be executed on coordinator or participant side.

## Design Decisions

TODO: remove this? I believe this is all described in implementation details and the protocol.  

We have used the **Akka** framework to implement coordinators and participants as actors since it simplifies distributed and concurrent application development.
Actors communicate with each other through messages using the **Akka** API.

We decided to use **Akka** since it proved a actor framework that could be used to avoid implementing the sending of messages.
We created two typed of actors, coordinators and participants.
From the tests we created we initaialize a couple of coordinators and participants (depending on the test case) and send a initalization message from one of the participants (the initiator) to the coordinators.
After that the protocol starts.

As we're implementing a commit protocol which is based on messages, it makes sense to use a framework for passing messages.
As we are restricted to Scala and **Akka** seems to be one of the most-used frameworks (actor framework) for that purpose, we chose to use that.
We decided against directly implementing participants and coordinators as a FSM as our team is more familiar with more imperative programming. Furthermore, in the beginning we were not sure if we understood all parts of the paper.

### View Changes

We decided to exclude the implementation of view changes from the requirements the author did not them either. It seems to be somewhat careless that the paper authors have not implemented, as this means that no byzantine primary coordinator is supported. We therefore assume that no full implementation of this protocol exists up to now.

## Implementation Details

These messages are signed using public key technology so that no unidentified participant can interfere.
The view change mechanism has not been implemented.

- not using a state machine
- the signing is implemented using a master certificate that signs all the individual certificates (of the coordinators and participants)
- it is currently not checked whether the message originates (with regards to the from-field) from the same actor as it is signed by (spoofing is still possible)

Shortcomings:

- running it in a distributed fashion

Initially it was not clear whether the initiator should send the commit request to the primary coordinator only.

### Running it in a distributed fashion

The idea to get our implementation running in a distributed fashion is:

- Manually start Akka Actors in different JVMs (could be on the same or on different PCs)
- Get the actors to communicate with each other using Artery (serialization of messages, actor discovery)
- Key distribution might be hard, disable the checks in code

## Evaluation

### Functional requirements

Functional requirements were evaluated using the Akka Actor Test Kit with Scala Tests (```ScalaTestWithActorTestKit```). We considered:  

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

- **Test 1:** Run with 1 coordinator replica and 1 participant, resulting in a commit.

- **Test 2:** Run with 4 coordinator replicas and 1 participant, resulting in a commit.

- **Test 3:** Run with 1 coordinator replica and 4 participants, resulting in a commit.

- **Test 4:** Run with 1 coordinator replica and 1 participant and have the participant unilaterally abort the transaction, resulting in an abort.

- **Test 5:** Run with 1 coordinator replica and 4 participants and have one participant unilaterally abort the transaction, resulting in an abort.

- **Test 6:** Run with 4 coordinator replicas and 4 participants and have one participant unilaterally abort the transaction, resulting in an abort.

- **Test 7:** Run with 1 coordinator replica and 1 participant and have the initiator abort the transaction, resulting in an abort.

- **Test 8:** Run with 4 coordinator replicas and 1 participant and have the initiator abort the transaction, resulting in an abort.

- **Test 9:** Run with 1 coordinator replica and 4 participants and have the initiator abort the transaction, resulting in an abort.

- **Test 10:** Run 2 instances of the protocol, both resulting in a commit.

- **Test 11:** Run with 4 coordinator replicas (of which 1 is nonresponsive) and 1 participant, resulting in a commit.

- **Test 12:** Run with 4 coordinator replicas (of which 1 is nonresponsive) and 1 participant and have the initiator abort, resulting in an abort.

- **Test 13:** Run with 4 coordinator replicas (of which 1 non-primary exhibits some byzantine behaviour) and 1 participant, resulting in a commit.

- **Test 14:** Run with 4 coordinator replicas (of which 1 non-primary exhibits some byzantine behaviour) and 1 participant and have the initiator abort, resulting in an abort.

- **Test 15:** Run with 4 coordinator replicas (of which the primary exhibits some byzantine behaviour) and 1 participant, resulting in a commit.

- **Test 16:** Run with 4 coordinator replicas (of which the primary exhibits some byzantine behaviour) and 1 participant and have the initiator abort, resulting in an abort.

- **Test 17:** Run with 1 participant and 1 slow coordinator which will exceed the timeout, resulting in a view change being suggested.

Tests 1 through 14 succeed as expected.
Tests 15 and 16 fail, since the solution to a byzantine primary coordinator replica is to perform a view change, which has not been implemented.
Test 17 requires only that the need for a view change is detected, not that it is actually performed.
Hence it also succeeds as described.

### Non-Functional Requirements

All tests were performed with 4 coordinators.
The tests were carried out on a laptop with an Intel i3-5005U (dual-core operating at a fixed 2.0 GHz) with 8 GB of RAM.  

The latency was measured both with normal behaving nodes and with a single byzantine non-primary coordinator replica.
If a non-primary coordinator replica is byzantine, a small performance reduction could occur since the algorithm might have to depend on other replicas to reach consensus.  
The test consisted of starting a new transaction once the previous had committed, until 100 commits had been completed. The average latency of such a test constitutes one sample. 50 such samples were collected for each test configuration.     
*@fig:evaluationchart1 shows the latency measured in these test. The error bars indicate 2 standard deviations, based on the variance between samples of 100 commits, not between the indiviual commits. The variance between indivual commits is expected to be larger.  
No performance difference could be discerned. This might be related to the observation that actors would often be running sequentially due to limited parallelism, which limited the benefit of early consensus.  

![Latency comparison between normal operation and a byzantine non-primary coordinator](./images/latency.png){#fig:evaluationchart1 width=75%}

## Discussion
The main challenge of the project was to understand who the system was supposed to work. It was not very clear from the original paper that the system as very heavily depending on the WS-AT protocol, and that it therefore was crucial to understand it before understanding the byzantine fault tolerant distributed commit protocol. The coronavirus situation also made it necessary for three of us to return to our home countries urgently, that led us to loose some time in the last few weeks of the project, which we could not fully recover in the extra week we got, since the new courses had started then.

One of our team members, Miguel Lucas, was responsible for testing the system in a distributed fashion, but due to the coronavirus situation he had to return to his country and finish his studies at his home university.
For this reason, he could not work on the project any more so the system could not be tested in a distributed fashion.

The paper mentions WS-AT a few times, but they have made it more clear that it that they assume strong knowledge of WS-AT. Reading WS-AT helped a lot!

## Future Work

- Expanding the simulation of byzantine behaviour.
  The current implementation of byzantine behaviour only covers a fraction of the byzantine faulty space.
  Expanding this could be interesting, but simulating more byzantine behaviours would have a large impact on code complexity.
  Ultimately, we believe simulating all possible byzantine behaviours is impossible.
  If we simulate anything less, we can only prove the system is not byzantine fault tolerant, not that it is.
- Running the system in a distributed manner: actors on different hosts should be able to communicate with each other.  

## Conclusion

To sum up, we managed to implement the protocol to the same extent as the paper, so not including view changes. The ability to tolerate some byzantine behaviour of a non-primary coordinator replica has been demonstrated. It has also been shown that the presence of this byzantine coordinator replica this does not affect the performance when tested in operation on a single machine.
