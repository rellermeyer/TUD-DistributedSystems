# Graph-CRDT : Distributed Systems Group 10

To run the app on a specific port, such as 8083, run:

```
sbt "run 8083"
```

When not giving the argument, default port is 8080., mind that the quotes are needed for the previous example.

# Running the dockerfile

To publish the dockerfile locally, start of by running the following command in the terminal in the `crdt-graph` folder of the project:

```
sbt reload plugins clean compile
```

This makes sure all the dependency plugins are set and cleans the project before compiling it. 
Now to create a local Docker image run the command 

```
sbt docker:publishLocal
```

This generates a directory with the Dockerfile and environment prepared for creating a Docker image.
Next it  builds an image using the local Docker server.
Now that this is all set up, you can run the docker image by running the following command:

```
docker run --rm -p 8080:8080 -i crdt-graph:0.1.0-SNAPSHOT
```

If this command does not work, please run 

```
docker image ls
```
Where the last created docker image would be on top and should contain the name "graph-crdt"

The docker image should run on port 8080, navigate to localhost:8080 to access the service in the browser. 

! TODO: Add port configuration !
