# Kelips

Kelips is a peer-to-peer distributed hash table system in which file look-ups are resolved in $O(1)$ complexity and membership changes are resolved  quickly. For the above mentioned performance, memory usage and constant background communication overheads are tolerated. In Kelips nodes are grouped into $k=\sqrt{n}$ affinity groups. Nodes use a gossiping mechanism to distribute information between nodes in affinity groups and across groups through contacts.

---

## Core Features of the System
  - O(1) Lookup time for files in the Kelips DHT (and multi-hop routing)
  - Insert files into the Kelips DHT
  - AVL Tree for storing file tuples
  - Algorithm for creating k=√n Affinity Groups
  - Gossiping/heartbeats protocol
  - Joining protocol
  - Contact replacement policy
  
  
## Plan of Attack

  1. Create basic implementation of a node
  2. Create message passing functionality
  3. Create file insertion functionality
  4. Create file lookup functionality
  5. Create gossiping protocol
  6. (optional) Joining protocol
