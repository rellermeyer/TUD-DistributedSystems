#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Fri Apr  2 17:45:16 2021

@author: n7
"""

from matplotlib import pyplot as plt
import numpy as np
import os
import pandas as pd


log_name = "logs"

_SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))


def plt_runtime_vs_prl(df):
    plt_dir = os.path.join(_SCRIPT_DIR, "../plots")
    if not os.path.isdir(plt_dir):
        os.mkdir(plt_dir)
    datasizes = log_df["DataSize"].unique()
    parallelisms = log_df["Parallelisms"].unique()
    for ds in datasizes:
        sub_df = log_df[log_df["DataSize"] == ds]

        rt_noreplan_means = np.zeros(parallelisms.size)
        rt_noreplan_stds = np.zeros(parallelisms.size)

        rt_replan_means = np.zeros(parallelisms.size)
        rt_replan_stds = np.zeros(parallelisms.size)

        for i in range(0, parallelisms.size):
            rt_nr = sub_df["Runtime (No Replan)"][sub_df["Parallelisms"] ==
                                                  parallelisms[i]]
            rt_r = sub_df["Runtime (Replan)"][sub_df["Parallelisms"] ==
                                              parallelisms[i]]

            rt_noreplan_means[i] = rt_nr.mean()
            rt_noreplan_stds[i] = rt_nr.std()
            rt_replan_means[i] = rt_r.mean()
            rt_replan_stds[i] = rt_r.std()
        fig = plt.figure()  # create a figure object
        ax = fig.add_subplot(1, 1, 1)
        ax.errorbar([sum(i) for i in parallelisms],
                    rt_noreplan_means/1000, rt_noreplan_stds/1000,
                    label="No Replan")
        ax.errorbar([sum(i) for i in parallelisms],
                    rt_replan_means/1000, rt_replan_stds/1000,
                    label="With Replan")
        ax.legend()
        ax.set_xticks([sum(i) for i in parallelisms])
        ax.set_xticklabels(parallelisms)
        ax.set_xlabel("Paralellisms", fontsize='18')
        ax.set_ylabel("Runtime [sec]", fontsize='18')
        ax.set_title("DataSize = " + str(ds), fontsize='20')
        ax.tick_params(axis='both', which='major', labelsize=14)
        ax.tick_params(axis='both', which='minor', labelsize=10)
        plt.grid(True, which='both')
        plt.subplots_adjust(top=0.93,
                            bottom=0.15,
                            left=0.13,
                            right=0.95,
                            hspace=0.2,
                            wspace=0.2)
        plt.savefig(os.path.join(plt_dir, "rt-prl_ds" + str(ds) + ".svg"))


def plt_runtime_vs_ds(df):
    plt_dir = os.path.join(_SCRIPT_DIR, "../plots")
    if not os.path.isdir(plt_dir):
        os.mkdir(plt_dir)
    datasizes = log_df["DataSize"].unique()
    parallelisms = log_df["Parallelisms"].unique()
    for prl in parallelisms:
        sub_df = log_df[log_df["Parallelisms"] == prl]

        rt_noreplan_means = np.zeros(parallelisms.size)
        rt_noreplan_stds = np.zeros(parallelisms.size)

        rt_replan_means = np.zeros(parallelisms.size)
        rt_replan_stds = np.zeros(parallelisms.size)

        for i in range(0, datasizes.size):
            rt_nr = sub_df["Runtime (No Replan)"][sub_df["DataSize"] ==
                                                  datasizes[i]]
            rt_r = sub_df["Runtime (Replan)"][sub_df["DataSize"] ==
                                              datasizes[i]]

            rt_noreplan_means[i] = rt_nr.mean()
            rt_noreplan_stds[i] = rt_nr.std()
            rt_replan_means[i] = rt_r.mean()
            rt_replan_stds[i] = rt_r.std()
        fig = plt.figure()  # create a figure object
        ax = fig.add_subplot(1, 1, 1)
        ax.errorbar(datasizes, rt_noreplan_means/1000, rt_noreplan_stds/1000,
                    label="No Replan")
        ax.errorbar(datasizes, rt_replan_means/1000, rt_replan_stds/1000,
                    label="With Replan")
        ax.legend()
        ax.set_xticks(list(datasizes))
        ax.set_xlabel("DataSize", fontsize='18')
        ax.set_ylabel("Runtime [sec]", fontsize='18')
        ax.set_title("Paralellisms = " + str(prl), fontsize='20')
        ax.tick_params(axis='both', which='major', labelsize=14)
        ax.tick_params(axis='both', which='minor', labelsize=10)
        plt.grid(True, which='both')
        plt.subplots_adjust(top=0.93,
                            bottom=0.15,
                            left=0.13,
                            right=0.95,
                            hspace=0.2,
                            wspace=0.2)
        plt.savefig(os.path.join(
            plt_dir, "rt-ds_prl" + str(sum(prl)) + ".svg"))


if __name__ == "__main__":
    log_file = os.path.join(_SCRIPT_DIR, log_name + ".pickle")
    log_df = pd.read_pickle(log_file)
    plt_runtime_vs_prl(log_df)
    plt_runtime_vs_ds(log_df)
