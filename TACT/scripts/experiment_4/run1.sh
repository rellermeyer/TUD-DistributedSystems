#!/bin/sh
HOST_IP="35.246.243.109"
RMI_IP="10.156.0.2"
LOG_DIR="logs/experiment_4/instances_1"
HOME_DIR="distributed_systems/out/production/rmi-tact"
USER="sven"

# Instances 
INSTANCE_01_IP="instance-01"
INSTANCE_02_IP="instance-02"
INSTANCE_03_IP="instance-03"

INSTANCES=(${INSTANCE_01_IP} ${INSTANCE_02_IP} ${INSTANCE_03_IP})

# Replicas
REPLICAS_INSTANCE_01=(ReplicaA ReplicaB)
REPLICAS_INSTANCE_02=(ReplicaC ReplicaD)
REPLICAS_INSTANCE_03=(ReplicaE ReplicaF)

REPLICAS=("${REPLICAS_INSTANCE_01[@]}" "${REPLICAS_INSTANCE_02[@]}" "${REPLICAS_INSTANCE_03[@]}")

# Charachters used
LETTERS=(x y z)

# #########################################################################
# #                                                                       # 
# # Initalize experiment                                                  #
# #                                                                       #
# #########################################################################

echo "Initialize experiment..."
for instance in ${INSTANCES[@]}
do
    echo "=> Setup ${instance}"
    ssh ${USER}@${instance} "
        source /home/${USER}/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        rm -rf ${LOG_DIR};
        mkdir -p ${LOG_DIR}
    "
done
echo "Done! \n"

# #########################################################################
# #                                                                       # 
# # Start master and replicas per instance                              #
# #                                                                       #
# #########################################################################

echo "Start master and replicas..."
echo "=> Start master on ${INSTANCE_01_IP}"
ssh ${USER}@${INSTANCE_01_IP} "
    source /home/${USER}/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala -Djava.rmi.server.hostname=${HOST_IP} main.scala.history.MasterReplica > ${LOG_DIR}/master.log 2>&1 &
"

echo "=> Start replicas on ${INSTANCE_01_IP}"
for replica in ${REPLICAS_INSTANCE_01[@]}
do
    echo "\t => Start ${replica} on ${INSTANCE_01_IP}"
    ssh ${USER}@${INSTANCE_01_IP} "
        source /home/${USER}/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        nohup scala main.scala.replica.TactReplica ${RMI_IP} ${replica: -1} > ${LOG_DIR}/${replica}.log 2>&1 &
    "
done

echo "=> Start replicas on ${INSTANCE_02_IP}"
for replica in ${REPLICAS_INSTANCE_02[@]}
do
    echo "\t => Start ${replica} on ${INSTANCE_02_IP}"
    ssh ${USER}@${INSTANCE_02_IP} "
        source /home/${USER}/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        nohup scala main.scala.replica.TactReplica ${RMI_IP} ${replica: -1} > ${LOG_DIR}/${replica}.log 2>&1 &
    "
done

echo "=> Start replicas on ${INSTANCE_03_IP}"
for replica in ${REPLICAS_INSTANCE_03[@]}
do
    echo "\t => Start ${replica} on ${INSTANCE_03_IP}"
    ssh ${USER}@${INSTANCE_03_IP} "
        source /home/${USER}/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR};
        nohup scala main.scala.replica.TactReplica ${RMI_IP} ${replica: -1} > ${LOG_DIR}/${replica}.log 2>&1 &
    "
done

# Wait for everything to start
sleep 5

echo "=> Start coordinator on ${INSTANCE_01_IP}"
ssh ${USER}@${INSTANCE_01_IP} "
    source /home/${USER}/.sdkman/bin/sdkman-init.sh;
    cd ${HOME_DIR};
    nohup scala main.scala.history.VoluntaryCoordinator ${RMI_IP} > ${LOG_DIR}/coordinator.log 2>&1 &
"
echo "Done! \n"

# Wait for coordinator to start
sleep 5

#########################################################################
#                                                                       # 
# Simulation                                                            #
#                                                                       #
#########################################################################

