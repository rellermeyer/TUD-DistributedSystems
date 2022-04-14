# A bar plot with errorbars
import numpy as np
import matplotlib.pyplot as plt
import json

# Open the files of data
with open("ack.json", "r") as file_read:
    results_ack = json.load(file_read)


with open("none.json", "r") as file_read:
    results_none = json.load(file_read)


# Labels to be used in the Y axis
labelsYaxis = ['Avg. wait time (ms)', 'Traffic generated (MB)',
               'Successful publications', 'Missed publications']


# Get values of fields
def getValuesField(dataset):
    fields_interest = ["avg_wait_time", "traffic_sent_bytes", "recv_pubs", "miss_pubs"]
    values = []
    for field in fields_interest:
        if field in dataset:
            values.append(dataset[field])
    return values


plt.rc('axes', labelsize=15)  # fontsize of the x and y labels
plt.rc('xtick', labelsize=12)  # fontsize of the x tick labels


def plot_values(completeInfo, counter_plots=1):
    plt.subplot(2, 2, counter_plots)
    widthBar = 0.1
    # creating the bar plot
    plt.bar(widthBar/2, completeInfo["means"][0], color='red',
            width=widthBar, label="None", edgecolor='black', alpha=0.5)
    plt.bar(widthBar/2+widthBar, completeInfo["means"][1], widthBar, color='blue',
            label="ACK", edgecolor='black', alpha=0.5)

    # Setting the error bars
    plt.errorbar(widthBar/2, completeInfo["means"][0], completeInfo["std"]
                 [0], linestyle='None', marker='', color="black", capsize=4, alpha=0.5)
    plt.errorbar(widthBar/2+widthBar, completeInfo["means"][1], completeInfo["std"]
                 [1], linestyle='None', marker='', color="black", capsize=4, alpha=0.5)
    plt.tick_params(
        axis='x',          # changes apply to the x-axis
        which='both',      # both major and minor ticks are affected
        bottom=False,      # ticks along the bottom edge are off
        top=False,         # ticks along the top edge are off
        labelbottom=False)  # labels along the bottom edge are off

    # Setting the labels
    plt.xlabel(completeInfo["xlabel"])
    plt.ylabel(completeInfo["ylabel"])
    plt.legend(fontsize=12)
    counter_plots += 1

    return counter_plots


# Values from the runs
ack_means = getValuesField(results_ack["avg"])
ack_std = getValuesField(results_ack["std"])


none_means = getValuesField(results_none["avg"])
none_std = getValuesField(results_none["std"])


# Create the four plots
counterPlot = 1

for i in range(4):
    completeInfo = {}
    completeInfo["means"] = [none_means[i], ack_means[i]]
    completeInfo["std"] = [none_std[i], ack_std[i]]
    completeInfo["ylabel"] = labelsYaxis[i]
    completeInfo["xlabel"] = ""
    counterPlot = plot_values(completeInfo, counterPlot)

plt.show()
