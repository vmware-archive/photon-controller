#!/bin/bash +xe

MULTI_HOST=$1
LIGHTWAVE_PASSWORD=$2
LW_CONTAINER_VERSION=$3
PC_CONTAINER_VERSION=$4

LIGHTWAVE_PARTNER_0=${LIGHTWAVE_PARTNER_0:-192.168.114.2}
LIGHTWAVE_PARTNER_1=${LIGHTWAVE_PARTNER_1:-192.168.114.3}
LIGHTWAVE_PARTNER_2=${LIGHTWAVE_PARTNER_2:-192.168.114.4}

(set -x;
# Create data container for sharing config data between containers.
docker create \
  -v /etc/photon-controller \
  -v /var/lib/vmware/config \
  -v /etc/ssl/private \
  -v /usr/local/etc/haproxy \
  --name photon-config-data \
   vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/true

# Create network
# Try creating overlay network in case there is key/value store setup for docker swarm
docker network create --driver overlay --subnet=192.168.114.0/24 lightwave > /dev/null 2>&1 || true)

if [ $(docker network ls | grep lightwave | wc -l) -ne 1 ]; then
  echo "INFO: Could not create an overlay network, trying to create bridged network now."

  (set -x;
  # Now try creating network with bridge networking if previous command failed because swarm was not setup.
  docker network create  --subnet=192.168.114.0/24 lightwave || true)

  if [ $(docker network ls | grep lightwave | wc -l) -ne 1 ]; then
    echo "ERROR: Failed to create a network. Exiting!"
    exit 1
  fi
fi

./helpers/run-lw-container.sh $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_PASSWORD standalone 1 $LW_CONTAINER_VERSION

if [ "$MULTI_HOST" == "1" ]; then
  ./helpers/run-lw-container.sh $LIGHTWAVE_PARTNER_1 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_PASSWORD partner 2 $LW_CONTAINER_VERSION
  ./helpers/run-lw-container.sh $LIGHTWAVE_PARTNER_2 $LIGHTWAVE_PARTNER_0 $LIGHTWAVE_PASSWORD partner 3 $LW_CONTAINER_VERSION
fi

exit 0
