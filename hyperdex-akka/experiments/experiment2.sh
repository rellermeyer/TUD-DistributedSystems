amounts=(1 2 4 8)
for ((i = 0 ; i < ${#amounts[@]}; i++)); do
    amount=${amounts[i]}
    export NUM_DATANODES=$amount 
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment2" 
    docker-compose down
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment2b" 
    docker-compose down
    mkdir "experiment2-${amount}n"
    mkdir "experiment2b-${amount}n"
    cp -r target/gatling/experiment2b*/* "experiment2b-${amount}n"
    rm -rf target/gatling/experiment2b*
    cp -r target/gatling/experiment2*/* "experiment2-${amount}n"
    rm -rf target/gatling/experiment2*
done
