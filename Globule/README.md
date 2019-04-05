# IN4391-globule

## How to Run

1. Run `make setup`
2. Copy `src/main/resources/application.conf.template` to `src/main/resources/application.conf`
3. Set your ip, port and servername in `src/main/resources/application.conf`
4. Create `~/.globule/peers.conf` and add a list of peers (ip:port separated by newlines)
5. Run `make compile`
6. Run `make run`

If you want to stop the server, run `make kill`

## Running tests
If at least two Globule servers are active and connected to each other, the experiment can be run on the local machine.
It is important to notice that the experiment relies on the location of the Globule servers and the location of the machine that is running the experiment.

* Run `python experiments/python/Analysis.py`

After each experiment, the globule servers has to be reset. This can be done using the command `make restart`.
Also the replicated files should be removed from the replica servers.