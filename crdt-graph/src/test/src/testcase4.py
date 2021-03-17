#testcase 4: Add v1 on node 1, Add v1 on node 2 + Remove on node 1, lookup on node v1(expected true)

import requests
import time

newVertex = {"vertexName": "v1"}

url = "http://localhost:"
node1_port = "8080"
node2_port = "8081"
node3_port = "8082"
addvertex_endpoint = "/addvertex"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"

print("adding vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=newVertex)

print(r1.text)

if (r1.text == "true"):
    print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node1_port)
    print("waiting 10 seconds for synchronization")
    time.sleep(10)


    print("adding vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
    r2 = requests.post(url + node2_port + addvertex_endpoint, json=newVertex)
    print(r1.text)

    if (r2.text == "true"):
        print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node2_port)
        print("waiting 10 seconds for synchronization")
        time.sleep(10)

        print("removing vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
        r1 = requests.delete(url + node1_port + removevertex_endpoint, json=newVertex)

        print(r1.text)

        if (r1.text == "true"):
            print("Succesfully deleted vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
            print("waiting 10 seconds for synchronization")
            time.sleep(10)

            #not finished, lookup needs to be done
