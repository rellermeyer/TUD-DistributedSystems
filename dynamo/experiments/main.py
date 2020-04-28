
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns

from local import *
from cluster7 import *

plt.rcParams.update({'font.size': 30})

put_latency = cluster_put_latency_n_3_3
get_latency = cluster_get_latency_n_3_3

get_latency_1 = cluster_get_latency_n_1_1
put_latency_1 = cluster_put_latency_n_1_1

get_latency_3 = cluster_get_latency_n_3_3
put_latency_3 = cluster_put_latency_n_3_3

get_latency_6 = cluster_get_latency_n_6_3
put_latency_6 = cluster_put_latency_n_6_3

get_sorted = np.sort(get_latency)
put_sorted = np.sort(put_latency)

# Percentile values to measure
p = np.array([30, 60, 80, 90, 95, 97, 98, 98.5, 98.9, 99.1, 99.2, 99.3, 99.4, 99.91, 99.93, 99.95, 99.98, 99.991, 99.993, 99.995, 99.998])

get_perc = np.percentile(get_sorted, p)
put_perc = np.percentile(put_sorted, p)

get1_perc = np.percentile(np.sort(get_latency_1), p)
get3_perc = np.percentile(np.sort(get_latency_3), p)
get6_perc = np.percentile(np.sort(get_latency_6), p)
put1_perc = np.percentile(np.sort(put_latency_1), p)
put3_perc = np.percentile(np.sort(put_latency_3), p)
put6_perc = np.percentile(np.sort(put_latency_6), p)

get_perc_map = {
    1: get1_perc,
    3: get3_perc,
    6: get6_perc
}

put_perc_map = {
    1: put1_perc,
    3: put3_perc,
    6: put6_perc
}

print("Get percentile: {}".format(get_perc))
print("Put percentile: {}".format(put_perc))


# See https://stackoverflow.com/questions/42072734/percentile-distribution-graph
def plot_percentile(name, percentiles, percentages):
    clear_bkgd = {'axes.facecolor': 'none', 'figure.facecolor': 'none'}
    sns.set(style='ticks', context='notebook', palette="muted", rc=clear_bkgd)

    # x = [30, 60, 80, 90, 95, 97, 98, 98.5, 98.9, 99.1, 99.2, 99.3, 99.4]
    x = percentages
    y = percentiles

    # Number of intervals to display.
    # Later calculations add 2 to this number to pad it to align with the reversed axis
    num_intervals = 5
    x_values = 1.0 - 1.0 / 10 ** np.arange(0, num_intervals + 2)

    # Start with hard-coded lengths for 0,90,99
    # Rest of array generated to display correct number of decimal places as precision increases
    lengths = [1, 2, 2] + [int(v) + 1 for v in list(np.arange(3, num_intervals + 2))]

    # Build the label string by trimming on the calculated lengths and appending %
    labels = [str(100 * v)[0:l] + "%" for v, l in zip(x_values, lengths)]

    fig, ax = plt.subplots(figsize=(8, 4))

    ax.set_xscale('log')
    plt.gca().invert_xaxis()
    # Labels have to be reversed because axis is reversed
    ax.xaxis.set_ticklabels(labels[::-1], fontsize=16)

    ax.plot([100.0 - v for v in x], y, label='N=1')

    for label in ax.get_yticklabels():
        label.set_fontsize(16)

    ax.grid(True, linewidth=0.5, zorder=5)
    ax.grid(True, which='minor', linewidth=0.5, linestyle=':')

    ax.set_ylabel('Latency (ms)', fontsize=18)
    ax.set_xlabel('Percentile', fontsize=18)
    ax.set_title(name + ' latency distribution', fontsize=22)

    ax.legend(fontsize=22)

    sns.despine(fig=fig)

    plt.show()
    fig.savefig(name + "-percentile_cluster_1_1.png", dpi=600, format='png')


def plot_percentile_multiple(name, percentiles_map, percentages):
    clear_bkgd = {'axes.facecolor': 'none', 'figure.facecolor': 'none'}
    sns.set(style='ticks', context='notebook', palette="muted", rc=clear_bkgd)

    # x = [30, 60, 80, 90, 95, 97, 98, 98.5, 98.9, 99.1, 99.2, 99.3, 99.4]
    x = percentages

    # Number of intervals to display.
    # Later calculations add 2 to this number to pad it to align with the reversed axis
    num_intervals = 5
    x_values = 1.0 - 1.0 / 10 ** np.arange(0, num_intervals + 2)

    # Start with hard-coded lengths for 0,90,99
    # Rest of array generated to display correct number of decimal places as precision increases
    lengths = [1, 2, 2] + [int(v) + 1 for v in list(np.arange(3, num_intervals + 2))]

    # Build the label string by trimming on the calculated lengths and appending %
    labels = [str(100 * v)[0:l] + "%" for v, l in zip(x_values, lengths)]

    fig, ax = plt.subplots(figsize=(8, 4))

    ax.set_xscale('log')
    plt.gca().invert_xaxis()
    # Labels have to be reversed because axis is reversed
    ax.xaxis.set_ticklabels(labels[::-1], fontsize=16)

    for key in percentiles_map:
        ax.plot([100.0 - v for v in x], percentiles_map[key], label="N={}".format(key))

    for label in ax.get_yticklabels():
        label.set_fontsize(16)

    ax.grid(True, linewidth=0.5, zorder=5)
    ax.grid(True, which='minor', linewidth=0.5, linestyle=':')

    ax.set_ylabel('Latency (ms)', fontsize=18)
    ax.set_xlabel('Percentile', fontsize=18)
    ax.set_title(name + ' latency distribution', fontsize=22)

    ax.legend(fontsize=18)

    sns.despine(fig=fig)

    plt.show()
    fig.savefig(name + "-percentile_cluster.png", dpi=600, format='png')

# plot_percentile("Get operation", get_perc, p)
# plot_percentile("Put operation", put_perc, p)


plot_percentile_multiple("Get operation", get_perc_map, p)
plot_percentile_multiple("Put operation", put_perc_map, p)
