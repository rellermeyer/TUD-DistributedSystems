#!/bin/bash

cp src/main/resources/application.kubernetes.conf src/main/resources/application.conf

minikube stop
minikube start
kubectl delete -f k8s/crdt-graph-rbac.yml | echo "rbac already deleted!"
kubectl delete -f k8s/crdt-graph-deployment.yml | echo "deployment already deleted!"
kubectl delete -f k8s/crdt-graph-service.yml | echo "service already deleted!"
eval $(minikube docker-env)
sbt docker:publishLocal
# create serviceAccount and role
kubectl create -f k8s/crdt-graph-rbac.yml
# create deployment
kubectl create -f k8s/crdt-graph-deployment.yml
# create service
kubectl create -f k8s/crdt-graph-service.yml

# tunnel the load balancer
minikube tunnel &
sleep 2
#
echo "Waiting 10 seconds to get the pods to start"
sleep 10
kubectl get pods

echo "Put in the pod names for port forwarding"
echo "Port 7000:"
read POD1
kubectl port-forward $POD1 7000:8080 &
sleep 2

echo "Port 7001:"
read POD2
kubectl port-forward $POD2 7001:8080 &
sleep 2

echo "Port 7002:"
read POD3
kubectl port-forward $POD3 7002:8080 &
sleep 2

minikube dashboard &
