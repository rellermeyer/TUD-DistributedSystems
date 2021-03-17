# testcase 11: Add v1, v2 on one node, add arc (v1, v2) on same node, add same arc on another node
# expected true

import requests

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

    print("adding arc between v1 and v2 on node with port " + node1_port)
    r1 = requests.post(url + node1_port + addarc_endpoint, json=arcv1_v2)
    print(r1.text)


    if (r1.text == "true"):
        print("succesfully addded arc between v1 and v2 on node with port " + node1_port)

        print("adding arc between v1 and v2 on node with port " + node2_port)
        r2 = requests.post(url + node2_port + addarc_endpoint, json=arcv1_v2)
        print(r2.text)


        if (r1.text == "true"):
            print("succesfully addded arc between v1 and v2 on node with port " + node2_port)
