import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

sns.set_theme()
data = pd.read_csv("mapBenchVaryingBucketSize.txt", delimiter=" ")

sns.lineplot(data=data, x="bucketsize", y="straw", label="Straw", marker="o")
fgrid = sns.lineplot(data=data, x="bucketsize", y="uniform", label="Uniform", marker="o")

fgrid.set(xlabel="Bucket Size (items)", ylabel="Time (ns)")

plt.legend()
plt.show()

