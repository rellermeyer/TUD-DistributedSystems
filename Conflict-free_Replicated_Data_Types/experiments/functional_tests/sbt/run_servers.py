import os, subprocess, time

#move to /crdt-graph
os.chdir("../../../../")

#kill other processes running on these ports
subprocess.Popen("fuser -k 8080/tcp".split())
subprocess.Popen("fuser -k 8081/tcp".split())
subprocess.Popen("fuser -k 8082/tcp".split())


#start node 1
server_startup_command1 = 'sh ../../../../sbt_run.sh 8080'
node1 = subprocess.Popen(server_startup_command1, universal_newlines=True, shell=True)
time.sleep(2)

#start node 2
server_startup_command2 = 'sh ../../../../sbt_run.sh 8081'
node2 = subprocess.Popen(server_startup_command2, universal_newlines=True, shell=True)
time.sleep(2)

#start node 3
server_startup_command3 = 'sh ../../../../sbt_run.sh 8082'
node3 = subprocess.Popen(server_startup_command3, universal_newlines=True, shell=True)
time.sleep(2)

