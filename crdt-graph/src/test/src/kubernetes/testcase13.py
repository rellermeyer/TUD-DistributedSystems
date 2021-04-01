# testcase 13: Remove v1 on one node, add v1 on another node, remove v1 on first node
# expected false, true, true

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


print("removing vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
r1 = requests.delete(url + node1_port + removevertex_endpoint, json=newVertex)

print(r1.text)

if(r1.text == "false"):
    print("could not remove vertex " + newVertex['vertexName'] + " on node with port " + node1_port)


    print("adding vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
    r2 = requests.post(url + node2_port + addvertex_endpoint, json=newVertex)
    print(r2.text)

    if (r2.text == "true"):
        print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node2_port)
        print("waiting 2 seconds for synchronization")
        time.sleep(2)


        print("removing vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
        r1 = requests.delete(url + node1_port + removevertex_endpoint, json=newVertex)

        print(r1.text)

        if (r2.text == "true"):
            print("Succesfully deleted vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