echo "Start simulation"
for r in {1..5}
do
    #########################################################################
    #                                                                       # 
    # Write simulation                                                      #
    #                                                                       #
    #########################################################################

    for i in {1..75}
    do
        RND_REPLICA=$((RANDOM % ${#REPLICAS[@]}))
        REPLICA=${REPLICAS[$RND_REPLICA]}

        RND_LETTERS=$((RANDOM % ${#LETTERS[@]}))
        LETTER=${LETTERS[$RND_LETTERS]}

        COMMAND="
                source /home/${USER}/.sdkman/bin/sdkman-init.sh;
                cd ${HOME_DIR};
                echo -ne '($i/75) $REPLICA: ';
                scala main.scala.client.Client ${RMI_IP} ${REPLICA} write ${LETTER} 1
            "
        
        # Run command
        if  [[ " ${REPLICAS_INSTANCE_01[*]} " == *" ${REPLICA} "* ]]; then
            ssh ${USER}@${INSTANCE_01_IP} ${COMMAND}
        fi

        if  [[ " ${REPLICAS_INSTANCE_02[*]} " == *" ${REPLICA} "* ]]; then
            ssh ${USER}@${INSTANCE_02_IP} ${COMMAND}
        fi

        if  [[ " ${REPLICAS_INSTANCE_03[*]} " == *" ${REPLICA} "* ]]; then
            ssh ${USER}@${INSTANCE_03_IP} ${COMMAND}
        fi

        # Sleep for max 2 seconds
        sleep $(bc -l <<< "scale=4 ; ${RANDOM}/16383")
    done
    echo "Done! \n"

    #########################################################################
    #                                                                       # 
    # Fetching results                                                      #
    #                                                                       #
    #########################################################################

    echo "Fetch Master results:"
    ssh ${USER}@${INSTANCE_01_IP} "
        source /home/${USER}/.sdkman/bin/sdkman-init.sh;
        cd ${HOME_DIR}; 
        scala main.scala.client.History ${RMI_IP}
    "
    echo "Done! \n"

    echo "Results:"
    for replica in ${REPLICAS[@]}
    do 
        echo "=> " $replica
        COMMAND="source /home/${USER}/.sdkman/bin/sdkman-init.sh; cd ${HOME_DIR};"

        for letter in ${LETTERS[@]}
        do
            COMMAND="$COMMAND scala main.scala.client.Client ${RMI_IP} ${replica} read ${letter};"
        done
        
        ssh ${USER}@${INSTANCE_01_IP} ${COMMAND}
    done
    echo "Done! \n"
done
echo "Done! \n"


#########################################################################
#                                                                       #
# Kill the master and replicas                                          #
#                                                                       #
#########################################################################

echo "Stop the master and all the replicas..."
ssh ${USER}@${INSTANCE_01_IP} "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh ${USER}@${INSTANCE_02_IP} "lsof -tc java | xargs --no-run-if-empty kill -9"
ssh ${USER}@${INSTANCE_03_IP} "lsof -tc java | xargs --no-run-if-empty kill -9"
echo "Done! \n"


#########################################################################
#                                                                       #
# Fetch generated logs                                                  #
#                                                                       #
#########################################################################

sleep 10;
echo "Fetching logs..."
echo "=> Remove old logs"
mkdir -p $LOG_DIR

echo "=> Get the new logs"
ssh ${USER}@${INSTANCE_01_IP} "cat ${HOME_DIR}/${LOG_DIR}/master.log" > $LOG_DIR/master.log

# Fetch logs on instance-01"
for replica in ${REPLICAS_INSTANCE_01[@]}
do
    ssh ${USER}@${INSTANCE_01_IP} "cat ${HOME_DIR}/${LOG_DIR}/${replica}.log" > $LOG_DIR/${replica}.log
done

# Fetch logs on instance-02"
for replica in ${REPLICAS_INSTANCE_02[@]}
do
    ssh ${USER}@${INSTANCE_02_IP} "cat ${HOME_DIR}/${LOG_DIR}/${replica}.log" > $LOG_DIR/${replica}.log
done

# Fetch logs on instance-03
for replica in ${REPLICAS_INSTANCE_03[@]}
do
    ssh ${USER}@${INSTANCE_03_IP} "cat ${HOME_DIR}/${LOG_DIR}/${replica}.log" > $LOG_DIR/${replica}.log
done
echo "Done! \n";