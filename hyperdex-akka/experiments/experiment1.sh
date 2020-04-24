amounts=(1 2 4 8)
#amounts=(1)
for ((i = 0 ; i < ${#amounts[@]}; i++)); do
    amount=${amounts[i]}
    export NUM_DATANODES=$amount
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment1Get"
    docker-compose down
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment1Put"
    docker-compose down
    docker-compose up --scale datanode=$NUM_DATANODES -d
    sleep 20
    sbt "gatling:testOnly hyperdex.Experiment1Search"
    docker-compose down

    mkdir "experiment1-${amount}n"
    mkdir "experiment1-${amount}n/get"
    mkdir "experiment1-${amount}n/put"
    mkdir "experiment1-${amount}n/search"
    cp -r target/gatling/experiment1get*/* "experiment1-${amount}n/get"
    cp -r target/gatling/experiment1put*/* "experiment1-${amount}n/put"
    cp -r target/gatling/experiment1search*/* "experiment1-${amount}n/search"
    rm -rf target/gatling/experiment1get*
    rm -rf target/gatling/experiment1put*
    rm -rf target/gatling/experiment1search*
done
