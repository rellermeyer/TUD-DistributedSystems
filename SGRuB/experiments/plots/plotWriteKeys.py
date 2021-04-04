import matplotlib.pyplot as plt
import numpy as np

# DATA REPLICATE
# entry; keys; gas
# 0;1;85055
# 1;2;127709
# 2;4;213083
# 3;8;384087
# 4;16;725718
# 5;32;1409007
# 6;48;2092333
# 7;64;2775694
# 8;96;4143549
# 9;128;5517628
# 10;192;-1
# 11;256;-1

# DATA NON-REPLICATE
# 0;1;40674
# 1;2;40674
# 2;4;40610
# 3;8;40610
# 4;16;40674
# 5;32;40674
# 6;48;40674
# 7;64;40610
# 8;96;40674
# 9;128;40610
# 10;192;40674
# 11;256;40674

# Create the range.
x_axis_label = [1, 2, 4, 8, 16, 32, 48, 64, 96, 128, 192, 256]
x_axis_tick_labels = [1, '', '', '', 16, 32, 48, 64, 96, 128, 192, 256]
y_replicate = [number / 10000 for number in [85055, 127709, 213083, 384087, 725718, 1409007, 2092333, 2775694, 4143549, 5517628]]
y_non_replicate = [number / 10000 for number in [40674, 40674, 40610, 40610, 40674, 40674, 40674, 40610, 40674, 40610, 40674, 40674]]

# Create the figure
fig = plt.figure(1, figsize=(5, 3.5))
ax = fig.add_subplot()
a = np.arange(len(x_axis_label))

# Add the plots
ax.plot(x_axis_label, y_non_replicate, 'r-+', label='No Replica (BL1)')
ax.plot(x_axis_label[:10], y_replicate, 'b-x', label='Always with replica (BL2)')
ax.plot(x_axis_label, 12 * [800], 'g-', label='Max Gas cost')

# Set the x axis
ax.xaxis.set_ticks(x_axis_label)
ax.xaxis.set_ticklabels(x_axis_tick_labels)
ax.legend(loc="lower right", bbox_to_anchor=(1,0.1))

# Add labels and show the plot
plt.xlabel("Keys (of 1 Byte) written in single batch")
plt.ylabel("Gas cost (x10.000 Gas)")
plt.savefig("experiment_write_keys.pdf", bbox_inches='tight')
plt.show()
