#!/usr/bin/env bash
echo "$(date -Iminutes): Start evaluation"
# move previous measurements
if [ -f ./DistributedObject/responseTimesScalability.csv ]
then
  mv -v ./DistributedObject/responseTimesScalability.csv ./DistributedObject/responseTimesScalability_prev.csv
fi
if [ -f ./DistributedObject/responseTimesConcurrency.csv ]
then
  mv -v ./DistributedObject/responseTimesConcurrency.csv ./DistributedObject/responseTimesConcurrency_prev.csv
fi
echo "$(date -Iminutes): Creating evaluator EC2 instance"
# Deploy the EC2 instance used to run the evaluation experiments
./gradlew deployEvaluator -Pec2.type=c4.large
mv instanceIds instanceIdEvaluator
# deploy the system with different amounts of Distributed Object replicas
for replicas in 2 3 5 7
do
  echo "$(date -Iminutes): Deploy system with $replicas replicas"
  ./deploySystem.sh $replicas
  echo "$(date -Iminutes): Finished deploying system with $replicas replicas"
  # Give the system some time to ensure it's fully started and ready for testing
  sleep 30
  # Run the evaluation tests multiple times on this deployment
  for iteration in {1..20}
  do
	echo "$(date -Iminutes): Running test iteration $iteration on system with $replicas replicas"
    ssh -o stricthostkeychecking=no ubuntu@$(cat evaluatorHost) "cd GlobeReplicator; ./gradlew --rerun-tasks -Plookupservice.url=$(cat lookupServiceUrl) systemTest"
    echo "$(date -Iminutes): Finished running test iteration $iteration on system with $replicas replicas"
    # Give the system time to fully finish the system test and become ready for the next iteration of testing
    sleep 5
  done
  # Shut down the system and remove the EC2 instances
  echo "$(date -Iminutes): Terminating EC2 instances"
  ./gradlew terminateEC2Instances
done
echo "$(date -Iminutes): Copying measurements from evaluator EC2 instance"
# Copy back the measurements taken in the evaluator EC2 instance
scp -o stricthostkeychecking=no ubuntu@$(cat evaluatorHost):~/GlobeReplicator/DistributedObject/responseTimesScalability.csv "responseTimesScalability.csv"
scp -o stricthostkeychecking=no ubuntu@$(cat evaluatorHost):~/GlobeReplicator/DistributedObject/responseTimesConcurrency.csv "responseTimesConcurrency.csv"
scp -o stricthostkeychecking=no ubuntu@$(cat evaluatorHost):~/GlobeReplicator/DistributedObject/nonReplicatedResponseTimes.csv "nonReplicatedResponseTimes.csv"
# Clean up the evaluator EC2 instance
echo "$(date -Iminutes): Terminating evaluator EC2 instance"
mv instanceIdEvaluator instanceIds
./gradlew terminateEC2Instances
rm evaluatorHost
echo "$(date -Iminutes): Finished evaluation"
