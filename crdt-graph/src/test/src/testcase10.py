# testcase 10: Add v1 on one node, add v1 on another node
# expected true

import requests
import time

newVertex = {"vertexName": "v1"}

url = "http://localhost:"
node1_port = "8080"
node2_port = "8081"
node3_port = "8082"
addvertex_endpoint = "/addvertex"
lookupvertex_endpoint = "/lookupvertex"

print("adding vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=newVertex)

print(r1.text)

if (r1.text == "true"):
    print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node1_port)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)

    print("adding vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
    r2 = requests.post(url + node2_port + addvertex_endpoint, json=newVertex)

    if (r2.text == "true"):
        print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node2_port)