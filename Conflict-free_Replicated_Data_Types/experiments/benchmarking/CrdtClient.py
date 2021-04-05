import requests

class CrdtClient:
    # The url of the loadbalancer or of a instance
    server_url="http://localhost:8080"
    
    def add_vertex(self, vertex_name):
        body = {"vertexName": vertex_name}
        r = requests.post(f"{self.load_balancer_url}/addvertex", json=body) 

        return (r.status_code == 200 , int(r.elapsed.total_seconds() * 1000))
    
    
    def add_arc(self, source_vertex, target_vertex):
        body = {"sourceVertex": source_vertex, "targetVertex": target_vertex}
        r = requests.post(f"{self.load_balancer_url}/addarc", json=body) 
        
        return (r.status_code == 200, int(r.elapsed.total_seconds() * 1000))
    
    
    def remove_vertex(self, vertex_name):
        body = {"vertexName": vertex_name}
        r = requests.delete(f"{self.load_balancer_url}/removevertex", json=body) 

        return (r.status_code == 200, int(r.elapsed.total_seconds() * 1000))
    
    
    def remove_arc(self, source_vertex, target_vertex):
        body = {"sourceVertex": source_vertex, "targetVertex": target_vertex}
        r = requests.delete(f"{self.load_balancer_url}/removearc", json=body) 
        
        return (r.status_code == 200, int(r.elapsed.total_seconds() * 1000))
    
    
    
    def lookup_vertex(self, vertex_name):
        r = requests.get(f"{self.load_balancer_url}/lookupvertex?vertexName={vertex_name}") 

        return (r.status_code == 200 and r.text == "true", int(r.elapsed.total_seconds() * 1000))
    
    
    def lookup_arc(self, source_vertex, target_vertex):
        r = requests.get(f"{self.load_balancer_url}/lookuparc?sourceVertex={source_vertex}&targetVertex={target_vertex}") 

        return (r.status_code == 200 and r.text == "true", int(r.elapsed.total_seconds() * 1000))