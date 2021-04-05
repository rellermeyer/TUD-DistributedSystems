import requests

class Neo4jClient():
    # The url of instance to make the requests to 
    server_url="http://neo4j:mySecretPassword@localhost:7000"
    # The endpoint to handle Cypher queries & commands 
    endpoint = "/db/neo4j/tx/commit"

    def add_vertex(self, vertex_name):
        url = self.server_url + self.endpoint

        add_vertex = {
            "statements" : [ {
                "statement" : "CREATE (v:Vertex {name: '" + vertex_name + "'})"  
            } ]
        }  

        r = requests.post(url, json=add_vertex)
        return (r.status_code == 200 , int(r.elapsed.total_seconds() * 1000))


    def remove_vertex(self, vertex_name):
        url = self.server_url + self.endpoint

        remove_vertex = {
            "statements" : [ {
                "statement" : "MATCH (v:Vertex {name: '" + vertex_name + "'}) DELETE v;"  
            } ]
         }
        
        r = requests.post(url, json=remove_vertex)
        return (r.status_code == 200 , int(r.elapsed.total_seconds() * 1000))

    def add_arc(self, source_vertex, target_vertex):
        url = self.server_url + self.endpoint

        add_arc = {
            "statements" : [ {
                "statement" : "MATCH (va:Vertex), (vb:Vertex) WHERE va.name = '" + source_vertex + \
                    "' AND vb.name = '" + target_vertex +"' CREATE (va)-[r:arc]->(vb);"  
            } ]
         }

        r = requests.post(url, json=add_arc)
        return (r.status_code == 200 , int(r.elapsed.total_seconds() * 1000))

    def remove_arc(self, source_vertex, target_vertex):
        url = self.server_url + self.endpoint

        add_vertex = {
            "statements" : [ {
                "statement" : "MATCH (va:Vertex {name: '" + source_vertex + "'})-[r:arc]->(vb:Vertex {name: '"\
                    + target_vertex + "'}) DELETE r;"  
            } ]
         }

        r = requests.post(url, json=add_vertex)
        return (r.status_code == 200 , int(r.elapsed.total_seconds() * 1000))

    def lookup_vertex(self, vertex_name):
        url = self.server_url + self.endpoint

        lookup_vertex = { 
            "statements" : [ {
                "statement" : "MATCH (v:Vertex {name: '" + vertex_name + "'}) RETURN v;"  
            } ]
        }

        r = requests.post(url, json=lookup_vertex)
        return (r.status_code == 200 and len(r.json()["results"][0]["data"]) > 0 , int(r.elapsed.total_seconds() * 1000))


    def lookup_arc(self, source_vertex, target_vertex):
        url = self.server_url + self.endpoint

        lookup_arc = { 
            "statements" : [ {
                "statement" : "MATCH (va:Vertex {name: '" + source_vertex + "'})-[r:arc]->(vb:Vertex {name: '"\
                    + target_vertex + "'}) RETURN r;"  
            } ]
        }

        r = requests.post(url, json=lookup_arc)
        return (r.status_code == 200 and len(r.json()["results"][0]["data"]) > 0 , int(r.elapsed.total_seconds() * 1000))


