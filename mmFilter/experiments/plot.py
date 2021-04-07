import numpy as np
import matplotlib as mpl
mpl.use('TkAgg')
import matplotlib.pyplot as plt

# Experiment 1
# use frames to represent bandwidth
def experiment1():
    duration = 40
    y1 = [48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48]
    print(sum(y1))
    x1 = np.arange(duration, (len(y1)+1)*duration, duration)

    y2 = [24, 48, 36, 36, 48, 24, 36, 36, 48, 36, 48, 24, 36, 24, 36, 36, 36, 36, 48, 12, 36, 12, 36, 48, 48, 12, 48, 24, 12, 48, 24, 48]
    print(sum(y2))
    x2 = np.arange(duration, (len(y2)+1)*duration, duration)

    p1=plt.plot(x1,y1,'r--',label='non mmFilter')
    p2=plt.plot(x2,y2,'g--',label='mmFilter')
    plt.plot(x1, y1,'ro-', x2, y2,'g+-')
    plt.title('Bandwidth consumption comparision')
    plt.xlabel('time(s)')
    plt.ylabel('frames')
    plt.legend()
    plt.show()

# experiment1()

def experiment2():
    duration = 50
    y1 = [36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36, 36]
    print(len(y1))
    x1 = np.arange(duration, (len(y1) + 1) * duration, duration)

    y2 = [144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144, 144]
    print(len(y2))
    x2 = np.arange(duration, (len(y2) + 1) * duration, duration)

    p1 = plt.plot(x1, y1, 'r--', label='1 edge device')
    p2 = plt.plot(x2, y2, 'g--', label='5 edge devices')
    plt.plot(x1, y1, 'ro-', x2, y2, 'g+-')
    plt.title('')
    plt.xlabel('time(s)')
    plt.ylabel('frames')
    plt.legend()
    plt.show()

# experiment2()

def experiment3():
    duration = 40
    y1 = [24, 48, 36, 36, 48, 24, 36, 36, 48, 36, 48, 24, 36, 24, 36, 36, 36, 36, 48, 12, 36, 12, 36, 48, 48, 12, 48]
    print(len(y1))
    x1 = np.arange(duration, (len(y1) + 1) * duration, duration)

    y2 = [48, 48, 36, 36, 48, 48, 48, 48, 48, 48, 48, 36, 48, 36, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48]
    print(len(y2))
    x2 = np.arange(duration, (len(y2) + 1) * duration, duration)

    y3 = [48, 48, 48, 48, 36, 48, 48, 48, 48, 48, 24, 48, 48, 36, 12, 36, 36, 36, 36, 36, 24, 36, 48, 36, 48, 48, 36]
    x3 = np.arange(duration, (len(y3) + 1) * duration, duration)

    fig, axL = plt.subplots()
    axR = axL.twiny()
    axD = axL.twiny()

    axD.plot(x1, y1, color='r', marker='o', linestyle=':', label='1 edge server & 2 edge devices')
    axL.plot(x2, y2, color='g', marker='+', linestyle=':', label='/')
    axR.plot(x3, y3, color='b', marker='+', linestyle=':', label='2 edge servers & 4 edge devices')

    axL.set_xlabel('time(s)')
    axL.set_ylabel('frames')

    handlesL, labelsL = axL.get_legend_handles_labels()
    handlesR, labelsR = axR.get_legend_handles_labels()
    handlesD, labelsD = axD.get_legend_handles_labels()
    handles = handlesL + handlesR
    labels = labelsL + labelsR
    axL.legend(handles, labels, loc='lower center', bbox_to_anchor=(0.4, 0.0), ncol=2, fontsize=12,
               handletextpad=0.4, columnspacing=0.4)
    axD.legend(handlesD, labelsD, loc='lower center', bbox_to_anchor=(0.34, 0.1), ncol=2, fontsize=12,
               handletextpad=0.4, columnspacing=0.4)

    plt.show()

experiment3()