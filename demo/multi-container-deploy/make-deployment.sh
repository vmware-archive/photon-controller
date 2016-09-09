#!/bin/sh -xe

PHOTON_CONTROLLER_HOST_IP=${PHOTON_CONTROLLER_HOST_IP:-192.168.114.12}
LIGHTWAVE_HOST_IP=${LIGHTWAVE_HOST_IP:-192.168.114.2}

docker cp ./make-deployment-helper.sh photon-controller-0:/
docker exec -t photon-controller-0 /make-deployment-helper.sh $PHOTON_CONTROLLER_HOST_IP $LIGHTWAVE_HOST_IP
