import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

sns.set_theme()
data = pd.read_csv("divStraw.txt", delimiter=" ")
data["nodes"] = data["bucket"]

# sns.lineplot(data=data, x="nodes", y="straw", label="Straw", marker="o")
fgrid = sns.lineplot(data=data, x="nodes", y="amount", label="Straw", marker="o")

fgrid.set(xlabel="OSD #", ylabel="Number of objects", ylim=(0,100000))


plt.legend()
plt.show()

