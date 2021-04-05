import time
from CrdtClient import CrdtClient
from Neo4jClient import Neo4jClient
import threading
import random

# performs the requests from the test_instructions_filename file using the service configured in client
# does not record results - only generates traffic
def start_noise(test_instructions_filename, client):
    count =0 # used only for logging
    with open(test_instructions_filename, "r") as file:
        while True:
            line = file.readline().split()
            if(len(line) == 0):
                break
            operation = line[0]
            delay = 0
            if(operation=="av"):
                arg=line[1]
                (res, delay) = client.add_vertex(arg)
            elif(operation=="aa"):
                arg1=line[1]
                arg2=line[2]
                (res, delay) = client.add_arc(arg1,arg2)
            elif(operation=="rv"):
                arg=line[1]
                (res, delay) = client.remove_vertex(arg)
            elif(operation=="ra"):
                arg1=line[1]
                arg2=line[2]
                (res, delay) = client.remove_arc(arg1, arg2)
            elif(operation=="lv"):
                arg=line[1]
                (res, delay) = client.lookup_vertex(arg)
            elif(operation=="la"):
                arg1=line[1]
                arg2=line[2]
                (res, delay) = client.lookup_arc(arg1, arg2)
            
            count+=1
            if(count % 100 == 0):
                print(f"Tests run {count} operations")
            time.sleep(0.01)
    
    # queue the read-heavy test as a backup if the first one wasn't terminated
    start_noise("operations2.txt", client)
            
    
def start_checks(result_filename, input_client, output_client):
    count = 100 # number of tests to perform
    min = 10000
    max = 0
    total = 0
    with open(result_filename, "w") as result_file:

        for i in range(0, count):
            # add a marker on input node and start the timer
            vertex_name= f"test_{i}"
            input_client.add_vertex(vertex_name)
            start_time = time.time()
            while True:
                # ask repeatedly for the marker on the output node
                (res, delay) = output_client.lookup_vertex(vertex_name)
                if(res):
                    ttc = int((time.time() - start_time) * 1000)
                    # log the result
                    result_file.write(f"{ttc}\n")
                    
                    # update the metrics
                    total += ttc
                    if(ttc<min):
                        min=ttc
                    if(ttc>max):
                        max=ttc
                    break
                # sleep 1ms before checking again
                time.sleep(0.001)
            print(f"Done test {vertex_name}")
            
            # sleep for 4+some random amount time before running the next test
            time.sleep(4+random.random()*4)  
        
        # write the summary at the end of the file
        result_file.write(f"Min: {min}\n")
        result_file.write(f"Max: {max}\n")
        result_file.write(f"Total: {total}\n")
        result_file.write(f"Count: {count}\n")
        print(f"Min: {min}")
        print(f"Max: {max}")
        print(f"Total: {total}")
        print(f"Count: {count}")
    

# CRDT-GRAPH
# setup the traffic mockup
# noise_client = CrdtClient()
# noise_client.load_balancer_url = "http://localhost:8080"
# x = threading.Thread(target=lambda : start_noise("operations1.txt", noise_client))
# x.start()
# time.sleep(10)

# setup the testable servers
# input_client = CrdtClient()
# input_client.load_balancer_url = "http://localhost:7000"

# output_client = CrdtClient()
# output_client.load_balancer_url = "http://localhost:7001"

# start_checks("time_to_consistency_result_crdt.txt")

# NEO4j
# setup the traffic mockup
neo4j_noise_client = Neo4jClient()
neo4j_noise_client.load_balancer_url="http://neo4j:mySecretPassword@localhost:7000"
x = threading.Thread(target=lambda : start_noise("operations1.txt", neo4j_noise_client))
x.start()
time.sleep(10)

# setup the testable servers
input_client = Neo4jClient()
input_client.load_balancer_url = "http://neo4j:mySecretPassword@localhost:7000"

output_client = Neo4jClient()
output_client.load_balancer_url = "http://neo4j:mySecretPassword@localhost:7001"

start_checks("time_to_consistency_result_neo4j.txt")


