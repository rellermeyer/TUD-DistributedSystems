# testcase 14: Add v1, v2 on one node, remove arc (v1, v2) on another node, add arc (v1, v2) on third node,
# remove arc (v1, v2) on first node, remove arc on second node
# expected true, true, false, true, true, false

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
r1_1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)

print("Adding vertex " + vertex2['vertexName'] + " on node with port " + node1_port)
r1_2 = requests.post(url + node1_port + addvertex_endpoint, json=vertex2)

print(r1_1.text)
print(r1_2.text)

if (r1_1.text == "true" and r1_2.text == "true"):
    print("succesfully added vertex " + vertex1['vertexName'] + " to node on port " + node1_port)
    print("succesfully added vertex " + vertex2['vertexName'] + " to node on port " + node1_port)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)

    # remove arc (v1, v2) on second node
    print("removing arc on node with port " + node2_port)
    r2 = requests.delete(url + node2_port + removearc_endpoint, json=arcv1_v2)
    print(r2.text)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)

    # add arc (v1, v2) on third node,
    print("adding arc on node with port " + node2_port)
    r3 = requests.post(url + node3_port + addarc_endpoint, json=arcv1_v2)
    print(r3.text)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)


    # remove arc (v1, v2) on first node
    print("removing arc on node with port " + node1_port)
    r1 = requests.delete(url + node1_port + removearc_endpoint, json=arcv1_v2)
    print(r1.text)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)


    # remove arc on second node
    print("removing arc on node with port " + node2_port)
    r2 = requests.delete(url + node2_port + removearc_endpoint, json=arcv1_v2)
    print(r2.text)
