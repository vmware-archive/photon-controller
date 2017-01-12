This directory contains prototype scripts for launching a pod that will run
in kubernetes node, and will pull secret username/password from
Kubernetes API server and would populate the photon config file that will
be used by Photon Cloud Provider plugin to communitate with Photon Controller.

## Build
docker build -t vmware/photon-kube-conf:latest .

## Publish container image
docker push vmware/photon-kube-conf:latest

## Create Pod
kubectl create -f photon-conf.yml
