# testcase 7: Add v1, v2 on node 1, add arc v1-v2 on node 2, add same arc on another node
# expected: true


import requests
import time

vertex1 = {"vertexName": "v1"}
vertex2 = {"vertexName": "v2"}

arcv1_v2 = {"sourceVertex": "v1", "targetVertex": "v2"}

url = "http://localhost:"
node1_port = "8080"
node2_port = "8081"
node3_port = "8082"
addvertex_endpoint = "/addvertex"
lookupvertex_endpoint = "/lookupvertex"
addarc_endpoint = "/addarc"

print("adding vertex " + vertex1['vertexName'] + " on node with port " + node1_port)
r1_1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)

print("adding vertex " + vertex2['vertexName'] + " on node with port " + node1_port)
r1_2 = requests.post(url + node1_port + addvertex_endpoint, json=vertex2)


print(r1_1.text)
print(r1_2.text)

if (r1_1.text == "true" and r1_2.text == "true"):
    print("succesfully added vertex " + vertex1['vertexName'] + " to node on port " + node1_port)
    print("succesfully added vertex " + vertex2['vertexName'] + " to node on port " + node1_port)
    print("waiting 2 seconds for synchronization")
    time.sleep(2)

    print("adding arc between v1 and v2 on node " + node2_port)
    r2 = requests.post(url + node2_port + addarc_endpoint, json=arcv1_v2)
    print(r2.text)

    if (r2.text == "true"):
        print("succesfully addded arc between v1 and v2")
        print("waiting 2 seconds for synchronization")
        time.sleep(2)

        print("adding arc between v1 and v2 on node " + node3_port)
        r3 = requests.post(url + node3_port + addarc_endpoint, json=arcv1_v2)
        print(r3.text)

        if (r3.text == "true"):
            print("succesfully addded arc between v1 and v2")

