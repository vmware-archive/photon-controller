#!/bin/sh

PHOTON_CONTROLLER_HOST_IP=192.168.114.12
LIGHTWAVE_HOST_IP=192.168.114.2

docker cp ./make-deployment-helper.sh photon-controller-mhs-demo0:/
docker exec -t photon-controller-mhs-demo0 /make-deployment-helper.sh $PHOTON_CONTROLLER_HOST_IP $LIGHTWAVE_HOST_IP
