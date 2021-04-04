import matplotlib.pyplot as plt
import numpy as np

# DATA REPLICATE
# entry; keys; replicates; gas
# 0;1;8;680376
# 1;2;4;510836
# 2;4;2;426166
# 3;8;1;384087


# DATA NON-REPLICATE
# entry; keys; replicates; gas
# 0;1;8;325328
# 1;2;4;162696
# 2;4;2;81284
# 3;8;1;40674
#

# Create the range.
x_axis_label = [1, 2, 4, 8]
x_axis_tick_labels = ['1/8', '2/4', '4/2', '8/1']
y_replicate = [number / 10000 for number in [680376, 510836, 426166, 384087]]
y_non_replicate = [number / 10000 for number in [325328, 162696, 81284, 40674]]

# Create the figure
fig = plt.figure(1, figsize=(5, 3.5))
ax = fig.add_subplot()
a = np.arange(len(x_axis_label))

# Add the plots
ax.plot(x_axis_label, y_non_replicate, 'r-+', label='No Replica (BL1)')
ax.plot(x_axis_label, y_replicate, 'b-x', label='Always with replica (BL2)')

# Set the x axis
ax.xaxis.set_ticks(x_axis_label)
ax.xaxis.set_ticklabels(x_axis_tick_labels)
ax.legend()

# Add labels and show the plot
plt.xlabel("Values per batch (Values/Batches)")
plt.ylabel("Gas cost (x10.000 Gas)")
plt.savefig("experiment_write_keys_batches.pdf", bbox_inches='tight')
plt.show()
