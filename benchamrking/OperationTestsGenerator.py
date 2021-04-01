
import numpy as np
import math 

number_of_vertices = 100_000
probability_of_an_arc = 0.0005
read_test_operations = 1_000_000
removal_probability = 0.04
random_lookup_probability = 0.1
random_add_probability = 0.1
probability_of_removing_a_vertex = 0.5
probability_of_looking_up_a_vertex = 0.5
avg_degree = number_of_vertices * probability_of_an_arc
std_deviation = math.sqrt((number_of_vertices-1)*probability_of_an_arc*(1-probability_of_an_arc))

write_heavy_test_name = "operations1.txt"
read_heavy_test_name = "operations2.txt"

with open(write_heavy_test_name, "w") as file:
    for i in range(0, number_of_vertices):
        file.write(f"av {i}\n")
        
    print("Written vertices")
    for current_vertex in range(0, number_of_vertices):
        degree = np.random.normal(avg_degree, std_deviation)
        for j in range(0, int(degree)):
            target = np.random.randint(0, number_of_vertices)
            while target == current_vertex:
                target = np.random.randint(0, number_of_vertices)
                
            file.write(f"aa {current_vertex} {target}\n")
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
    for i in range(0, read_test_operations):
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
    
    
