import numpy as np
import matplotlib.pyplot as plt
data = np.loadtxt('eval.txt')

x = data[:, 0]
y = data[:, 1]
plt.plot(x, y, 'b.')
plt.xlabel('Number of rounds')
plt.ylabel('Latency (average number of hops)')
plt.show()