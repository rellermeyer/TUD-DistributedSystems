# JADE

## Running the project
You need to build and run docker container:
- Build the docker images: `sbt docker:publishLocal`
- Run the docker container for the node: `docker run -p 2552:2552 -v /var/run/docker.sock:/var/run/docker.sock --rm node:0.1 <External IP>`
    - For example: `docker run -p 2552:2552 -v /var/run/docker.sock:/var/run/docker.sock --rm node:0.1 12.345.678.901`
- Run the docker container for the manager instance: `docker run -p 2551:2551 --rm manager:0.1 <External IP>` 
    - To connect to remote machines, pass the IP addresses as follows: `docker run -p 2551:2551 --rm manager:0.1 <External IP> <Count of Node IPs> <Node IPs> <Manager Replica IPs>`
    - If you don't specify `<Manager Replica IPs>`, the jade manager will work without replication. Otherwise the `<Manager Replica IPs>` should also include the IP of the manager it is running on.
    - For example: `docker run -p 2551:2551 --rm manager:0.1 12.345.678.901 2 192.168.1.2 192.168.1.3 192.168.1.4` will have `192.168.1.2` and `192.168.1.3` as node and `192.168.1.4` as replica.

## Developing

Run all tests with:

```bash
sbt test
```

Run code style analysis with:

```bash
sbt scalastyle
```
