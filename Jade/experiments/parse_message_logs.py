import os
import sys

log_directory = sys.argv[1]

total_count = 0
for log_file in os.listdir(log_directory):
    with open(os.path.join(log_directory, log_file), 'r') as f:
        contents = f.read()
        total_count += contents.count('received handled message')

print(total_count)
