# CRUSH

This project contains the open source implementation of the [CRUSH algorithm](https://ceph.io/wp-content/uploads/2016/08/weil-crush-sc06.pdf) for group 11 in the 2021 edition of the Distributed Systems course at TU Delft.

## Running the implementation
In order to run the implementation, a machine with docker installed is required.

1. Clone the repository
2. Compile the scala code with `sbt docker:publishLocal`
3. Run docker-compose `cd docker` and `docker-compose up`

Running is then as simple as starting the `docker-compose` command with the generated file.

## Configuring the experiments
The algorithm experiments are in the test folder. Run these the same as any other test. The experiments can take some time.

Note that for updated CrushMaps, also the `application.conf` file in the `src/main/resources` must be updated 
accordingly. (This also requires the docker container to be re-created).


## Running the tests

In order to run the tests for the project, the command `sbt test` can be run in a terminal. 

In IntelliJ, the tests can be run by right clicking the test files in `test/scala/CRUSH/` and running the files.


## Configurator
To easily configure the amount of OSD nodes and the bucket sizes, the src/experimentGenerator/generator.js can write the configuration files.
NodeJS is required to run it. The generated files(`gen.conf` and `docker.yaml`) must be copied to the actual config location, see the help of the generator.

```node generator.js [1,2,3] [1,1,2]```
