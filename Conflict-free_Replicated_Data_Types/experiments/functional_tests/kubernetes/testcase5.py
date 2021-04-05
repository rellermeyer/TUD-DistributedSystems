# testcase 5: Add v1, Add v2, add arc (v1, v2), add arc (v2, v1), remove v1, lookup arc(v1,v2), lookup arc(v2, v1)
# expected false, false

import requests

vertex1 = {"vertexName": "v1"}
vertex2 = {"vertexName": "v2"}

arcv1_v2 = {"sourceVertex": "v1", "targetVertex": "v2"}
arcv2_v1 = {"sourceVertex": "v2", "targetVertex": "v1"}

url = "http://localhost:"
node1_port = "7000"
node2_port = "7001"
node3_port = "7002"
addvertex_endpoint = "/addvertex"
addarc_endpoint = "/addarc"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"
lookuparc_endpoint = "/lookuparc"

print("Adding vertex " + vertex1['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex1)

print(r1.text)

if (r1.text == "true"):
    print("succesfully added vertex " + vertex1['vertexName'] + " to node on port " + node1_port)

    print("Adding vertex " + vertex2['vertexName'] + " on node with port " + node1_port)
    r1 = requests.post(url + node1_port + addvertex_endpoint, json=vertex2)
    print(r1.text)

    if (r1.text == "true"):
        print("succesfully added vertex " + vertex2['vertexName'] + " to node on port " + node1_port)

        print("adding arc between v1 and v2")
        r1 = requests.post(url + node1_port + addarc_endpoint, json=arcv1_v2)
        print(r1.text)

        if (r1.text == "true"):
            print("succesfully addded arc between v1 and v2")

            print("adding arc between v2 and v1")
            r1 = requests.post(url + node1_port + addarc_endpoint, json=arcv2_v1)
            print(r1.text)

            if (r1.text == "true"):
                print("succesfully addded arc between v2 and v1")

                print("removing vertex " + vertex1['vertexName'])
                r1 = requests.delete(url + node1_port + removevertex_endpoint, json=vertex1)
                print(r1.text)

                if(r1.text == "true"):
                    print("succesfully removed vertex " + vertex1['vertexName'])
                    print("looking up arc v1-v2")
                    r1 = requests.get(url + node1_port + lookuparc_endpoint + "?sourceVertex=" + vertex1['vertexName'] +
                                      "&targetVertex=" + vertex2['vertexName'])

                    print(r1.text)

                    print("looking up arc v2-v1")
                    r1 = requests.get(url + node1_port + lookuparc_endpoint + "?sourceVertex=" + vertex2['vertexName'] +
                                      "&targetVertex=" + vertex1['vertexName'])

                    print(r1.text)

