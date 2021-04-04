import matplotlib.pyplot as plt
import numpy as np

# DATA Replicating
# entry; bytes; gas
# 0;1;24584
# 1;2;24584
# 2;4;24584
# 3;8;24584
# 4;16;24584
# 5;32;24916
# 6;48;25445
# 7;64;25445
# 8;96;25974
# 9;128;26503
# 10;192;27561
# 11;256;28619

# DATA Non-replicating
# entry; bytes; gas
# 0;1;23625
# 1;2;23625
# 2;4;23625
# 3;8;23625
# 4;16;23625
# 5;32;23625
# 6;48;23625
# 7;64;23625
# 8;96;23625
# 9;128;23625
# 10;192;23625
# 11;256;23625

# Create the range.
x_axis_label = [1, 2, 4, 8, 16, 32, 48, 64, 96, 128, 192, 256]
x_axis_tick_label = [1, '', '', '', 16, 32, 48, 64, 96, 128, 192, 256]
y_no_replicate = [number / 10000 for number in [23625, 23625, 23625, 23625, 23625, 23625, 23625, 23625, 23625, 23625, 23625, 23625]]
y_replicate = [number / 10000 for number in [24584, 24584, 24584, 24584, 24584, 24916, 25445, 25445, 25974, 26503, 27561, 28619]]

# Create the figure
fig = plt.figure(1, figsize=(5, 3.5))
ax = fig.add_subplot()
a = np.arange(len(x_axis_label))

# Add the plots
ax.plot(x_axis_label, y_no_replicate, 'r-+', label='No Replica (BL1)')
ax.plot(x_axis_label, y_replicate, 'b-x', label='Always with replica (BL2)')

# Set the x axis
ax.xaxis.set_ticks(x_axis_label)
ax.xaxis.set_ticklabels(x_axis_tick_label)
ax.legend()

# Add labels and show the plot
plt.xlabel("Bytes read")
plt.ylabel("Gas cost (x10.000 Gas)")
plt.savefig("experiment_getbytes.pdf", bbox_inches='tight')
plt.show()