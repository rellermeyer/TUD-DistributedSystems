
import numpy as np
import math 
# number will be decreased by a small amount when some deletions happen 
number_of_vertices = 5_000 
# probability of an arc between any two instances 
probability_of_an_arc = 0.001 
# number of reads in the read-heavy test 
read_test_operations = 20_000 
# probability of removing a random vertex in each after processing each vertex
removal_probability = 0.04
# probability of adding a lookup command after each add arc command in write-heavy test
random_lookup_probability = 0.1
# probability of adding an add command after each lookup command in read-heavy test 
random_add_probability = 0.1
# used in the write-heavy test. prabability of removing a vertex. Removing an arc has a 1-x probability
probability_of_removing_a_vertex = 0.5
# used in the read-heavy test. prabability of looking up a vertex. Looking up an arc has a 1-x probability
probability_of_looking_up_a_vertex = 0.5

avg_degree = number_of_vertices * probability_of_an_arc
std_deviation = math.sqrt((number_of_vertices-1)*probability_of_an_arc*(1-probability_of_an_arc))

write_heavy_test_name = "operations1.txt"
read_heavy_test_name = "operations2.txt"

with open(write_heavy_test_name, "w") as file:
    # write the vertices first so you dont get errors in neo4j
    for i in range(0, number_of_vertices):
        file.write(f"av {i}\n")
    print("Written vertices")
    
    # start adding the arcs
    for current_vertex in range(0, number_of_vertices):
        # get the degree of the vertex using the normal distribution
        degree = np.random.normal(avg_degree, std_deviation)
        for j in range(0, int(degree)):
            # select a target and write the operation to the instruction set
            target = np.random.randint(0, number_of_vertices)
            while target == current_vertex:
                target = np.random.randint(0, number_of_vertices)
                
            file.write(f"aa {current_vertex} {target}\n")
            
            # add rare random lookups durring the write-heavy test
            if(np.random.ranf()<random_lookup_probability):
                if(np.random.ranf()<probability_of_looking_up_a_vertex):
                    vertex_to_look = np.random.randint(0, number_of_vertices)
                    file.write(f"lv {vertex_to_look}\n")
                else:
                    source_arc_to_look = np.random.randint(0, number_of_vertices)
                    target_arc_to_look = np.random.randint(0, number_of_vertices)
                    file.write(f"la {source_arc_to_look} {target_arc_to_look}\n")
            
            
        if(current_vertex % 1000 == 0):
            print(f"Written arcs for {current_vertex} vertices")
        
        # after processing the arcs of an vertex add a rare random removal command
        if(np.random.ranf()<removal_probability):
            if(np.random.ranf()<probability_of_removing_a_vertex):
                vertex_to_remove = np.random.randint(0, number_of_vertices)
                file.write(f"rv {vertex_to_remove}\n")
            else:
                source_arc_to_rmv = np.random.randint(0, number_of_vertices)
                target_arc_to_rmv = np.random.randint(0, number_of_vertices)
                file.write(f"ra {source_arc_to_rmv} {target_arc_to_rmv}\n")
                            
    print("Written arcs")
    
    
with open(read_heavy_test_name, "w") as file:
    # write the read_test_operations read operations
    for i in range(0, read_test_operations):
        # before each read operation add a rare random write command
        if(np.random.ranf()<random_add_probability):
            file.write(f"av x{i}\n")

        if(np.random.ranf()<probability_of_looking_up_a_vertex):
            vertex_to_look = np.random.randint(0, number_of_vertices)
            file.write(f"lv {vertex_to_look}\n")
        else:
            source_arc_to_look = np.random.randint(0, number_of_vertices)
            target_arc_to_look = np.random.randint(0, number_of_vertices)
            file.write(f"la {source_arc_to_look} {target_arc_to_look}\n")
                
        if(i % 10_000 == 0):
            print(f"Written {i} lookups")
            
            
    print("Written lookups")
    
    
