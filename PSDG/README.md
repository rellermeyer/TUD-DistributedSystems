# PSDG

Pub/Sub Delivery Guarantees (PSDG) is a set of techniques that can be implemented in distributed publish/subscribe systems for message processing and delivery guarantees. Existing pub/sub systems that do not have these guarantees can only guarantee the delivery of messages to clients that are already known by all brokers. In the proposed algorithm, the brokers are connected to each other in an acyclic topology and messages are routed based on a reverse path forwarding scheme. In this implementation, the baseline without guarantees and the ACK based guarantee are implemented.

##  Distributed Systems - Group 4

- Remy Duijsens
- Kristóf Oláh
- Tomás Herńadez Quintanilla


## Requirements

To run the project: 

- Java 17
- Scala 2.13.8
- Maven

To run the simulation:

- Docker

To run the evaluation tool:

- Python 3 with ndjson, numpy and matplotlib installed

## Building the Project

From the project root folder run
`mvn -f ./pom.xml clean compile package`

## Building the Docker Environment

From the project root folder run
`docker-compose build`

## Running the Docker Environment

From the project root folder run
`docker-compose up`

## Doing multiple runs

In the .env file in the project root folder change the line to

`DIRECTORY_SAVE=./experiments/runs/runs_[TYPE]/run[X]`

where `[TYPE]` is either 'ack' or 'none', and `[X]` is the identifier of the run (1, ..., n).

After changing this setting, rebuild the docker environment and start the new run.


## Evaluating the data

The evaluation tool folder is located at `./experiments/evaluation`.

From this folder, run `python3 run_evaluation.py`. At least 2 runs are necessary to do the evaluation.

The results per run are stored in the `./experiments/evaluation/results` folder.
The accumulative results, namely the average and standard deviation over all runs, are located in `./experiments/evaluation/accumulative_results`.

If the chosen guarantee type is changed, line 6 of the `run_evaluation.py` file has to be changed.
Use `option = "ack"` for ack-based guarantee and `option = "none"` for no guarantee (baseline).

## Comparing the guarantee types

Copy the `ack.json` and `none.json` files from the `./experiments/evaluation/accumulative_results` folder to the `./experiments/graph_scripts` folder.
From this folder, run `python3 plot_chart.py`. The resulting graph will be saved in the folder.

## Changing the client simulation settings

In the `Client.scala` class the number of simulations and the guarantee type can be changed that will be used in the simulations.
The options are located at line 23 and 24.