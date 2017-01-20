Demo purpose only! Do not use in production.

This directory contains demo scripts for launching a pod that will run
in kubernetes node, and will pull secret username/password from
Kubernetes API server and would populate the photon config file that will
be used by Photon Cloud Provider plugin to communitate with Photon Controller.

## TODO
 1. Make this pod only run on Master nodes.
 2. Remove polling and move to watching the API change.

## Build
docker build -t vmware/photon-kube-conf:latest .

## Publish container image
docker push vmware/photon-kube-conf:latest

## Create Pod
kubectl create -f photon-conf.yml
