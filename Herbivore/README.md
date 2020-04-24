# Fruitarian

A clone of the herbivore anonymous communication protocol

## Running

Running the project can best be done using the `run.py` script.
Make sure you have Python 3 installed.
Then, simply run:

```shell
python run.py -n 3
```

This will build and package the project and start a Fruitarian network with 3 nodes on localhost that connect to each other through subsequent ports, with one node running an experiment (i.e. sending random data and measuring various performance metrics).
The `-e` flag can be used to set the amount of nodes that should be running an experiment.
The script can also be used to connect to an existing network using the `-j` and `-p` flags to specify which host and port to join respectively.
If you already have a packaged version of the project and do not want to repackage, you can skip this using the `-s` flag.
In case you need more information, simply add the `-h` flag.

## Setup

We use the LTS version of the JDK which at this point is Java 11.
Make sure you have Scala and sbt installed.

### IntelliJ IDEA

Make sure to download the [scala plugin](https://www.jetbrains.com/help/idea/discover-intellij-idea-for-scala.html).
You might get a popup for this if you open the project without having it installed.

Once opened in IntelliJ you can open the sbt tool window (`view > tool windows > sbt` on MacOS) and import the project.
This should download any dependencies and set up project in IntelliJ itself.

You can now mark the `src/main` folder as sources root and the `src/test` folder as test sources root.
This will help resolve any issues in IntelliJ itself.

You should now be able to run the project from the main file.

### Terminal

To run the project in the terminal cd to the root of the project and call `sbt`.
Once inside the `sbt` terminal you can run the command `run` to run the main project and `test` to run the tests.

### Pre-Commit

To apply some simple checks before you commit like removing trailing whitespaces, or to forbid you to commit to master you can install [pre-commit](https://pre-commit.com).
Installation can be done using the command `python3 -m pip install pre-commit` and running `pre-commit install` in the project root folder.

## Chat
For demonstration purposes the application contains a chat mode that uses the `ChatLogger` observer.
This sets up the application to act as an inbox based chat (more like email).
Each node in the clique is able to send a message which will be received anonymously by all nodes in this clique (including the sender).

This mode significantly slows down the rounds to allow easy syncing of the random generators for each node.
This is because these random generators have to be in sync with the roundId to work (see `models.Peer` class).
Using a slower rounds allows new nodes to catch up.

The chat mode is activated by using the `--chat` flag.
Or when you are joining another node `<ownport> <other-node-ip> <their-port> --chat` (for example `5000 192.168.1.2 5000 --chat`).
Since the chat mode takes over the terminal it will log all logs to the file `application.log`.
Each start is logged with a timestamp like so: `[START] 2020-04-07 11:30`
Type the command `/help` to view all the possible commands.
