from time import sleep
from CrdtClient import CrdtClient
from Neo4jClient import Neo4jClient
 
# performs the requests from the test_instructions_filename file using the service configured in client
# results are written to result_filename
# the summary writen at the end of the file include minimal, total and count (average), maximal values
def do_test(test_instructions_filename, client, result_filename):
    with open(result_filename, "w") as result_file:
        min = 10000
        max = 0
        total = 0
        count = 0
        with open(test_instructions_filename, "r") as test_instructions:
            while True:
                line = test_instructions.readline().split()
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
                
                result_file.write(f"{delay}\n") # log result
                
                # update metrics
                if(delay<min):
                    min=delay
                if(delay>max):
                    max=delay
                    
                total += delay
                count +=1
                if(count % 100 == 0):
                    print(f"Tests run {count} operations")
                sleep(0.001) # wait 1ms between requests
                
        # add the summary to the end of the file
        result_file.write(f"Min: {min}\n")
        result_file.write(f"Max: {max}\n")
        result_file.write(f"Total: {total}\n")
        result_file.write(f"Count: {count}\n")
        print(f"Min: {min}")
        print(f"Max: {max}")
        print(f"Total: {total}")
        print(f"Count: {count}")
    
    

# client = CrdtClient()
client = Neo4jClient()

do_test("operations1.txt", client, "result_write_heavy.txt")
sleep(10)
do_test("operations2.txt", client, "result_read_heavy.txt")
