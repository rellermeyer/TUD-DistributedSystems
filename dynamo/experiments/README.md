# Experiments

## Instructions
In order to run the experiments you need to run the `BenchmarkSpec` scala file. To simply run this immediately run:

```sbt "testOnly dynamodb.benchmark.*"```

This will output the benchmark results in stdout. It will print the mean and standard deviation of the latencies. Furthermore it will print out all latencies in a comma separated array (between square brackets).

This array can then be visualized using our plotting script located in this folder in `main.py`. 
First copy the array to any other python file or to `main.py` itself and construct a numpy array from it. 
Then pass it to the `plot_percentile` function. If you want to compare multiple latency distributions use the `plot_percentile_multiple` function which takes in a key value (dict) of (int, numpy array) pairs.

Note that the script uses several Python packages defined in `requirements.txt` so first install those using:

```pip install -r requirements.txt```

The results of our runs are defined in `local.py` for the local runs and `cluster7.py` for the cluster running on 7 nodes.