import os
from functions.getSizeOfFiles import format_bytes
from results import getRunResult, getSumOfField, getStandardDeviation
import json

option = "ack"

DIRECTORY_RUNS = f"../runs/runs_{option}"

runResults = {}
avgSummary = {}
stdSummary = {}

for directory in os.listdir(DIRECTORY_RUNS):
    directoryFull = DIRECTORY_RUNS+"/"+directory
    runResults[directory] = getRunResult(directoryFull)


for field in runResults[directory]:
    if not isinstance(runResults[directory][field], str):
        if "std" not in field:
            stdSummary[field] = getStandardDeviation(runResults, field)
        avgSummary[field] = getSumOfField(runResults, field)/len(runResults)


avgSummary["traffic_sent"] = format_bytes(avgSummary["traffic_sent_bytes"])
avgSummary["avg_traffic_sent"] = format_bytes(avgSummary["traffic_sent_bytes"]/len(runResults))

stdSummary["traffic_sent"] = format_bytes(stdSummary["traffic_sent_bytes"])
stdSummary["avg_traffic_sent"] = format_bytes(stdSummary["traffic_sent_bytes"]/len(runResults))

results = {"avg": avgSummary, "std": stdSummary}

with open(f'accumulative_results/{option}.json', 'w') as file_write:
    file_write.write(json.dumps(results, indent=4))
