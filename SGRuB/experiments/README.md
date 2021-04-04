# SGRuB

## Requirements

* Scala 2.12.* (2.12.12+)
* JVM 1.8 compatible JDK
* SBT 1.4.9+
* geth 1.10.1+
* Python3.6+

## Run experiments

Start the private network and miner, follow the instructions in `geth_private/README.md`.

There is no need to setup contracts beforehand as in the `src`, contract addresses are created clean per experiment.

There are 8 different tests currently.
```
0: Example experiment 
1: Experiment: Write X Bytes 
2: Experiment: Write Batches with X Keys of 1 Byte
3: Experiment: Write X Bytes per Y Keys (even/random distributed)
4: Experiment: gGet cost with(out) replica
5: Experiment: Deliver cost
6: Experiment: Static Baselines
7: Experiment: gGet cost with(out) replica, specific range
```

Experiment 0 is an unlisted example experiment to show the workings of an experiment case.

Experiment 1 is an experiment in which one can provide an array and the experiment will write each element of 
the array as byte size to the blockchain for non-replicating and replicating.

Experiment 2 is an experiment in which the user provides two different arrays. 
The first array (X) is the amount of keys with the value of 1 byte will be put on the blockchain.
The second array (Y) is the replication amount of the X. 
The length of X and Y must be equal.

Experiment 3 is an experiment to get the write costs of different X bytes per Y keys
First you are asked to provide the amount of byte test cases you want to perform, and afterwards the stepsize of this array.
Secondly you are asked to provide the amount of key test cases you want to perform, and afterwards the stepsize of this array.
The experiment will then loop through each testcase and create a case in which you write X bytes on each of the Y keys.
The experiment is conducted for both the replica and non-replica and also the bytes can be evenly and randomly distributed over the keys.

Experiment 4 is an experiment to retrieve the gasCost of the gGet function for replica and non-replica.
The user is required to provide an array of byte sizes and the test case will then retrieve each element with the given byte size.

Experiment 5 is an experiment is similar to experiment 4, an array is asked and get operations are performed. 
But instead of the get Gas cost, the deliver Gas cost is measured for the non-replicating baseline.

Experiment 6 is the static baselines experiment in which multiple writes and reads can be performed to calculate the Gas cost per operation in comparison to the read-to-write ratio.
The experiment is performed for both the replica and non-replica.

Experiment 7 is similar to experiment 4, however the user is asked to choose between replicating and non-replicating, 
while experiment 4 will always perform both.

Running the experiments will create a new `.csv` file in the `experiments/results` folder. If the experiment setup is exactly the same as a previous experiment then it will be overwritten.


## Run plots
The plots are created with Python. They are located in `experiments/plots`.
To run, for example, the `plotA.py` it is sufficient to open a terminal with `python plotA.py` to generate the plot.

The plots have the result data already imported in the code and it is not required to run a experiment as the `.csv` is not required beforehand. 

The output of the plots are created in the `experiments/plots` folder. The graphs are printed to the screen and saved as a `.pdf` file that can be used properly in a report.