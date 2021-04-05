# testcase 9: Add v1, v2 on node 1, Add arc (v1, v2) on node 1 + remove v1 on node 2, lookup v1 on node 1
# expected false (case described in the paper, removeVertex takes precedence)


import requests, time

vertex1 = {"vertexName": "v1"}
vertex2 = {"vertexName": "v2"}

arcv1_v2 = {"sourceVertex": "v1", "targetVertex": "v2"}

url = "http://localhost:"
node1_port = "7000"
node2_port = "7001"
node3_port = "7002"
addvertex_endpoint = "/addvertex"
addarc_endpoint = "/addarc"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"

print("adding vertex " + vertex1['vertexName'] + " on node with port " + node1_port)
r1_1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)

print("adding vertex " + vertex2['vertexName'] + " on node with port " + node1_port)
r1_2 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)

print(r1_1.text)
print(r1_2.text)

print("adding arc between v1 and v2 on node with port " + node1_port)
r1 = requests.post(url + node1_port + addarc_endpoint, json=arcv1_v2)
print(r1.text)

print("waiting 2 seconds for synchronization")
time.sleep(2)

print("removing v1 on node with port " + node2_port)
r2 = requests.delete(url + node2_port + removevertex_endpoint, json=vertex1)
print(r2.text)

print("waiting 2 seconds for synchronization")
time.sleep(2)

print("looking up v1 on node with port " + node1_port)
r1 = requests.get(url + node1_port + lookupvertex_endpoint + "?vertexName=v1")
print(r1.text)
