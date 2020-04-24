#!/usr/bin/env bash
if [ -z $1 ] 
then
	echo "Please provide the number of replicas as argument, e.g. ./deploySystem 3"
	exit -1
fi

echo "$(date -Iminutes): Deploying Lookup Service "
./gradlew deployLookupService
# Wait for Lookup Service to have completely started so the Distributed Object can register itself at startup.
sleep 60
for i in $(seq 1 $1)
do
	echo "$(date -Iminutes): Deploying Distributed Object $i"
	./gradlew deployDistributedObject
done
