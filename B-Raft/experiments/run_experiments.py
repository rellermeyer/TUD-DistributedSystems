import atexit
import itertools
import logging
import os
import subprocess
import time

import numpy as np
from tqdm import tqdm

with open("run_main_pepijn.txt", "r") as f:
    run_main_command = f.read()

CURR_POPEN_PROCESS = None


def run_experiments(
        experiment_config,
        exp_dir=None,
        max_elections=10,
        num_replications=10,
        follow_raft_log=True,
):
    global CURR_POPEN_PROCESS
    if not exp_dir:
        exp_dir = time.strftime("%Y%m%d-%H%M%S")

    exp_dir = os.path.join(os.path.abspath(os.getcwd()), exp_dir)
    logging.info(f"Creating experiment directory: {exp_dir}")
    os.makedirs(exp_dir, exist_ok=True)

    experiments = []
    for key, values in experiment_config.items():
        experiments.append([(key, val) for val in values])

    all_exps = sorted(list(itertools.product(*experiments)), key=lambda x: -x[1][1])

    exp_string = "\n".join([str(x) for x in all_exps])
    logging.info("All experiments: \n" + exp_string)
    with open(os.path.join(exp_dir, "experiments.txt"), "w+") as f:
        f.write(exp_string)

    for run in range(num_replications):
        logging.info(f"Replication run {run + 1}/{num_replications}")
        for exp_num, value_list in tqdm(enumerate(all_exps)):
            logging.info(f"Running exp {exp_num}/{len(all_exps)} with values: {value_list}")
            experiment_vars = {}
            for (variable, value) in value_list:
                experiment_vars[variable] = str(value)

            logfile = os.path.join(
                exp_dir, f"run{run + 1}_" +
                         "_".join([f"{key}={value}" for key, value in experiment_vars.items()])
                         + ".log"
            )
            # specify logfile
            logging.info(f"Setting logfile to {logfile}")
            experiment_vars["LOGFILE"] = logfile

            # run experiment
            num_elections = 0
            starttime = time.time()
            popen = subprocess.Popen(
                run_main_command,
                stdout=subprocess.PIPE,
                env=experiment_vars,
                bufsize=10,
                stdin=subprocess.PIPE,
            )

            CURR_POPEN_PROCESS = popen

            for stdout_line in iter(
                    popen.stdout.readline,
                    "",
            ):
                stdout_line = str(stdout_line)

                if "[VERIFY APPEND DATA]" in stdout_line:
                    break

                if "[UNABLE TO VERIFY DUE TO TIMEOUT]" in stdout_line:
                    break

                if (time.time() - starttime) > 300:
                    logging.warning(f"Experiment {exp_num} took more than 5 minutes, skipping")
                    break

            popen.terminate()
            logging.info(
                f"Experiment {exp_num} finished in {time.time() - starttime :.2f} seconds"
            )


def cleanup():
    timeout_sec = 5
    p = CURR_POPEN_PROCESS
    if not p:
        return
    p_sec = 0
    for second in range(timeout_sec):
        if p.poll() == None:
            time.sleep(1)
            p_sec += 1
    if p_sec >= timeout_sec:
        p.kill()  # supported from python 2.6

    logging.info("Killed all processes")


atexit.register(cleanup)

if __name__ == "__main__":
    # Specify the list of values to use here, see src/main/resources/application.conf for possible variables
    # Every combination of the values set here will be run 'num_replications' times
    num_replications = 4
    experiment_config = {
        "raftType": ["Raft", "BRaft"],
        "crashIntervalHeartbeats": [10000],
        "nodes": np.linspace(start=3, stop=21, num=9, dtype=int),
    }

    logging.getLogger().setLevel("INFO")

    # output experiment directory, replace with name of experiment(group)
    experiment_dir = "resources/output/append_data_2"

    # set to true to see Raft logging in console of this process
    follow_raft_log = False

    # actually run the experiments
    run_experiments(
        experiment_config,
        exp_dir=experiment_dir,
        max_elections=10,
        num_replications=num_replications,
        follow_raft_log=follow_raft_log,
    )
