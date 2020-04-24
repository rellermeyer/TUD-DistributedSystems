#!/usr/bin/env bash
# move previous measurements
if [ -f ./DistributedObject/responseTimesScalability.csv ]
then
  mv -v ./DistributedObject/responseTimesScalability.csv ./DistributedObject/responseTimesScalability_prev.csv
fi
if [ -f ./DistributedObject/responseTimesConcurrency.csv ]
then
  mv -v ./DistributedObject/responseTimesConcurrency.csv ./DistributedObject/responseTimesConcurrency_prev.csv
fi
nohup ./gradlew :LookupService:run &> LookupService.log &
pids=$!
sleep 5
# deploy the system with different amounts of Distributed Object replicas
port=8081
for i in {1..10}
  do
  nohup ./gradlew :DistributedObject:run -Pdistributedobject.url=http://localhost:$port &> DistributedObject.$port.log &
  pids="$pids $!"
  ((port++))
  sleep 1
done
# Run the evaluation tests multiple times on this deployment
for iteration in {1..20}
do
./gradlew systemTest --rerun-tasks
done
kill $pids
