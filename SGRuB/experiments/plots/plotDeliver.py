import matplotlib.pyplot as plt
import numpy as np

# DATA
# entry; bytes; gas
# 0;1;33302
# 1;2;33430
# 2;4;36614
# 3;8;36870
# 4;16;37254
# 5;32;38278
# 6;48;39774
# 7;64;40798
# 8;96;43255
# 9;128;45839
# 10;192;50879
# 11;256;56240

# Create the range.
x_axis_label = [1, 2, 4, 8, 16, 32, 48, 64, 96, 128, 192, 256]
x_axis_tick_labels = [1, '', '', '', 16, 32, 48, 64, 96, 128, 192, 256]
y_deliver = [number / 10000 for number in [33302, 33430, 36614, 36870, 37254, 38278, 39774, 40798, 43255, 45839, 50879, 56240]]

# Create the figure
fig = plt.figure(1, figsize=(5, 3.5))
ax = fig.add_subplot()
a = np.arange(len(x_axis_label))

# Add the plots
ax.plot(x_axis_label, y_deliver, 'r-+', label='No Replica (BL1)')

# Set the x axis
ax.xaxis.set_ticks(x_axis_label)
ax.xaxis.set_ticklabels(x_axis_tick_labels)
ax.legend()

# Add labels and show the plot
plt.xlabel("Value size (in Bytes)")
plt.ylabel("Gas cost (x10.000 Gas)")
plt.savefig("experiment_deliver.pdf", bbox_inches='tight')
plt.show()
