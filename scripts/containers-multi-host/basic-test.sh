#!/bin/bash -e
set -e
PHOTON_CONTROLLER_HOST_IP=192.168.114.12
LIGHTWAVE_HOST_IP=192.168.114.2

docker cp ./basic-test-helper.sh photon-controller-0:/
docker exec -t photon-controller-0 /basic-test-helper.sh
