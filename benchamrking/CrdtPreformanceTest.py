
write_heavy_test_name = "operations1.txt"
read_heavy_test_name = "operations2.txt"

client = CrdtClient()

min = 10000
max = 0
total = 0


do_test(write_heavy_test_name)
do_test(read_heavy_test_name)

def do_test(testname):
    min = 10000
    max = 0
    total = 0
    count =0
    with open(testname, "r") as file:
        line = file.readline().split()
        operation = line[0]
        delay = 0
        if(operation=="av"):
            arg=operation[1].rsplit()
            (res, delay) = client.add_vertex(arg)
        elif(operation=="aa"):
            arg1=operation[1].rsplit()
            arg2=operation[2].rsplit()
            (res, delay) = client.add_arc(arg1,arg2)
        elif(operation=="rv"):
            arg=operation[1].rsplit()
            (res, delay) = client.remove_vertex(arg)
        elif(operation=="ra"):
            arg1=operation[1].rsplit()
            arg2=operation[2].rsplit()
            (res, delay) = client.remove_arc(arg1, arg2)
        elif(operation=="lv"):
            arg=operation[1].rsplit()
            (res, delay) = client.lookup_vertex(arg)
        elif(operation=="la"):
            arg1=operation[1].rsplit()
            arg2=operation[2].rsplit()
            (res, delay) = client.lookup_arc(arg1, arg2)
        
        if(delay<min):
            min=delay
        if(delay>max):
            max=delay
            
        total += delay
        count +=1
        
    print(f"Min: {min}")
    print(f"Max: {max}")
    print(f"Total: {total}")
    print(f"Count: {count}")
    
