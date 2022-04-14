# Mutual Inconsistency Detection In Distributed Systems Group7

## Authors
<ul>
  <li> Adam Kadiev </li>
  <li> Mohamed Rashad </li>
  <li> Hanzhang Lin </li>
  <li> Kevin Nanhekhan </li>
</ul>

## Requirements

- Scala 2.13.1
- JVM 1.8 compatible JDK
- Python 3.8+
- SBT 1.6.2
- Docker

## Build

Use the following command to build the docker image:

```
docker build -t group-7-midd .
```

## Run customized experiments

You can choose to run the akka system with a sequence of operations defined by yourself, by simply passing the
arguments in the right format in sbt shell. The format of the expected commands that can be issued against the akka system in the sbt shell is the following (<ARG ...> indicates a required arguments to be given, <ARG OPTIONAL ...> is not required but a value can be given if required):

```
upload-<ARG: siteName (must exist)>-<ARG: fileName>

update-<ARG: the site name that will update the file>-<ARG: origin pointer of the file to update>

split-<ARG: site name where the split needs to happen>-<ARG OPTIONAL: timeout in ms after and before split>

merge-<ARG: sitename from which to send filelist to other partition>-<ARG: siteName that should get the filelist from the sending siteName>-<ARG OPTIONAL: timeout in ms after and before merge>
```
NOTE: origin pointer has the format `(siteName,timestamp)` including the parentheses and the comma.

Steps and examples are given below:

Run/Create sbt shell from the created image:

```
docker run -it --rm group-7-midd ./sbt
```

Create and run 24 sites in the distributed system using `run` command in sbt shell. This creates 24 sites with the names Site0, Site1, ..., Site23.

```
> run 24
```

Upload a file called `test.txt` to site 5:

```
> upload-Site5-test.txt
```

The origin pointer will be printed in the console so that it can be copied and pasted when the update command needs to be issued.

split the siteList into two partitions at site 10, with timeout set to 1000ms by default (optional argument), to give enough time to make sure the file list in each site is consistent. The thread is slept using the timeout value before and after the split in order to make sure that all the pending messages are done executing.

```
> split-Site10-1000
```

or simply

```
> split-Site10
```

Will result in the following partition list:  

**List(Set(Site0, Site1,.... Site23)) ---> List(Set(Site0, Site1,.... Site10), Set(Site11, Site12, ... Site24))**

Now splitting the partition list into two partition sets at site 15, with timeout 2000ms

```
> split-Site15-2000
```

Results in the following partition list:

**List(Set(Site0, Site1,.... Site10), Set(Site11, Site12, ... Site24)) ---> List(Set(Site0, Site1,.... Site10), Set(Site11, Site12, Site13, Site14, Site15), Set(Site16, Site17, ... Site24))**


> Note that after the split, two new partition sets will be generated:
>
> > { siteName <= given `siteName` (lexically) }
> >
> > { siteName > given `siteName` (lexically) }
>
> if the given `siteName` is already the largest in the current partition, or it is not a valid
> siteName in the current system, nothing will happen.

Update the file with origin pointer `(siteName,timestamp)` e.g. (Site12,90300) in site 12 can be done using the following command:

```
> update-Site12-(Site12,90300)
```

To merge, for example, the partition sets `{Site11, Site12, Site13, Site14, Site15}` and `{Site16, Site17, ... Site24}` then one of the sites in one of the partition sets needs to send its own filelist to a site in the other partition set. For instance, site 12 sends its file list to site 20, so that site 20 can check if its file list is consistent with that of site 12,
and deal with the inconsistencies if there are any. This can be done using the following command:  
```
> merge-12-20
```
Just like for the `split` command, also here a timeout can be given:
```
> merge-12-20-1500
```
The expected result from this command is the following:

**List(Set(Site0, Site1,.... Site10), Set(Site11, Site12, Site13, Site14, Site15), Set(Site16, Site17, ... Site24)) ---> List(Set(Site0, Site1,.... Site10), Set(Site11, Site12,.... Site23))**

Lastly, to quit the session and go back to the sbt shell, the following command can be given:
```
  quit
```
  
### Run Experiments

To run a specific experiment, replace `<ExperimentName>` with the experiment that you want to run in the following docker command:

