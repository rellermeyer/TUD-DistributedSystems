# Experiments

The code for measuring the performance of the implementation differs ever so slightly. The "updated" code can be found under `experiments/src` and can be build and run in a similar manner as described in the README in the root of this project.

The Scala files of most interest are `monitor.MonitorPerformanceMeasurement.scala`, which contains the actual code that was used to run the expirement and `monitor.ExpressionGenerator` which generates a random arithmetic expression given the number of operations that should be included in it.

`experimentData.txt` is our obtained result from running this experiment. The images in this folder made in MatLab based on this data.
