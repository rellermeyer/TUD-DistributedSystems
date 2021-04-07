# gossip_learning
IN4391 Distributed Systems, Group 4, TU Delft 2020-2021.
Scala implementation of https://link.springer.com/chapter/10.1007/978-3-030-22496-7_5

# How to run - local setup

Run the following command to split the datasets and generate a docker-compose file.
```sh
python3 init.py local {Eta} {Lambda} {Batchsize} {Label} {Dataset_name} {Model_send_interval}  {Num_nodes} {Num_Neighbours} {Data_aggregation_interval_seconds}
``` 
For example:
```sh
python3 init.py local 0.001 0.001 10 1 pendigits 1000 5 3 20
```

The default arguments for the har dataset are: `python3 init.py local 100 0.01 10 1 har 1000 8 2 5`.

The default arguments for the spambase dataset are: `python3 init.py local 10000 0.000001 10 1 spambase 1000 8 2 5`.

Then execute
```sh
docker build . -t gossipnode
docker build ./collector/ -t collector
docker-compose up
```

# How to run - swarm setup

The arguments for generating a docker-compose file for a swarm setup are the same as the ones for a local setup. Just leave out `local` at the start:
```sh
python3 init.py {ETA} {Lambda} {Batchsize} {Label} {Dataset_name} {Model_send_interval}  {Num_nodes} {Num_Neighbours} {Data_aggregation_interval_seconds}
```
So for example:
```sh
python3 init.py 0.001 0.001 10 1 pendigits 1000 5 3 20
```

The default arguments for the har dataset are: `python3 init.py 100 0.01 10 1 har 1000 100 20 1`.

The default arguments for the spambase dataset are: `python3 init.py 10000 0.000001 10 1 spambase 1000 100 20 1`.

Then execute
```sh
docker stack deploy –compose-file docker-compose.yml 
```

# Experiments
The experiment outputs are located in the `experiments` folder. They have been conducted with mostly default parameters, except for those mentioned in the file name.

# (Optional) external collector
If one would like to make use of an external collector this is possible by adding the address of the collector as a 
parameter to the respective init python file, note the data aggregation argument is not taken into account anymore and must be set directly with the external collector, this must be done in the following format: `{IP}:{PORT}`.

The full command would be
```sh
python3 init.py 10000 0.000001 10 1 spambase 1000 8 2 5 example.com:80
```

##Results
Now the results can be collected by going to `localhost:8080` (or the address of an external collector)! 

The data is aggregated in timeslots of the indicated data aggregation interval.

#Datasets
Included are three datasets:

## Pendigits
This datasets contain the pixel brightness in the first columns and the number in the final column. Source: https://archive.ics.uci.edu/ml/datasets/Pen-Based+Recognition+of+Handwritten+Digits

## Spambase
This dataset contains the characteristics in the first columns and whether it is spam (1) or not (0) in the final columns. Source: https://archive.ics.uci.edu/ml/datasets/spambase

## Human Activity Recognition
This dataset contains the measurements in the first columns and the activity label in the final column. Source: https://archive.ics.uci.edu/ml/datasets/Human+Activity+Recognition+Using+Smartphones
