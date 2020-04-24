amounts=(1 2 4 8)
for ((i = 0 ; i < ${#amounts[@]}; i++)); do
    amount=${amounts[i]}
    export NUM_DATANODES=$amount 
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment3" 
    docker-compose down
    mkdir "experiment3-${amount}n"
    cp -r target/gatling/experiment3*/* "experiment3-${amount}n"
    rm -rf target/gatling/experiment3*
done
