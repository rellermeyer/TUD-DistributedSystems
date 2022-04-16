## IN4391 Distributed Systems Group 12

### Authors:

- Aditya Shankar
- Miruna Betianu
- Rohan Madhwal
- Theodoros Veneti

# Archipelago - Algorithms for Leaderless Consensus

This project contains the implementation of the [The first two Archipelago algorithms](https://ieeexplore-ieee-org.tudelft.idm.oclc.org/document/9546485).

Archipelago is a series of three novel leaderless algorithms which generate consensus in a distributed system of nodes without electing a leader node. The first algorithm relies on shared memory and a new variant of the the classical adopt-commit object
that returns maximum values to help different processes converge to the same output. The second algorithm is a generalisation of the first in a message passing system with omission failures.


## Running the implementation
In order to run the implementation, a computer with docker pre-installed is required.
Steps to run `oft-archipelago`:
1. Clone the repository
2. Build the docker image using `docker build -t ds .`
3. Run the image in a container with `docker run ds`

In order to run `archipelago` instead, please replace the last line of `Dockerfile` with `CMD ["sbt", "runMain archipelago.Main"]`

## Experiments
The results of our experiments are inside the `experimental_results` folder

In order to replicate any of the results, change the parameters to the experiment's parameters in `main/scala/archipelago/archipelago` 
for `archipelago` experiments and `main/scala/oft_archipelago/oft_archipelago` for `oft_archipelago` experiments and rebuild the docker image as above.
