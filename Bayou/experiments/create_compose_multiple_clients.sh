#!/bin/sh

BASE_IP="172.22.0."
N_SLAVES=$(($1+0))
I=2 #starting value of ip range (i=2 -> 172.22.0.2, i=50 -> 172.22.0.50)
OUTDEGREE=1 #number of known servers per slave
N_CLIENTS=3 #number of clients to spawn
t="  "

# Write yml to start master server
rm ../docker-compose.yml
touch ../docker-compose.yml
echo "version: '2'" >> ../docker-compose.yml
echo "services:" >> ../docker-compose.yml
echo "${t}bayou-master:" >> ../docker-compose.yml
echo "${t}${t}container_name: master" >> ../docker-compose.yml
echo "${t}${t}image: master:0.1" >> ../docker-compose.yml
echo "${t}${t}restart: on-failure" >> ../docker-compose.yml
echo "${t}${t}ports:" >> ../docker-compose.yml
echo "${t}${t}${t}- 9999" >> ../docker-compose.yml
echo "${t}${t}command: \"[]\"" >> ../docker-compose.yml
echo "${t}${t}networks:" >> ../docker-compose.yml
echo "${t}${t}${t}bayou:" >> ../docker-compose.yml
echo -e "${t}${t}${t}${t}ipv4_address: $BASE_IP$I\n" >> ../docker-compose.yml

for ((i=1; i <= $N_SLAVES; ++i))
do
SUBIP=$(($i + $I))
ID=$(($i))
# Create list of random known server IPs
IP_LIST="\"[$BASE_IP$(($SUBIP-1))"

for ((k=1; k <= $(($OUTDEGREE-1)); ++k))
do
# Add random server ip to list
IP_LIST="$IP_LIST,$BASE_IP$(($I+$(($RANDOM%$(($N_SLAVES+1))))))"
done
IP_LIST="$IP_LIST]\""

# Write yml to start N_SLAVES slave servers
echo "${t}bayou-server$ID:" >> ../docker-compose.yml
echo "${t}${t}container_name: slave$ID" >> ../docker-compose.yml
echo "${t}${t}image: slave:0.1" >> ../docker-compose.yml
echo "${t}${t}restart: on-failure" >> ../docker-compose.yml
echo "${t}${t}ports:" >> ../docker-compose.yml
echo "${t}${t}${t}- 9999" >> ../docker-compose.yml
echo "${t}${t}command: $IP_LIST" >> ../docker-compose.yml
echo "${t}${t}networks:" >> ../docker-compose.yml
echo "${t}${t}${t}bayou:" >> ../docker-compose.yml
echo -e "${t}${t}${t}${t}ipv4_address: $BASE_IP$SUBIP\n" >> ../docker-compose.yml
done

for ((j=1; j <= $N_CLIENTS; ++j))
do
CLIENT_ID=$(($j))
# Select random server IP to communicate with
RANDOM_SERVER_IP="$BASE_IP$(($I+$(($RANDOM%$(($N_SLAVES+1))))))"

# Write yml to start a client with randomly picked IP
echo "${t}bayou-client$CLIENT_ID:" >> ../docker-compose.yml
echo "${t}${t}container_name: client$CLIENT_ID" >> ../docker-compose.yml
echo "${t}${t}image: client:0.1" >> ../docker-compose.yml
echo "${t}${t}restart: unless-stopped" >> ../docker-compose.yml
echo "${t}${t}ports:" >> ../docker-compose.yml
echo "${t}${t}${t}- 9999" >> ../docker-compose.yml
echo "${t}${t}command:" >> ../docker-compose.yml
echo "${t}${t}${t}- \"${RANDOM_SERVER_IP}\"" >> ../docker-compose.yml
echo "${t}${t}${t}- \"ham\"" >> ../docker-compose.yml
echo "${t}${t}${t}- \"kaas\"" >> ../docker-compose.yml
echo "${t}${t}${t}- \"melk\"" >> ../docker-compose.yml
echo "${t}${t}${t}- \"m\"" >> ../docker-compose.yml
echo "${t}${t}networks:" >> ../docker-compose.yml
echo "${t}${t}${t}bayou:" >> ../docker-compose.yml
echo -e "${t}${t}${t}${t}ipv4_address: $BASE_IP$(($SUBIP+$j))\n" >> ../docker-compose.yml
done

# Write yml to create network
echo "networks:" >> ../docker-compose.yml
echo "${t}bayou:" >> ../docker-compose.yml
echo "${t}${t}driver: bridge" >> ../docker-compose.yml
echo "${t}${t}ipam:" >> ../docker-compose.yml
echo "${t}${t}${t}config:" >> ../docker-compose.yml
echo "${t}${t}${t}${t}- subnet: 172.22.0.0/24" >> ../docker-compose.yml
echo "${t}${t}${t}${t}  gateway: 172.22.0.254" >> ../docker-compose.yml
