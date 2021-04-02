# WASP Implementation

## Design

Our [original design](#Original-Design) is explained below, and was meant to be built with Apache Flink, however we soon realized that there was no easy way to replace the default scheduler inside of Flink. Therefore, we decided to implmenet our own scheduler, which resulted in a slightly [adapted design](#Adapted-Design) explained below.

### Original Design

The design is based on expanding Apache Flink [[1]](#1) with parts of the optimizations presented in WASP [[2]](#2). A visual representation of the full WASP implementation is depicted in [Figure 1](#FigDesign), with components that we are implementing in green, components that we are simulating in yellow, and components that we are not implementing as red.

The main component of our implementation is the Scheduler. The WASP paper does not describe a method to distribute the tasks among the nodes, but it does provide an instance of an Integer Linear Program problem. Solving said problem gives a result of how many tasks to run on every data center (node), depending on the current state of the system (bandwidths, latencies, available computation slots). Our Scheduler will schedule the tasks based on this result. We will use the Gurobi Optimization Tool [[3]](#3) to solve the ILP problem.

To make our scheduler adaptive (react to changes in bandwidth, computational power, etc.) we also implement the Reconfiguration Manager. This component decides whether a bottleneck is present and, based on some heuristics, decides what kind of action to take. 

The decided action from the Reconfiguration Manager is used as input by the Scheduler to calculate a new execution plan for the query(s) currently running on the system.

Initially, we will simulate all monitoring components, as well as the actual execution of queries on the data centers. We will use Flink to provide queries and streams of data to our Job Manager.

Due to time constraints for completion of this project, we do not consider checkpointing. If a task is rescheduled, it must be executed again from the beginning.

The WASP paper describes three techniques to adapt the execution plan to the state of the system: Task re-assignment, Operator scaling, and Query re-planning. Our Scheduler and Reconfiguration Manager components implement the first two techniques. We decided to leave out Query re-planning due to time constraints, and only consider the one logical plan we receive from the simulated Query Planner.

![WASP Design](images/WASP_Design.png)

<a id="FigDesign">*Figure 1:*</a>
*A visual representation of the WASP implementation, with components that we are implementing in this project depicted in green.*


#### Functional Requirements

* The system should be able to identify the bottlenecks and take the appropriate actions as mentioned below.
    * Reconfiguration due to computational resources - If the computational resources are the bottleneck for a particular operator, the algorithm should first attempt to scale up (increase resources within the same site), and then scale out (distribute the workload on a greater number of sites). On the other hand, if excess resources are allocated to an operator, the algorithm should scale down.
    * Reconfiguration due to bandwidth/latency - If the upstream bandwidth of a particular node is the bottleneck, the upstream node should limit the stream sent to that node. The plan should be restructured to utilize links with higher bandwidths.
* Reconfiguration should be possible, not only at the start of each execution, but also when the execution is in progress.

#### Non-Functional Requirements

* Data quality - No data degradation (no throwing away parts of the stream if it cannot be processed fast enough or sacrificing accuracy, only as last resort).
* Streaming Data - The system should handle data that continuously flows to it at different speeds.
* Scalability - Nodes shall utilize resources within a certain boundary (not going above or below a certain threshold). Parallelism can be increased by scaling up/out the operators.
* Distribution - Distributing tasks to be processed amongst different machines on multiple data centers.
* Processing Guarantees - The submitted query shall be executed, and the result retrieved within a certain finite amount of time.
* Flow Control - The system should be able to handle effectively the nodes work overload.

#### Origianal Evalutaion Plan

To evaluate the execution of the adaptive strategy, we submit a number of queries and measure how well it performs. The queries only consist of simple stateless tasks. 

At specified intervals, we introduce changes to the input stream rate, bandwidths of different links, and the computational power of different data centers.

We will use the same metrics as the paper, which are 
1. The delay in seconds from submitting the query and obtaining the results.
2. The ratio between the stream input rate and the processing rate.

The baseline is the scheduler without reconfiguring any of the task placements after the initial schedule vs. WASP reconfiguring task placement based on collected metrics from the data centers.

We use the Yahoo Streaming Benchmark (YSB) [[4]](#4) to asses our system under large workloads.

### Adapted Design

Due to Flink not having an easy way of implementing a custom scheduler, we resorted to implementing our own scheduler, which will create a physical plan based on the task placement for slots on each task manager from the ILP, as is explained in the WASP paper. The altered design is depicted in [Figure 2](#FigDesignAdapted)

![Adapted WASP Design](images/WASP_design_adapted.png)

<a id="FigDesignAdapted">*Figure 2:*</a>
*A visual representation of the WASP implementation, with components that we are implementing in this project depicted in green.*

Difference to the original design are that now we do not implement the query planner and directly create a query into a physical plan. Additionally, we did not implement the global metric monitor and do fully implement task managers. We did not implement the global metric monitor, as it is responsible for gathering metrics from the task managers, and we simply simulate these metrics alongside the WAN monitor (bandwidth metric). These metrics are randomly generated before hand and saved in [config files](src/configs), such that we can repeate experiments with the exact same metrics. These config files are then parsed once the query is running and every 10 seconds a new config is assigned to the task managers.

Due to the limited time in this project, we did not implement the full reconfiguration manager and all its possible operations (scale up/down, scale out, etc.), but instead resort to solving the ILP every time the config changes, create a new physical plan from said ILP results of how many tasks to place on each task manager, and then compate this plan to the currently running plan. If both the plans are equal the Job Manager will do nothing, however if the plans are different, all currently executing tasks will be halted, the ones that need to migrate to a new task manger are then migrated to said task manager, and then tasks start executing again.

## Execution Commands

This system is meant to be run on different JVM instances, which communicate over RMI calls. First a Job Manager will need to be started, followed by a number of Task Managers, and then the Sample Query that one wants to run. For convenience, we have create commands for running all Job Managers and Task Managers at once

```bash
# Running all managers at once (in replan mode)
sbt "runMain jobmanager.JobManagerRunner 8 -replan"

# Running all individually
sbt "runMain jobmanager.JobManagerRunner -replan"
# In a new JVM start multiple task mangers (8 with the default config)
sbt "runMain taskmanager.TaskManagerRunner"

# Then start the sample query
sbt "runMain SampleQueryRunner"
```

Config files (for the different task manger metrics) can be generated using

```bash
# For 8 tms, 5 different configs, and 5 task slots on each tm
python3 src/metadataGen.py -n 8 -c 5 -s 5
```

The config will then appear in the root directory and needs to be moved to the /src/configs folder, as well as specifying
it in the command line when running the JobManger, with for example having a config file named config-100.json

```bash
sbt "runMain jobmanager.JobManagerRunner 8 -replan config-100.json"
```

## Adapted Evaluation

With the new design, we also had to adapt our evaluation. Our new evaluation will run several benchmarks for queries of different sizes and different parallelism to identify how performance changes under these conditions when the scheduler uses adaptive or static scheduling. All the commands and detailed explanations of the different experiments, along with the results, are presented in the [experiments folder](/experiments/README.md).

## References 

<a id="1">[1]</a>
The Apache Software Foundation. Apache Flink. DOI:https://flink.apache.org/ 

<a id="2">[2]</a>
Albert Jonathan, Abhishek Chandra, and Jon Weissman. 2020. WASP: Wide-area Adaptive Stream Processing. In Proceedings of the 21st International Middleware Conference (Middleware '20). Association for Computing Machinery, New York, NY, USA, 221–235. DOI:https://doi-org.tudelft.idm.oclc.org/10.1145/3423211.3425668

<a id="3">[3]</a>
Gurobi Optimization, LLC. Gurobi Optimization Tool. 2021. DOI:http://www.gurobi.com

<a id="4">[4]</a>
Yahoo. Yahoo Streaming Benchmark. 2020. DOI:https://github.com/yahoo/streaming-benchmarks