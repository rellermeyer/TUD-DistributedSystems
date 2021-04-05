# modify the script to your configuration before running it
pkill kubectl -9

kubectl port-forward mygraph-neo4j-core-5 7000:7474 &
kubectl port-forward mygraph-neo4j-core-1 7001:7474 &

kubectl port-forward mygraph-neo4j-core-5 7003:7687 &
kubectl port-forward mygraph-neo4j-core-1 7004:7687 &
