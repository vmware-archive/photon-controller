#!/bin/bash -xe

LIGHTWAVE_PARTNER_0=${LIGHTWAVE_PARTNER_0:-192.168.114.2}

# Get Load balancer IP from  swarm multi-host setup.
LIGHTWAVE_LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy) || true

if [ "${LIGHTWAVE_LOAD_BALANCER_IP}TEST" ==  "TEST" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LIGHTWAVE_LOAD_BALANCER_IP=$(docker-machine ip default) || true
fi

if [ "${LIGHTWAVE_LOAD_BALANCER_IP}TEST" ==  "TEST" ]; then
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LIGHTWAVE_LOAD_BALANCER_IP=127.0.0.1
fi

PHOTON_PEER_0=${PHOTON_PEER_0:-192.168.114.11}
PHOTON_PEER_1=${PHOTON_PEER_1:-192.168.114.12}
PHOTON_PEER_2=${PHOTON_PEER_2:-192.168.114.13}

./helpers/run-pc-container.sh $PHOTON_PEER_0 $PHOTON_PEER_1 $PHOTON_PEER_2 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_LOAD_BALANCER_IP ${PC_NAME_POSTFIX}0 $1

if [ "$1" == "multi" ]; then
  ./helpers/run-pc-container.sh $PHOTON_PEER_1 $PHOTON_PEER_0 $PHOTON_PEER_2 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_LOAD_BALANCER_IP ${PC_NAME_POSTFIX}1 $1
  ./helpers/run-pc-container.sh $PHOTON_PEER_2 $PHOTON_PEER_0 $PHOTON_PEER_1 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_LOAD_BALANCER_IP ${PC_NAME_POSTFIX}2 $1
fi
