# TUDelft 2019 - Graduate Distributed Systems Course

## Implementing Paper: Rover toolkit
This group implemented the [Mobile computing with the Rover toolkit](https://ieeexplore.ieee.org/document/580429/) paper.

As our interpretation differs from the original paper, we chose a different name for the project: "Nomad".

## What
In our reading of the paper, we found it to be somewhat vague. It is our understanding that the paper is about an architecture and a corresponding toolkit to better illustrate the architecture. With the architecture being about encapsulating the application's Model+Data within special kind of objects called "RDO"'s (remotely defined object). 

We are not entirely sure how far "remotely defined" in "Remotely Defined Objects" means. We interpreted RDO's to be an object (containing data+functionality) whose definition is statically defined (is known a-priori to the client and server application) whose master data is then kept on some "server" that the "clients" retrieve. We are uncertain if the original definition of "remotely defined" also includes the function _implementation_ that is shipped together with the data. Our implementation can be extended to provide the latter by extending "data" to also contain executable code of the related RDO's functionality.

The RDO's methods originally are described as "Q(ueued)RPC's".

We implemented a framework for RDO's that is as follows: An RDO (```RdObject```) is a rich (high-level, domain-level) interface to the application's model that needs to be synchronized over multiple machines. The RdObject consists of two components: the interface and the state. An RDO implementation has access to its state and has a primitive to modify the state. Each modification happens through a supplied ```Op```  (short for operation, type in code: ```AtomicObjectState#Op```) based on the idea of Inversion of Control pattern. Each modification results in a new state (version). The RdObject's state is replaced with the resulting state. Each operation on the state generates a ```StateLog``` recorded operation on the state. When the RdObject is synced with the server (send current version and retrieve latest), only a diff between last-remote-version and current-local state log entries are exchanged. In effect, almost every log entry is an QRPC as we locally apply the change and queue it up for later transmission to the master version. Then remote, when receiving the logs then attempts to apply or "merge" them with its current version. Merge in this scenario describes the situation when the client and server's versions of the data have diverged. When this is the case, an application-provided implementation of a ```ConflictResolutionMechanism``` is provided.

The rover paper notes that any conflict resolution should happen on the server, and with this being a framework we have also included flexible merging as part of our design. Furthermore, the system can be extended with client-side conflict resolution to: 
    - offload the server (clients need to resolve when downloading changes, and cannot upload until local version is considered rebased on that of the remote)
    - enable human conflict resolution implementations
    - allow for extending with user consensus on merges or operations

The state log mechanism is inspired by the concept of event sourcing and git's model. The State log currently can handle three types of events: state is initialized (containing initial state), operation is applied (containing the operation that was applied and state on which) and a merge (parent states and used conflict resolution implementation).

We consider our conflict resolution implementation to be powerful enough to enable multiple "reasonably concurrent" users of same RdObject. The code in the repository includes a chatapp built as an RdObject to show this off.

When inspecting the code, the main area of focus should be the following:
 - ```RdObject```
 - ```AtomicObjectState```
 - ```StateLog```
 - ```ConflictResolutionMechanism```
 (todo: include some core domain-level client-server types/functions here, Steffan?)

## Authors
Lelekas, Ioannis; Louchtch, Philippe; Sluis, Steffan