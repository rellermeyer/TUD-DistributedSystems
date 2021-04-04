import matplotlib.pyplot as plt
import numpy as np


# Method to extrapolate the test results.
def create_baseline_plot(writes, write_cost_a, write_cost_b, deliver_cost, reads):
    x_array = []
    y_array = []

    # Calcuate the total write cost.
    total_write_cost = write_cost_a + write_cost_b * (writes - 1)

    for i in reads:
        # Append to x array
        read_ops = i * writes
        y_array.append(((total_write_cost + deliver_cost * read_ops) / (read_ops + writes)) / 10000)

        # Append to y array
        x_array.append(i)

    # Return extrapolated results.
    return x_array, y_array


# Create the range.
x_axis = [0, 0.0625, 0.125, 0.25, 0.5, 0.75, 1, 2, 4, 8, 16, 32, 64, 128, 256]
x_axis_label = [0, '', 0.125, '', 0.5, '', 1, '', 4, '', 16, '', 64, '', 256]
x_range = range(len(x_axis))

# Extrapolate the results
bl1 = create_baseline_plot(256, 85688, 40674, 33302, x_axis)
bl2 = create_baseline_plot(256, 130005, 85055, 0, x_axis)

fig = plt.figure(1, figsize=(5, 3.5))
ax = fig.add_subplot()
a = np.arange(len(x_axis))

# Add the plots
ax.plot(a, bl1[1], 'r-+', label='No Replica (BL1)')
ax.plot(a, bl2[1], 'b-x', label='Always with replica (BL2)')

# Set the x axis
ax.xaxis.set_ticks(a)
ax.xaxis.set_ticklabels(x_axis_label)
ax.legend()

# Add labels and show the plot
plt.xlabel("Read-to-write ratio")
plt.ylabel("Per-operation cost (x10.000 Gas)")
plt.savefig("experiment_baselines.pdf", bbox_inches='tight')
plt.show()
