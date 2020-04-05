---
title: "WIP: Final Report: Byzantine-CP"
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

# Implementation [&uarr;](./../README.md)

In the past weeks we have implemented the system described in *"A Byzantine Fault Tolerant Distributed Commit Protocol"* by Wenbing Zhao (Department of Electrical and Computer Engineering, Cleveland State University)[^1].

## Introduction

In the paper the author describes a commit protocol for transactions which run over untrusted networks. The protocol consist of duplicating the coordinator and running a Byzantine agreement algorithm among the coordinator replicas. This protocol tolerates byzantine coordinator and faulty participant behaviour.
The Two Phase Commit (2PC) protocol addresses the issue of implementing a distributed commit protocol for distributed transactions, and different approaches have been conducted in order to make it support byzantine behaviours. This paper's motivation was to improve these 2PC byzantine behaviour approaches.
A distributed transaction is a transaction that is performed in multiple machines across a computer network. The transaction is only committed if all operations succeed, and it is aborted if any operation fails.

## Distributed commit protocol

According to 2PC protocol a distributed transaction contains one coordinator and some participants, but in the byzantine distributed commit protocol several coordinators are used. We have used the **akka** framework to implement coordinators and participants as actors since it simplifies distributed and concurrent application development. Actors communicate with each other through messages using the akka API. These messages are signed using public key technology so that no unidentified participant can interfere. It is assumed that there will exist *"3f + 1"* available coordinator replicas where "*f*" is the maximum number of byzantine coordinator replicas.
In the byzantine distributed commit protocol the original coordinator is called primary and coordinator copies receive the name of replicas. Every participant must register with the coordinator before the commit protocol starts. The commit protocol starts when a replica receives a commit request from a participant, which from now on will be called initiator. Now the coordinator replica sends a *"prepare"* request to every registered participant and waits until enough *"prepared"* messages are received from the participants. When *"prepared"* messages are received an instance of a *"Byzantine Agreement Algorithm"* is created. After reaching an agreement, coordinator replicas send  the agreement outcome to participants, which will only commit the transaction once *"f + 1"* similar outcomes are received. This way at least one of the *"f + 1*" outcomes received comes from a non-byzantine replica.

## Byzantine Agreement Algorithm

Wenbing Zhao's  algorithm is based on the BFT algorithm by Castro  and Liskov. Byzantine Agreement Algorithm differs from BFT because BFT aims to agree on the ordering of the requests received while the Byzantine Agreement algorithm's objective is to agree on the outcome of a transaction.
Byzantine Agreement Algorithm has three main phases:

- **Ba-pre-prepare phase**: in this phase the primary sends a "*ba-pre-prepare"* message to all other replicas. The *"ba-pre-prepare"* message contains the following information: view id, transaction id,  transaction outcome and decision certificate. The decision certificate is a collection of records of each participant's vote for every transaction. A new view is created if the *"ba-pre-prepare"* message fails any verification (signed by the primary, coherent transaction and view and has not accepted a *"ba-pre-prepare"* in this view-transaction). Once a replica is ba-pre-prepared it multicasts a "pre-prepared" message to all other replicas.

- **Ba-prepare phase**: a *"ba-prepare"* message contains the view id, transaction id, digested decision certificate, transaction outcome and replica id (*"i"*). The message is accepted if it is correctly signed by replica  *"i"*, the receiving replica's view and transaction id match message view and transaction's id, message's transaction outcome matches receiving replica transaction outcome and decision certificate's digest matches the local decision certificate. When a replica collects 2f matching *"ba-prepare"* messages from different replicas it can make a decision for the current transaction and sends a *"ba-commit"* message to all other replicas.

- **Ba-commit phase**: a *"ba-commit"* message contains the view and transaction id, decision certificate's digest, transaction outcome and sender replica id. A replica is said to have ba-committed if it receives 2f+1 matching *"ba-commit"* messages from different replicas and the agreed outcome is sent to every participant in the current transaction. *"Ba-commit"* messages are verified alike *"ba-prepare"* messages. **View changes with timeouts and so missing**

## Objectives

We set objectives from the beginning in order to figure out and organise the work that had to be done. The objective list was divided into categories to state the priority of each objective.

- **Must have:** features the project must have in order to fulfill the basic requirements.
  - Implementation for coordinators.
  - Implementation for participants/initiators.
  - System testing infrastructure, including coordinator byzantine behaviours testing.

- **Should have/Could have**: features that might be implemented depending on time constraints.
  - Distributed deployment: test the system in multiple interconnected machines to simulate a realistic environment.
  - Message signing and signature checking.

- **Could have/Will not have**:
  - View change mechanism (this feature was not implemented by the paper authors either).

## Evaluation
All tests were performed with 4 coordinators. In each test, 100 transactions were committed. From this, the average latency and throughput were calculated.  
2 participants:  
Average latency (ms): 382
Throughput (transactions/s): 2.6156101  
4 participants:  
Average latency (ms): 434  
Throughput (transactions/s): 2.3037758  
6 participants:  
Average latency (ms): 569  
Throughput (transactions/s): 1.754817  
8 participants:  
Average latency (ms): 614  
Throughput (transactions/s): 1.6273392  
10 participants:  
Average latency (ms): 822
Throughput (transactions/s): 1.2165303

## Footnotes

[^1]: https://ieeexplore.ieee.org/document/4351387
