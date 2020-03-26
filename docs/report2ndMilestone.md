# Byzantine Fault Tolerant Distributed Commit Protocol: Evaluation
**IN4391 Distributed Systems - group 7**  
  
We are Michael Leichtfried, Douwe Brinkhorst, Patrik Kron and Miguel Lucas, and here we will describe how we are going to evaluate the BFTDC Protocol system described in *"A Byzantine Fault Tolerant Distributed Commit Protocol"* by Wenbing Zhao (Department of Electrical and Computer Engineering, Cleveland State University).
## Setup
Along with the development we have built a set of tests which tested every feature we implemented. This way we ensured that every module did its work properly.
We have built a total of 11 tests through which Coordinators and Participants exange messages and perform the corresponding message verification and decision making processes. These tests ensure the implementation correctness by creating protocol instances and making coordinators and participants conduct several distributed commit protocols. Different number of coordinators and participants is used to test  the system's resilience to multiple message passing. Faulty participant behaviour is also tested by sending abort messages in the middle of a commit transaction.
### Tests

 **Test 1:** Initiate the protocol and commit with 1 coordinator and 1 participant.
 **Test 2:** Initiate the protocol and commit with 4 coordinators and 1 participant.
 **Test 3:** Initiate the protocol and commit with 1 coordinator and 4 participants.
 **Test 4:** Initiate the protocol and abort with 1 coordinator and 1 participant.
 **Test 5:** Initiate the protocol and abort with 4 coordinators and 1 participant.
 **Test 6:** Initiate the protocol and abort with 1 coordinator and 4 participants.
 **Test 7:** Initiate the protocol with 1 coordinator and 1 participant and make the participant abort the transaction.
 **Test 8:** Initiate the protocol with 1 coordinator and 5 participants and make one participant unilaterally abort the transaction.
 **Test 9:**  Initiate the protocol with 4 coordinators and 5 participants and make one participant unilaterally abort the transaction.
 **Test 10:** Initiate 2 instances of the protocol in parallel and succeed committing in both.
 **Test 11:** Initiate the protocol with 1 coordinator and 1 participant and before the transaction is committed, send an abort message to coordinator to simulate a failure in the particpant.

  
