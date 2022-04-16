# [A Byzantine Fault-Tolerant Raft Algorithm Combined with Schnorr Signature (B-Raft), [1]](https://ieeexplore.ieee.org/document/9377376)
## Implemented in Scala for IN4391 Distributed Systems (2021/22 Q3)
Group 10: Julian Biesheuvel, Riley Jense & Pepijn te Marvelde

## General Information
This repo contains the code for the project of the Distributed Systems course. It is an implementation of the B-Raft with Schnorr Signature [[1]](###-bibliography), based on a prototype Raft implementation by Max Bundscherer [[2]](###-biblography). Akka Actors are used for communication between nodes. 

## File Structure
Source code for the Raft/B-Raft implementation is found in [`src` (link)](src). Experiments, including result dataset and plotting code, are found in [`experiments` (link)](experiments). More info on each folder is found in their respective `README.md`'s.



### Bibliography
1.  S. Tian, Y. Liu, Y. Zhang and Y. Zhao, "A Byzantine Fault-Tolerant Raft Algorithm Combined with Schnorr Signature," 2021 15th International Conference on Ubiquitous Information Management and Communication (IMCOM), 2021, pp. 1-5, doi: 10.1109/IMCOM51814.2021.9377376.
2. M. Bundscherer, "(Prototype) Raft Consensus Algorithm in Scala," Github repository, 2020, https://github.com/maxbundscherer/prototype-scala-raft