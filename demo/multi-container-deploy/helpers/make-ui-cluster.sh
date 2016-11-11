#!/bin/bash -xe

UI_CONTAINER_VERSION=$1
UI_HOST_IP=192.168.114.15

if [ $(uname) == "Darwin" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LOAD_BALANCER_IP=$(docker-machine ip vm-0) || true
else
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LOAD_BALANCER_IP=$(ip route get 8.8.8.8 | awk 'NR==1 {print $NF}')
fi

./helpers/run-ui-container.sh $UI_HOST_IP https://$LOAD_BALANCER_IP:9000 9000 4343 $UI_CONTAINER_VERSION
