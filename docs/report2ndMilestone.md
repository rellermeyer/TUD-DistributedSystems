---
title: "Report Milestone 2: Byzantine-CP"
author: [Douwe Brinkhorst, Patrik Kron, Michael Leichtfried, Miguel Lucas]
date: "2020-03-27"
subject: "Distributed Systems (Group 7)"
coursecode: "IN4391"
keywords: [distributed, commit protocol, atomic commit, byzantine]
lang: "en"
titlepage: "true"
titlepageTUDelft: "true"
papersize: "a4"
---

# Evaluation [&uarr;](./../README.md)

In this report we will describe how we are going to evaluate the BFTDC[^1] Protocol system described in *"A Byzantine Fault Tolerant Distributed Commit Protocol"* by Wenbing Zhao (Department of Electrical and Computer Engineering, Cleveland State University).

## Functional requirements

- Basic Committing
- Aborting
- Unilateral aborting
- Byzantine behavior

The functional requirements were evaluated using Scala tests (```ScalaTestWithActorTestKit```).

Along with the development we have built a set of tests which tested every feature we implemented. This way we ensured that every module did its work properly.  
We have built a total of 14 tests through which Coordinators and Participants exange messages and perform the corresponding message verification and decision making processes. These tests ensure the implementation correctness by creating protocol instances and making coordinator replicas and participants conduct several distributed commit protocols. Different number of coordinator replicas and participants is used to test  the system's resilience to multiple message passing. Faulty participant behaviour is also tested by sending abort messages in the middle of a commit transaction.

The following tests were implemented using tests in/with Scala/Akka:

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

Further evaluations planned in the near future:

- measuring throughput and latency in continuous operation. For this, it might be interesting to implement a commit payload, as well as running the system in some distributed fashion (giving each actor its own JVM would help to show some of the distributed difficulties/overhead).  

## Non-Functional Requirements

Further evaluations planned in the near future:

- measuring throughput and latency in continuous operation. For this, it might be interesting to implement a commit payload, as well as running the system in some distributed fashion (giving each actor its own JVM would help to show some of the distributed difficulties/overhead).  

## Additional features

Implementing view changes is something we're working on.  

## Footnotes

[^bftdcp]: BFTDCP Byzantine Fault Tolerant Distributed Commit Protocol
