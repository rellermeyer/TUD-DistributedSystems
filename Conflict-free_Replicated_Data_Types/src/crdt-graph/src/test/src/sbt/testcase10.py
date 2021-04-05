# testcase 10: Add v1 on node 1, add v1 on node 2, wait, remove v1 on node 1 +
# remove v1 on node 2, wait, lookup v1 on node 3
# expected false, show removes commune

import requests, time

vertex1 = {"vertexName": "v1"}
vertex2 = {"vertexName": "v2"}

arcv1_v2 = {"sourceVertex": "v1", "targetVertex": "v2"}
arcv2_v1 = {"sourceVertex": "v2", "targetVertex": "v1"}

url = "http://localhost:"
node1_port = "8080"
node2_port = "8081"
node3_port = "8082"
addvertex_endpoint = "/addvertex"
addarc_endpoint = "/addarc"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"
removearc_endpoint = "/removearc"
lookuparc_endpoint = "/lookuparc"

print("Adding vertex " + vertex1['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)
print(r1.text)

print("Adding vertex " + vertex1['vertexName'] + " on node with port " + node2_port)
r2 = requests.post(url + node2_port + addvertex_endpoint, json=vertex1)
print(r2.text)


print("waiting for 2 seconds")
time.sleep(2)

print("removing v1 on node with port " + node1_port)
r1 = requests.delete(url + node1_port + removevertex_endpoint, json=vertex1)
print(r2.text)

print("removing v1 on node with port " + node2_port)
r2 = requests.delete(url + node2_port + removevertex_endpoint, json=vertex1)
print(r2.text)

print("waiting for 2 seconds")
time.sleep(2)

print("looking up v1 on node with port " + node3_port)
r3 = requests.get(url + node3_port + lookupvertex_endpoint + "?vertexName=" + vertex1['vertexName'])
print(r3.text)