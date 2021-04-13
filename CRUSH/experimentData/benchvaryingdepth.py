import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

sns.set_theme()
data = pd.read_csv("mapBenchVaryingDepth.txt", delimiter=" ")
data["nodes"] = 8 ** data["depth"]

sns.lineplot(data=data, x="nodes", y="straw", label="Straw", marker="o")
fgrid = sns.lineplot(data=data, x="nodes", y="uniform", label="Uniform", marker="o")

fgrid.set(xlabel="Cluster Size (OSDs)", ylabel="Time (ns)")
fgrid.axes.set_xscale('log')

plt.legend()
plt.show()

