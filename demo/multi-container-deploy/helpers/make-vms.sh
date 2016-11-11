#!/bin/bash -xe

MULTI_HOST=$1
MULTI_CONTAINER=$2

VM_DRIVER=${3:-vmwarefusion}
MEMORY=2048

if [ $MULTI_HOST -eq 0 -a $MULTI_CONTAINER -eq 1 ]; then
  MEMORY=4096
fi

if [ "$VM_DRIVER" == "vmwarefusion" ]; then
  IF=eth0
  MEMORY_OPT="--vmwarefusion-memory-size=$MEMORY"
else
  # Assume VirtualBox is available. IF should be eth1 for VirtualBox
  IF=eth1
  MEMORY_OPT="--virtualbox-memory=$MEMORY"
fi



if [ "$MULTI_HOST" == "1" ]; then

  docker-machine create -d $VM_DRIVER mh-keystore
  eval "$(docker-machine env mh-keystore)"

  docker run -d \
      -p "8500:8500" \
      -h "consul" \
      progrium/consul -server -bootstrap

  docker-machine create \
      -d $VM_DRIVER \
      --swarm --swarm-master \
      --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-advertise=$IF:2376" \
      $MEMORY_OPT \
      vm-0

  docker-machine create \
      -d $VM_DRIVER \
      --swarm \
      --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-advertise=$IF:2376" \
      $MEMORY_OPT \
      vm-1

  docker-machine create \
      -d $VM_DRIVER \
      --swarm \
      --swarm-discovery="consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-store=consul://$(docker-machine ip mh-keystore):8500" \
      --engine-opt="cluster-advertise=$IF:2376" \
      $MEMORY_OPT \
      vm-2

  eval $(docker-machine env --swarm vm-0)

else
  if [ "$(uname)" == Darwin ]; then
    docker-machine create -d $VM_DRIVER $MEMORY_OPT vm-0
  fi
fi
