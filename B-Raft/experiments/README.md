# BRaft Experiments
To quantitatively assess the quality of our BRaft implementation run our cluster under different circumstances with different configurations. 

This is done in run_experiments.py.
At the bottom of this file one can specify what experiments to run and how many times to replicate these.

The script then creates the different configurations and call BRaft Main with them, this starts our cluster. Logs are captured as they contain an accurate view of our system at any time. These logs are then parsed to collect metrics and draw conclusions.

## Configuring experiments
Through the `experiment_config` dict all parameters in BRaft's [`application.conf` (link)](../src_/main/resources/application.conf) raftPrototype key can be controlled. These are:

```editorconfig
raftPrototype {
	raftType="BRaft"

	electionTimerIntervalMin=3

	electionTimerIntervalMax=4

	heartbeatTimerInterval=1

	nodes=21

	crashIntervalHeartbeats=1000000

	sleepDowntime=8

	maxTerm=9999
}
```

### example
By setting 
```python
    num_replications = 4
    experiment_config = {
        "raftType": ["Raft", "BRaft"],
        "nodes": np.linspace(start=3, stop=21, num=9, dtype=int),
    }
```
in `run_experiments.py` 4 runs of experiments are done with every combination of nodes and raftType specified. This particular config will thus result in 2 * 9 * 4 = 54 output logs.


## Log parsing and plotting
Can be found in the [Plotting notebook](DS%20Graphs.ipynb)

