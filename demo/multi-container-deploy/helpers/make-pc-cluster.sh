#!/bin/bash +xe

MULTI_CONTAINER=$1
LIGHTWAVE_PASSWORD=$2
LIGHTWAVE_DOMAIN=$3
ENABLE_FQDN=$4

LIGHTWAVE_PARTNER_0=${LIGHTWAVE_PARTNER_0:-192.168.114.2}

if [ $(uname) == "Darwin" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LOAD_BALANCER_IP=$(docker-machine ip vm-0) || true
else
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LOAD_BALANCER_IP=$(ip route get 8.8.8.8 | awk 'NR==1 {print $NF}')
fi

# Get Load balancer IP from swarm multi-host setup if available.
docker inspect --format '{{ .Node.IP }}' haproxy
if [ $? == 0]; then
  LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)
fi

PHOTON_PEER_IP0=192.168.114.11
PHOTON_PEER_IP1=192.168.114.12
PHOTON_PEER_IP2=192.168.114.13

if [ $ENABLE_FQDN == "1" ]; then
  PHOTON_PEER_0=pc-0.${LIGHTWAVE_DOMAIN}
  PHOTON_PEER_1=pc-1.${LIGHTWAVE_DOMAIN}
  PHOTON_PEER_2=pc-2.${LIGHTWAVE_DOMAIN}
else
  PHOTON_PEER_0=${PHOTON_PEER_IP0}
  PHOTON_PEER_1=${PHOTON_PEER_IP1}
  PHOTON_PEER_2=${PHOTON_PEER_IP2}
fi

./helpers/run-pc-container.sh $PHOTON_PEER_IP0 $PHOTON_PEER_0 $PHOTON_PEER_1 $PHOTON_PEER_2 $LIGHTWAVE_PARTNER_0 $LOAD_BALANCER_IP ${PC_NAME_POSTFIX}1 $MULTI_CONTAINER $LIGHTWAVE_PASSWORD $LIGHTWAVE_DOMAIN $ENABLE_FQDN

if [ "$MULTI_CONTAINER" == "1" ]; then
  ./helpers/run-pc-container.sh $PHOTON_PEER_IP1 $PHOTON_PEER_1 $PHOTON_PEER_0 $PHOTON_PEER_2 $LIGHTWAVE_PARTNER_0 $LOAD_BALANCER_IP ${PC_NAME_POSTFIX}2 $MULTI_CONTAINER $LIGHTWAVE_PASSWORD $LIGHTWAVE_DOMAIN $ENABLE_FQDN
  ./helpers/run-pc-container.sh $PHOTON_PEER_IP2 $PHOTON_PEER_2 $PHOTON_PEER_0 $PHOTON_PEER_1 $LIGHTWAVE_PARTNER_0 $LOAD_BALANCER_IP ${PC_NAME_POSTFIX}3 $MULTI_CONTAINER $LIGHTWAVE_PASSWORD $LIGHTWAVE_DOMAIN $ENABLE_FQDN
fi
