#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

MULTI_HOST=$1
LIGHTWAVE_PASSWORD=$2
LW_CONTAINER_VERSION=$3
PC_CONTAINER_VERSION=$4

LIGHTWAVE_PARTNER_0=${LIGHTWAVE_PARTNER_0:-192.168.114.2}
LIGHTWAVE_PARTNER_1=${LIGHTWAVE_PARTNER_1:-192.168.114.3}
LIGHTWAVE_PARTNER_2=${LIGHTWAVE_PARTNER_2:-192.168.114.4}

HOSTNAME=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "cat /host-etc/hostname")
OS_NAME=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "cat /host-etc/os-release | grep '^NAME=' | sed -e 's/NAME=//g' | tr -d '[:space:]'")
HYPERVISOR=$(docker run --rm --net=host -t -v /etc:/host-etc vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/bash -c "dmidecode -s system-product-name | tr -d '[:space:]'")

if [ "$OS_NAME" == "Boot2Docker" -o "$HOSTNAME" == "moby" ]; then
  # docker-machine VM or Docker For Mac VM needs to be prepared to get systemd working inside container.
  # This preparation script runs from inside the container but effects few things on the VM it is running on
  # by mounting / inside. That script creates some mount points which are required for systemd. Insanity will prevail!
  docker run --rm --net=host -t \
       -v /var/run/docker.sock:/var/run/docker.sock -v /:/tmp/root --cap-add=SYS_ADMIN --security-opt=seccomp:unconfined \
       vmware/photon-controller-seed:1.0.3 ./helpers/prepare-docker-machine-helper.sh /tmp/root/ > /dev/null 2>&1
fi

echo "Creating Lightwave configuration container..."
# Create data container for sharing config data between containers.
docker create \
  -v /etc/photon-controller \
  -v /var/lib/vmware/config \
  -v /etc/ssl/private \
  -v /usr/local/etc/haproxy \
  --name photon-config-data \
   vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/true > /dev/null 2>&1

echo "Creating Network..."
# Try creating overlay network in case there is key/value store setup for docker swarm
docker network create --driver overlay --subnet=192.168.114.0/24 lightwave > /dev/null 2>&1 || true

if [ $(docker network ls | grep lightwave | wc -l) -ne 1 ]; then
  # Now try creating network with bridge networking if previous command failed because swarm was not setup.
  docker network create  --subnet=192.168.114.0/24 lightwave > /dev/null 2>&1 || true

  if [ $(docker network ls | grep lightwave | wc -l) -ne 1 ]; then
    echo "ERROR: Failed to create a network. Exiting!"
    exit 1
  fi
fi

exit 0
