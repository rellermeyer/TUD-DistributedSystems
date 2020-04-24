# library & dataset
import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt

data_slave_one = [3752, 3705, 3488, 3753, 3716]
data_slave_two = [2711, 2840, 3122, 2907, 3055]
data_slave_three = [2304, 2604, 2751, 3248, 3252]
data_slave_four = [2328, 2436, 3096, 2392, 2796]
data_slave_five = [2527, 3078, 2925, 2859, 2757]
data_slave_seven = [2135, 2565, 2352, 2467, 2304]
data_slave_ten = [2881, 2323, 2168, 3166, 2103]
data_slave_fifteen = [2603, 2182, 2440, 2380, 2350]

data = []
total = 1500000

for i in range(0, len(data_slave_one)):
    data.append([1, total / data_slave_one[i]])

for i in range(0, len(data_slave_two)):
    data.append([2, total/ data_slave_two[i]])

for i in range(0, len(data_slave_three)):
    data.append([3, total/ data_slave_three[i]])

for i in range(0, len(data_slave_four)):
    data.append([4, total/ data_slave_four[i]])

for i in range(0, len(data_slave_five)):
    data.append([5, total/ data_slave_five[i]])
for i in range(0, len(data_slave_seven)):
    data.append([7, total/ data_slave_seven[i]])
for i in range(0, len(data_slave_ten)):
    data.append([10, total/ data_slave_ten[i]])

for i in range(0, len(data_slave_fifteen)):
    data.append([15, total/ data_slave_fifteen[i]])

# Create the pandas DataFrame
df = pd.DataFrame(data, columns=['# of slaves', 'throughput (messages/ms)'])
#df = sns.load_dataset('iris')

print(df)

# Change width
sns.lineplot(x="# of slaves", y="throughput (messages/ms)", data=df)
plt.xticks([1, 2, 3, 4, 5, 7, 10, 15])
plt.ylim([0, 1000])
plt.show()
