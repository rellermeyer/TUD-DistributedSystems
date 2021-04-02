# WASP - Benchmarks

In order to run all the experiments at once (which will take several hours), run the following command

```bash
python3 experiments/run_exp.py
```

which will generate a .csv file of all the collected data and a .pickle log, which can then all be plotted with

```bash
python3 experiments/plot.py
```

and all the resulting plots will appear in the plots directory inside the experiments.

## Experiement 1

### Experimental Design

This experiments measures the runtime of the query for different data sizes, comparing the perforamnce of replanning the physical plan when the bandwidths of the task managers changes (happens every 10 seconds) and not replanning the physical plan.

The experiment runs with 8 Task Managers with each having 3 available task slots, 3 operators for the query (map, map, and a reduce), and a total parallelism of 13 that is split into (7,5,1) for the respictive operation.

All experiments are adjusting metric values every 10 seconds based on the values in the [config-12.json](/config-12.json), generated with the [metadataGen.py](/src/metadataGen.py) script.

### Execution Commands

```bash
# Starting Job Manager and Task Managers with replanning
sbt "runMain jobmanager.JobManagerRunner 8 -replan"
# Then starting the query
sbt "runMain SampleQueryRunner"

# Starting Job Manager and Task Managers without replanning
sbt "runMain jobmanager.JobManagerRunner 8 -noreplan"
# Then starting the query
sbt "runMain SampleQueryRunner"
```

For the runs adjust the size of the data for the query by changing `val dataSize` in the [SampleQueryRunner.scala](/src/main/scala/jobmanager/SampleQueryRunner.scala) on line 12 to:

- 4000
- 8000
- 16000
## Experiment 2

### Experimental Design

This experiment measures the performance of the adaptive scheduler when different parallelism for each operator is used. We compare running the original physical without replanning (when the bandwidths change every 10 seconds between the task managers) and compare it to replanning the physical plan when the bandwidth changes (or not replanning it when the optimal plan with the new bandwidths is the exact same).

In the setup, we are running 8 task managers, each having 5 abailable task slots, the query will run with 3 operators (map, map, and reduce) with varying parallelism, and the data size will remain constant at 4000 (ensure that the `val dataSize` in the [SampleQueryRunner](/src/main/scala/jobmanager/SampleQueryRunner.scala) on line 12 is set equal to 4000).

### Execution Commands

```bash
# Starting Job Manager and Task Managers with replanning
sbt "runMain jobmanager.JobManagerRunner 8 -replan"
# Then starting the query
sbt "runMain SampleQueryRunner"

# Starting Job Manager and Task Managers without replanning
sbt "runMain jobmanager.JobManagerRunner 8 -noreplan"
# Then starting the query
sbt "runMain SampleQueryRunner"
```
