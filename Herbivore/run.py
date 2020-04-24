#!/usr/bin/env python
import argparse
import signal
import subprocess
import sys
import time
import queue
import threading

PATH_TO_BINARY = './target/universal/stage/bin/fruitarian'

DEFAULT_HOST = 'localhost'
DEFAULT_PORT = 5000

procs = []
threads = []
writequeue = queue.Queue()


def log(str):
    """Log str immediately to stdout"""
    sys.stdout.write(str)
    sys.stdout.flush()


def enqueue_output(out, idx, queue):
    """
    Enqueue some output in order to be written in order later on.
    Neatly prints the node id in front of the line.

    out: output (i.e. of stdout of a process)
    idx: internal index of the node/process
    queue: the queue to enqueue to
    """
    for line in iter(out.readline, b''):
        prefix = str(idx)
        if len(prefix) <= 4:
            prefix = f"{idx}{' ' * (4 - len(prefix))}"
        queue.put(f"{prefix}| {line}")
    out.close()


def package():
    """Runs `sbt clean stage` and waits for it to finish."""
    log("----- Running sbt clean stage\n")
    proc = subprocess.Popen(['sbt', 'clean', 'stage'], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, universal_newlines=True)
    for line in proc.stdout:
        log(f" | {line}")
    proc.wait()


def start_first_node(experiment):
    """Start the first node of the fruitarian network without any parameters."""
    log("----- Starting fruitarian node 0\n")
    exp_flag = '-e' if experiment else ''
    proc = subprocess.Popen(['./target/universal/stage/bin/fruitarian', exp_flag], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, universal_newlines=True)
    procs.append(proc)

    thread = threading.Thread(target=enqueue_output,
                              args=(proc.stdout, 0, writequeue))
    thread.daemon = True
    thread.start()
    threads.append(thread)


def add_node(idx, host, server_port, known_port, experiment):
    """
    Add a node to the fruitarian network.

    idx: the current node index (only for reference within this script and logs)
    host: the host of the node we want to connect to
    server_port: the port at which to start the server at this node
    known_port: the port of an already known node
    """
    log(f"----- Starting fruitarian node {idx}\n")
    exp_flag = '-e' if experiment else ''
    proc = subprocess.Popen(['./target/universal/stage/bin/fruitarian', str(server_port), host, str(known_port), exp_flag],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True)
    procs.append(proc)

    thread = threading.Thread(target=enqueue_output,
                              args=(proc.stdout, idx, writequeue))
    thread.daemon = True
    thread.start()
    threads.append(thread)


def print_output():
    """Continuously check the queue for any new output and log it"""
    while True:
        try:
            line = writequeue.get_nowait()
        except queue.Empty:
            # Prevent Python from going haywire
            time.sleep(.01)
            pass
        else:
            log(line)

        if all(proc.poll() is not None for proc in procs):
            return


def parse_args():
    """Parse the arguments of the script"""
    parser = argparse.ArgumentParser(
        description="Run the Fruitarian project")

    parser.add_argument('-n', '--nodes', type=int, required=True,
                        help="number of nodes to start")
    parser.add_argument('-e', '--experiment', type=int, default=1, metavar='NUMBER',
                        help="number of nodes that should start an experiment (default: 1)")
    parser.add_argument('-j', '--join', type=str, nargs='?', const=DEFAULT_HOST, default=None, metavar='HOST',
                        help="specify host to join already existing network (default when HOST unspecified: localhost)")
    parser.add_argument('-p', '--port', type=int, default=DEFAULT_PORT,
                        help="specify known first port to connect to (default: 5000)")
    parser.add_argument('-s', '--skip-packaging', action='store_true',
                        help="skip packaging of the Scala project")
    return parser.parse_args()


def stop(sig, frame):
    """Gracefully stop all the processes"""
    print("Stopping...")
    for proc in procs:
        proc.terminate()
    sys.exit(0)


args = parse_args()
# Register handler for SIGINT
signal.signal(signal.SIGINT, stop)

if args.nodes < 1:
    sys.exit("At least one node should be started")
if not args.skip_packaging:
    package()

# Start a thread for monitoring the other threads
print_thread = threading.Thread(target=print_output)
print_thread.daemon = True
print_thread.start()

new_network = args.join is None
host = 'localhost' if new_network or args.join in {
    'localhost', '127.0.0.1', '0.0.0.0'} else args.join

experiment_nodes = args.experiment

for i in range(args.nodes):
    should_be_exp = experiment_nodes > 0
    # Start a first node if we are not joining another host
    if i == 0 and new_network:
        start_first_node(should_be_exp)
    # 'Chain' the ports of the nodes if we are starting a network (for fun)
    elif new_network:
        add_node(i, host, args.port + i, args.port + i - 1, should_be_exp)
    # Else, add nodes that join other host and known port
    # Starts numbering ports one up from known port number
    else:
        add_node(i, host, args.port + 1 + i, args.port, should_be_exp)

    if should_be_exp:
        experiment_nodes -= 1

    # Make sure the nodes have some time to start
    time.sleep(1)

while True:
    if print_thread.is_alive():
        time.sleep(1)

log("All nodes stopped, exiting...")
sys.exit(0)
