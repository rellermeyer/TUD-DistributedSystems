# testcase 3: Add v1 on one node, remove v1 on other node, lookup v1 on first node
# expected true, after waiting false, false

import requests
import time

newVertex = {"vertexName": "v1"}

url = "http://localhost:"
node1_port = "7000"
node2_port = "7001"
node3_port = "7002"
addvertex_endpoint = "/addvertex"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"

print("adding vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=newVertex)

print(r1.text)

if (r1.text == "true"):
    print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node1_port)
    print("waiting 2 seconds for synchronization")
    time.sleep(2)

    print("removing vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
    r2 = requests.delete(url + node2_port + removevertex_endpoint, json=newVertex)

    print(r2.text)

    if (r2.text == "true"):
        print("Succesfully deleted vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
        print("waiting 2 seconds for synchronization")

        time.sleep(2)

        r1 = requests.get(url + node1_port + lookupvertex_endpoint + "?vertexName=" + newVertex['vertexName'])
        r3 = requests.get(url + node3_port + lookupvertex_endpoint + "?vertexName=" + newVertex['vertexName'])

        print(r1.text)
        print(r3.text)

        if (r1.text == "false" and r3.text == "false"):
            print("Vertex " + newVertex['vertexName'] + " got succesfully removed from other nodes as well")
