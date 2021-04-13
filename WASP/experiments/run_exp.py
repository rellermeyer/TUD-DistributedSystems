#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Thu Apr  1 21:45:45 2021

@author: n7
"""

import os
import pandas as pd
import subprocess
from datetime import datetime

parallelisms = [(7, 2, 1), (12, 7, 1), (17, 12, 1)]
datasizes = [4000, 8000, 16000]
repeats = 10
tms_count = 8
cfg_name = "config-12"

# parallelisms = [(7, 2, 1)]
# datasizes = [4000, 8000]
# repeats = 2
# tms_count = 8


_SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def get_cfg_name():
    """
    Get the config file being used from JobManager.scala.

    Parameters
    ----------
    size : int
        Number of data elements to pass to the query.

    Returns
    -------
    cfg_name : str
        Name of the config file.

    """
    key = "      val reader = new FileReader(\"src/configs/"
    with open(os.path.join(_SCRIPT_DIR, "../src/main/scala/jobmanager/"
                           "JobManager.scala"), "r") as f:
        lines = f.readlines()
    for line in lines:
        if key in line:
            start = len(key)
            end = line.find(".json\")")
            cfg_name = line[start:end]
            return cfg_name
    return ""


def change_datasize(size):
    """
    Change the datasize in SampleQueryRunner.scala.

    Parameters
    ----------
    size : int
        Number of data elements to pass to the query.

    Returns
    -------
    lines : list
        List of edited lines (str) from SampleQueryRunner.scala.

    """
    key = "    val dataSize = "
    with open(os.path.join(_SCRIPT_DIR, "../src/main/scala/jobmanager/"
                           "SampleQueryRunner.scala"), "r+") as f:
        lines = f.readlines()
        for i in range(0, len(lines)):
            if key in lines[i]:
                lines[i] = key + str(size) + os.linesep
                break
        f.seek(0)
        f.writelines(lines)
        f.truncate()
    return lines


def change_parallelisms(prls):
    """
    Change the operator parallelisms in SampleQueryRunner.scala.

    Parameters
    ----------
    prls : tuple
        Tuple containing the parallelism values (ints) for each operator.
        (map, map, reduce).

    Returns
    -------
    lines : list
        List of edited lines (str) from SampleQueryRunner.scala.

    """
    key = "    val parallelisms = Array"
    with open(os.path.join(_SCRIPT_DIR,
                           "../src/main/scala/jobmanager/"
                           "SampleQueryRunner.scala"), "r+") as f:
        lines = f.readlines()
        for i in range(0, len(lines)):
            if key in lines[i]:
                lines[i] = key + str(tuple(prls)) + os.linesep
                break
        f.seek(0)
        f.writelines(lines)
        f.truncate()
    return lines


def run(num_tms, datasize, parallelisms, replan, cfg_file):
    """
    Run the experiment with the supplied parameters

    Parameters
    ----------
    num_tms : int
        Number of Task Managers.
    datasize : int
        Number of data elements to pass to the query.
    parallelisms : tuple
        Tuple containing the parallelism values (int) for each operator.
        (map, map, reduce).
    replan : bool
        Specifies whether the scheduler should be adaptive (True) or
        not (False).

    Returns
    -------
    runtime : int
        Number of milliseconds required for the query to complete.
    num_replans : int
        Number of replans performed.
    loss : int
        Number of operations lost.

    """
    change_datasize(datasize)
    change_parallelisms(parallelisms)

    pwd = os.getcwd()
    os.chdir(os.path.join(_SCRIPT_DIR, "../"))
    job_mgr_args = "runMain jobmanager.JobManagerRunner " + \
        str(num_tms) + " -" + ("" if replan else "no") + \
        "replan " + cfg_file + ".json"
    jm = subprocess.Popen(["sbt", job_mgr_args], stdout=subprocess.PIPE)
    jm_logs = ""

    while (True):
        dat = jm.stdout.readline().decode()
        # print(dat, end="", flush=True)
        jm_logs += dat
        if ("TaskManager " + str(num_tms - 1) + " bound!" in dat):
            break

    os.chdir(os.path.join(_SCRIPT_DIR, "../"))
    query_cmd = "sbt \"runMain SampleQueryRunner\""
    os.system(query_cmd)
    os.chdir(pwd)

    correct_result = datasize * 4
    finished_key = "FINISHED JOB: "
    runtime_key = "Total RunTime: "
    replan_key = "Number of Replans: "
    while (True):
        line = jm.stdout.readline().decode()
        # print(line, end="", flush=True)
        jm_logs += line
        dat = jm_logs.strip().split(os.linesep)[-3:]
        if (finished_key in dat[-3] and runtime_key in dat[-2] and
                replan_key in dat[-1]):
            jm.terminate()

            # Remove ' ms' from the end
            runtime = int(dat[-2].strip()[len(runtime_key):-3])

            num_replans = int(dat[-1].strip()[len(replan_key):])
            result = int(dat[-3].strip()[len(finished_key):])
            loss = correct_result - result
            return runtime, num_replans, loss


if __name__ == "__main__":
    log_str = ("num_tms, datasize, parallelisms, runtime_noreplan,"
               "runtime_replan,num_replans,loss\n")
    log_df = pd.DataFrame(columns=["Num TMs", "DataSize", "Parallelisms",
                                   "Runtime (No Replan)", "Runtime (Replan)",
                                   "Num Replans", "Loss"])
    # cfg_name = get_cfg_name()

    for ds in datasizes:
        start = str(tms_count) + "," + str(ds) + ","
        for prl in parallelisms:
            for i in range(repeats):
                line = start + str(prl).replace(",", "") + ","
                runtime_noreplan, _, _ = run(
                    tms_count, ds, prl, False, cfg_name)
                runtime_replan, num_replans, loss = run(tms_count, ds, prl,
                                                        True, cfg_name)
                line = line + str(runtime_noreplan) + "," + \
                    str(runtime_replan) + "," + str(num_replans) + "," + \
                    str(loss) + "\n"
                log_str += line
                log_df.loc[len(log_df)] = [tms_count, ds, prl,
                                           runtime_noreplan, runtime_replan,
                                           num_replans, loss]
    logs_name = "logs_" + cfg_name + "_" + \
        datetime.today().strftime("%Y%m%d_%H%M%S")
    with open(os.path.join(_SCRIPT_DIR, logs_name + ".csv"), "w") as f:
        f.write(log_str)
    log_df.to_pickle(os.path.join(_SCRIPT_DIR, logs_name + ".pickle"),
                     protocol=4)
