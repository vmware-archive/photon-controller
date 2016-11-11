#!/bin/bash -xe

VM_DRIVER=${VM_DRIVER:-vmwarefusion}

if [ "$VM_DRIVER" == "vmwarefusion" ]; then
  IF=eth0
  MEMORY_OPT="--vmwarefusion-memory-size=4096"
else
  # Assume VirtualBox is available. IF should be eth1 for VirtualBox
  IF=eth1
  MEMORY_OPT="--virtualbox-memory=4096"
fi


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

if [ "$1" == "multi" ]; then

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

fi

eval $(docker-machine env --swarm vm-0)
