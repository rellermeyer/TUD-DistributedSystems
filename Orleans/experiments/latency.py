# library & dataset
import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt

data_slave_one = [181, 148, 151, 150, 150]
data_slave_two = [117, 99, 100, 100, 100]
data_slave_three = [98, 99, 100, 99, 100]
data_slave_four = [89, 75, 74, 76, 74]
data_slave_five = [69, 77, 69, 84, 66]
data_slave_seven = [67, 74, 75, 73, 75]
data_slave_ten = [71, 74, 74, 71, 65]


data = []


for i in range(0, len(data_slave_one)):
    data.append([1, data_slave_one[i]])

for i in range(0, len(data_slave_two)):
    data.append([2, data_slave_two[i]])

for i in range(0, len(data_slave_three)):
    data.append([3, data_slave_three[i]])

for i in range(0, len(data_slave_four)):
    data.append([4, data_slave_four[i]])

for i in range(0, len(data_slave_five)):
    data.append([5, data_slave_five[i]])
for i in range(0, len(data_slave_seven)):
    data.append([7, data_slave_seven[i]])
for i in range(0, len(data_slave_ten)):
    data.append([10, data_slave_ten[i]])

# Create the pandas DataFrame
df = pd.DataFrame(data, columns=['# of slaves', 'latency (in ms)'])
#df = sns.load_dataset('iris')

print(df)

# Change width
sns.lineplot(x="# of slaves", y="latency (in ms)", data=df)
plt.xticks([1, 2, 3, 4, 5, 7, 10])
plt.ylim([0, 170])
plt.show()
