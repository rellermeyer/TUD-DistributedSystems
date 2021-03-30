import os, subprocess, time, requests, threading

#move to /crdt-graph
os.chdir("../../../../")

subprocess.Popen("fuser -k 8080/tcp".split())
subprocess.Popen("fuser -k 8081/tcp".split())
subprocess.Popen("fuser -k 8082/tcp".split())

server_startup_command1 = 'sbt "run 8080"'
node1 = subprocess.Popen(server_startup_command1, universal_newlines=True, shell=True)
time.sleep(2)

server_startup_command2 = 'sbt "run 8081"'
node2 = subprocess.Popen(server_startup_command2, universal_newlines=True, shell=True)
time.sleep(2)

server_startup_command3 = 'sbt "run 8082"'
node3 = subprocess.Popen(server_startup_command3, universal_newlines=True, shell=True)
time.sleep(2)

