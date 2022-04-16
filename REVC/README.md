# TU Delft - Distributed Systems

## REVC Implementation

The original paper can be found [here](https://ieeexplore.ieee.org/document/9234035).

## How to run

### Running experiments
Requirements: Scala v2.13.8 and sbt v1.6.2

The main entry point is [Runner.scala](./src/main/scala/Runner.scala). Run with `sbt "run <print bitsizes: true/false> <number of messages per child: int> <number of actors: int> <clocks separated with commas: str>"`.

### Reproducing the exact results and plots used in the report
Requirements: Python3 with pandas, pandas, and matplotlib installed, and also the sbt-assembly plugin (should get installed automatically).

Run the [run.sh](./experiments/run.sh) script: `./experiments/run.sh`
