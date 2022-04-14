# IN4391-2022-group6

Repository for the project for distributed systems course at the TUDelft (IN4391)
We present our Akka based implementation of Zeus: Locality-aware Distributed Transactions by Katsarakis et al., 2021, a distributed transaction protocol that is able to process millions of transactions per second in a reliable way and can theoretically outperform traditional distributed transactions protocols. The main purpose of this repository is to demonstrate that our implementation is able to achieve similar performance as described in the original paper and discuss how this was achieved.

## How to run
Running the implementation is done by running the 'Zeus.scala' file. 
This needs to have a transaction file in order to run. Which needs to be set on line 280. This repo includes an example of such a file in the resources folder called 'simpletestcasexx.csv'
More resource files can be found at this link as they are too big to add to github: https://drive.google.com/file/d/1ZJmC941O4Gn7RlF66IWwDcXATiJfCHB8/view?usp=sharing
