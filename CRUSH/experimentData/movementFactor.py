import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

sns.set_theme()
data = pd.read_csv("movementBucketFactor.txt", delimiter=" ")
data["nodes"] = data["size"]

sns.lineplot(data=data, x="nodes", y="straw", label="Straw", marker="o")
fgrid = sns.lineplot(data=data, x="nodes", y="uniform", label="Uniform", marker="o")

fgrid.set(xlabel="Original size", ylabel="Movement factor")
# fgrid.axes.set_yscale('log')

plt.legend()
plt.show()

