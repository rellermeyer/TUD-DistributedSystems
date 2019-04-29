#!/bin/sh
HOST_IP="35.246.243.109"
RMI_IP="10.156.0.2"
LOG_DIR="logs/experiment_2/instances_1"
HOME_DIR="distributed_systems/out/production/rmi-tact"

REPLICAS=(ReplicaA ReplicaB ReplicaC ReplicaD ReplicaE ReplicaF)
LETTERS=(x y z)

#########################################################################
#                                                                       # 
# Initalize experiment                                                  #
#                                                                       #
#########################################################################

echo "Initialize experiment"

echo "=> Setup instance-01"
ssh sven@instance-01 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    rm -rf ${LOG_DIR};
    mkdir -p ${LOG_DIR}
"

echo "=> Setup instance-02"
ssh sven@instance-02 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    rm -rf ${LOG_DIR};
    mkdir -p ${LOG_DIR}
"

echo "=> Setup instance-03"
ssh sven@instance-03 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    rm -rf ${LOG_DIR};
    mkdir -p ${LOG_DIR}
"
echo ""

#########################################################################
#                                                                       # 
# Start master and 2 replicas per instance                              #
#                                                                       #
#########################################################################

echo "Start master and replicas"
echo "=> Start master on instance-01"
ssh sven@instance-01 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala -Djava.rmi.server.hostname=${HOST_IP} main.scala.history.MasterReplica > ${LOG_DIR}/master.log 2>&1 &
"

echo "=> Start Replica A on instance-01"
ssh sven@instance-01 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} A > ${LOG_DIR}/replicaA.log 2>&1 &
"
echo "=> Start Replica B on instance-01"
ssh sven@instance-01 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} B > ${LOG_DIR}/replicaB.log 2>&1 &
"

echo "=> Start Replica C on instance-02"
ssh sven@instance-02 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} C > ${LOG_DIR}/replicaC.log 2>&1 &
"
echo "=> Start Replica D on instance-02"
ssh sven@instance-02 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} D > ${LOG_DIR}/replicaD.log 2>&1 &
"

echo "=> Start Replica E on instance-03"
ssh sven@instance-03 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} E > ${LOG_DIR}/replicaE.log 2>&1 &
"
echo "=> Start Replica F on instance-03"
ssh sven@instance-03 "
    source /home/sven/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.replica.TactReplica ${RMI_IP} F > ${LOG_DIR}/replicaF.log 2>&1 &
"
echo ""

# Wait for everything to start
sleep 5

echo "Random reads and writes"
for r in {1..5}
do
    #########################################################################
    #                                                                       # 
    # Write simulation                                                      #
    #                                                                       #
    #########################################################################

    for i in {1..75}
    do
        REPLICA="ReplicaA"        
        READORWRITE="write"

        RND_LETTERS=$((RANDOM % 3))
        LETTER=${LETTERS[$RND_LETTERS]}
        
        ssh sven@instance-01 "
            source /home/sven/.sdkman/bin/sdkman-init.sh;
            cd ${HOME_DIR};
            echo -ne '($i/75) $REPLICA: ';
            scala main.scala.client.Client ${RMI_IP} ${REPLICA} ${READORWRITE} ${LETTER} 1 &
        "

        sleep $(bc -l <<< "scale=4 ; ${RANDOM}/32767")
    done
    echo ""

    echo "Fetch Master results:"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.History ${RMI_IP}
    "
    echo ""


    #########################################################################
    #                                                                       # 
    # Fetching results                                                      #
    #                                                                       #
    #########################################################################

    echo "Results:"
    echo "=> Replica A"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaA read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaA read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaA read z;
    "
    echo "=> Replica B"
    ssh sven@instance-01 "    
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaB read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaB read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaB read z;
    "
    echo "=> Replica C"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaC read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaC read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaC read z;
    "
    echo "=> Replica D"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaD read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaD read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaD read z;
    "
    echo "=> Replica E"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaE read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaE read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaE read z;
    "
    echo "=> Replica F"
    ssh sven@instance-01 "
        source /home/sven/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        scala main.scala.client.Client ${HOST_IP} ReplicaF read x;
        scala main.scala.client.Client ${HOST_IP} ReplicaF read y;
        scala main.scala.client.Client ${HOST_IP} ReplicaF read z;
    "
done


#########################################################################
#                                                                       #
# Kill the master and replicas                                          #
#                                                                       #
#########################################################################

echo "Stop the master and all the replicas..."
ssh sven@instance-01 "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh sven@instance-02 "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh sven@instance-03 "lsof -tc java | xargs --no-run-if-empty kill -9"


#########################################################################
#                                                                       #
# Fetch generated logs                                                  #
#                                                                       #
#########################################################################

sleep 10;
echo "Fetching logs..."
echo "=> Remove old logs"
mkdir -p $LOG_DIR

echo "=> Obtain new logs"
ssh sven@instance-01 "cat ${HOME_DIR}/${LOG_DIR}/master.log" > $LOG_DIR/master.log
ssh sven@instance-01 "cat ${HOME_DIR}/${LOG_DIR}/replicaA.log" > $LOG_DIR/replicaA.log
ssh sven@instance-01 "cat ${HOME_DIR}/${LOG_DIR}/replicaB.log" > $LOG_DIR/replicaB.log
ssh sven@instance-02 "cat ${HOME_DIR}/${LOG_DIR}/replicaC.log" > $LOG_DIR/replicaC.log
ssh sven@instance-02 "cat ${HOME_DIR}/${LOG_DIR}/replicaD.log" > $LOG_DIR/replicaD.log
ssh sven@instance-03 "cat ${HOME_DIR}/${LOG_DIR}/replicaE.log" > $LOG_DIR/replicaE.log
ssh sven@instance-03 "cat ${HOME_DIR}/${LOG_DIR}/replicaF.log" > $LOG_DIR/replicaF.log
echo "";

echo "Done!"
