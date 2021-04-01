# WASP - Benchmarks

<!-- TODO Add final plots -->
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

### Results

<!-- #ops = 3, #par_total = 13 (7,5,1) -->
<!-- 8 task managers, 5 available task slots each -->
| Type     | Data Size | Runtime (ms) | Nr Replans |
| -------- | --------- | ------------ | ---------- |
| NOREPLAN | 4000      |    31549     |            |
| REPLAN   | 4000      |    15956     |            |
| -------- | --------- | ------------ | ---------- |
| NOREPLAN | 8000      |    53210 - 53150  - 53129   |            |
| REPLAN   | 8000      |    52837 - 43731 - 29510 - 22664 - 22149 |      2      |
| -------- | --------- | ------------ | ---------- |
| NOREPLAN | 16000     |    94307     |            |
| REPLAN   | 16000     |    68063     |            |

## Experiment 2

### Experimental Design

This experiment measures the performance of the adaptive scheduler when different parallelism for each operator is used. We compare running the original physical without replanning (when the bandwidths change every 10 seconds between the task managers) and compare it to replanning the physical plan when the bandwidth changes (or not replanning it when the optimal plan with the new bandwidths is the exact same).

In the setup, we are running 8 task managers, each having 5 abailable task slots, the query will run with 3 operators (map, map, and reduce) with varying parallelism, and the data size will remain constant at 4000 (ensure that the `val dataSize` in the [SampleQueryRunner](https://github.com/nicktehrany/WASP/src/main/scala/jobmanager/SampleQueryRunner.scala) on line 12 is set equal to 4000).

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

### Results

<!-- 8 task managers, 5 available task slots each, data size 4000-->
| Type     | Parallelism  | Runtime (ms) | Nr Replans |
| -------- | ------------ | ------------ | ---------- |
| NOREPLAN |  10 (7,2,1)  | 33861      |            |
| REPLAN   |  10 (7,2,1)  | 30063 - 30066 - 29967 - 29877 - 29980                 | 2, 2, 2, 2, 2
| -------- | ------------ | ------------ | ---------- |
| NOREPLAN | 20 (12,7,1)  |         |            |
| REPLAN   | 20 (12,7,1)  |         |            |
| -------- | -----------  | ------------ | ---------- |
| NOREPLAN | 30 (17,12,1) |      |            |
| REPLAN   | 30 (17,12,1) |    |            |
| -------- | ------------ | ------------ | ---------- |
| NOREPLAN | 40 (22,17,1) |         |            |
| REPLAN   | 40 (22,17,1) |         |            |