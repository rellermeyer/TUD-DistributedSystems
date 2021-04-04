#!/usr/bin/env bash

#echo "Round,CommitType,runTime,throughput,msg/s,NrTransactions,NrBackupSites,NrParticipants,timeoutBackupSiteThreshold,timeoutBlockingThreshold,voteAbortProbability,latencyLong,latencyBackup,timeToWriteLog,timeToWritePersistent" > ./results.csv

#echo "numparticipants test 3-8-13-18" >> ./results.csv
#
#sbt "run 2PC NrParticipants 3"
#sbt "run 2PC NrParticipants 8"
#sbt "run 2PC NrParticipants 13"
#sbt "run 2PC NrParticipants 18"
#
#sbt "run BC NrParticipants 3"
#sbt "run BC NrParticipants 8"
#sbt "run BC NrParticipants 13"
#sbt "run BC NrParticipants 18"
#
#
#echo "latency test 1-300\n" >> ./results.csv
#sbt "run 2PC latencyLong 1"
#sbt "run 2PC latencyLong 300"
#
#sbt "run BC latencyLong 1"
#sbt "run BC latencyLong 300"
#
#echo "abort prob test 0-0.01-0.1\n" >> ./results.csv
#sbt "run 2PC voteAbortProbability 0"
sbt "run 2PC voteAbortProbability 0.01"
sbt "run 2PC voteAbortProbability 0.1"

sbt "run BC voteAbortProbability 0"
sbt "run BC voteAbortProbability 0.01"
sbt "run BC voteAbortProbability 0.1"

cp ./results.csv ./results-`date +"%F_%H:%M"`.csv

# Don't give useful results I think?

#sbt "run BC timeToWritePersistent 10" &> output/log26.txt 2>&1 &
#sbt "run BC timeToWritePersistent 50" &> output/log27.txt 2>&1 &
#
#sbt "run BC timeToWriteLog 0" &> output/log28.txt 2>&1 &
#sbt "run BC timeToWriteLog 10" &> output/log29.txt 2>&1 &
#
#sbt "run BC voteAbortProbability 0" &> output/log30.txt 2>&1 &
#sbt "run BC voteAbortProbability 0.01" &> output/log31.txt 2>&1 &
#sbt "run BC voteAbortProbability 0.1" &> output/log32.txt 2>&1 &
#
#sbt "run BC timeoutBlockingThreshold 1000" &> output/log33.txt 2>&1 &
#sbt "run BC timeoutBackupSiteThreshold 1000" &> output/log34.txt 2>&1 &