```
docker run -it --rm --mount type=bind,source=${PWD}/experiments,target=/MIDD/experiments group-7-midd ./sbt "testOnly *<ExperimentName>"
```
e.g.
```
docker run -it --rm --mount type=bind,source=${PWD}/experiments,target=/MIDD/experiments group-7-midd ./sbt "testOnly *ExperimentTimestamps"
```

Use the following command to run all the experiments at once.

```
docker run -it --rm --mount type=bind,source=${PWD}/experiments,target=/MIDD/experiments group-7-midd ./sbt test
```

There are two experiments: `ExperimentTimestamps` and `ExperimentVersionVector`. For both of the experiments, the values `spawningActorsTimeout = 100ms`, `timeoutSplit = 100ms`, `timeoutMerge = 100ms`, `thresholdSplit = 20` and `thresholdMerge = 20` are pre-defined constants and the only changing variable is the `numSites` value which indicates the sites that need to be spawned. 

`spawningActorsTimeout` indicates the timeout that is used for waiting for all the sites to be spawned to ensure all sites are spawned for all consequent operations. `timeoutSplit` and `timeoutMerge` are used for the timeouts for before and after a split and merge respectively. There are two counters, one that keeps track of how many splits have occurred and one that keeps track of how many merges have occurred during a run. Each time a merge or a split occurs, the corresponding counter is incremented. The `thresholdSplit` and `thresholdMerge` values are used to stop a run of an experiment when those values are reached by one of the aforementioned counters. This is done so that a run does not go on indefinitely.

The experiments are run 10 times for each value of `numSites`. `numSites` is 2 initially (1 is not possible since the experiments will not terminate as there will be no splits and merges occurring with 1 site) and the value gets incremented by 1 until it reaches 20 sites which stops the experiment. During a run, which operation (upload, update, merge or split) is performed depends on a random value. If the random value falls in one of the intervals of an operation, then the corresponding operation is performed. This keeps going like this until either the `thresholdSplit` or the `thresholdMerge` are reached. To make sure that the same operations are performed by both algorithms in each run, a seed is set for the random number generator. 

Each time a merge is performed, a check is done for inconsistencies. The amount of inconsistencies detected is written to a file. Format of the filename used for timestamp experiment inconsistencies results: `run_<num_run>_timestamps_sites_<num_sites>_icd.txt`. Format of the filename used for version vector algorithm experiment inconsistencies results: `run_<num_run>_version_vector_sites_<num_sites>_icd.txt`.

Lastly, at the end of each run the execution time (in ms) is printed to the console and written to a file. The format used for the timestamp execution time filename: `run_<num_run>_timestamps_sites_<num_sites>_exec.txt`. The format used for the version vector execution time filename: `run_<num_run>_version_vector_sites_<num_sites>_exec.txt`.

All the generated results are stored in the `results` folder under the `experiments` folder. To be able to visualize the results and error bars in the form of plots, a csv file can be created from the generated files under `results`. This can be done by running the `ResultsFormat.scala` under `experiments/scala/com/akkamidd` folder. It will generate separate csv files for both algorithms under folder `experiments/csv_format` containing the columns `sites, run, icd, exec` and the values of each run as rows. The generated csv files can then read by jupyter notebook file called `plot.ipynb` under `experiments` folder which is responsible for plotting the values and the error bars. Lastly, one-way ANOVA test is conducted in `plot.ipynb` on the values to see whether the inconsistency and execution time results between the algorithms are significant or not.

There is also a python script called `plots_and_anova.py` that does the same thing as `plot.ipnyb` under `experiments` folder. It is created in case the plots and anova tests need to be generated without using jupyter notebook.

In order to have the required libraries' setup for running the script, follow these steps:

1. Execute the following command in `experiments` folder to create the virtual environment 
```
python3 -m venv venv
```
2. Activate the virtual environment

In Windows:
```
venv\Scripts\activate
```
In Linux:
```
source venv/bin/activate
```
3. Install the required libraries using `requirements.txt` file in `experiments` folder
```
pip3 install -r requirements.txt
```
4. Run the `plots_and_anova.py`
```
python3 plots_and_anova.py
```

The script will generate two images containing the figures/plots for inconsistencies and execution times and it will save them in `experiments` folder. Also, the one-way ANOVA test results are printed to the console.
