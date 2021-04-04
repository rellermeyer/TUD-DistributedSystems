import matplotlib.pyplot as plt
import numpy as np

# DATA NO REPLICATE
# 0;1;40674
# 1;2;40674
# 2;4;40674
# 3;8;40674
# 4;16;40674
# 5;32;40674
# 6;48;40674
# 7;64;40674
# 8;96;40610
# 9;128;40674
# 10;192;40674
# 11;256;40610

# DATA REPLICATE
# 0;1;84991
# 1;2;85119
# 2;4;85247
# 3;8;85503
# 4;16;86015
# 5;32;107141
# 6;48;128369
# 7;64;129393
# 8;96;151645
# 9;128;173897
# 10;192;218401
# 11;256;262841


# Create the range.
x_axis_label = [1, 2, 4, 8, 16, 32, 48, 64, 96, 128, 192, 256]
x_axis_tick_label = [1, '', '', '', 16, 32, 48, 64, 96, 128, 192, 256]
y_no_replicate = [number / 10000 for number in [40674, 40674, 40674, 40674, 40674, 40674, 40674, 40674, 40674, 40674, 40674, 40674]]
y_replicate = [number / 10000 for number in [84991, 85119, 85247, 85503, 86015, 107141, 128369, 129393, 151645, 173897, 218401, 262841]]

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
plt.xlabel("Bytes written")
plt.ylabel("Gas cost (x10.000 Gas)")
plt.savefig("experiment_writebytes.pdf", bbox_inches='tight')
plt.show()
