#!/usr/bin/env bash

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
minikube tunnel
#KUBE_IP=$(minikube ip)
#MANAGEMENT_PORT=$(kubectl get svc akka-simple-cluster -ojsonpath="{.spec.ports[?(@.name==\"management\")].nodePort}")
#curl http://$KUBE_IP:$MANAGEMENT_PORT/cluster/members | jq
