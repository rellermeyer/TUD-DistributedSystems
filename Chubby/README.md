# Chubby

## How to run

1. Ensure all IPs used by the servers are in `serverips.txt`
2. Run in the sbt-shell `assembly`
3. Run in a terminal window from the root directory `make`
4. Run `make network` (only required once)

**For server**
1. Run `make run-server`
2. Type start to start the server **NB: Server starts immediately with leader election**
3. The servers have found a leader if one has the output `Became leader...`

**For client**
1. Run `make run-client`
2. **NB: Client starts immediately and requires an established leader (so wait until the servers have found a leader before starting the client)**

## Experiments
There are no scripts used to evaluate the performance of the system. Replicas and clients were started manually.
The output of the nodes contain all the information required to do the analysis.