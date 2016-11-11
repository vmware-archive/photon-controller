#!/bin/bash +xe

if [ $(hash docker; echo $?) -ne 0 ]; then
  echo "ERROR: docker is not installed. Please install docker and docker-machine first before running this script."
  exit 1
fi

if [ $(hash docker-machine; echo $?) -ne 0 ]; then
  echo "ERROR: docker-machine is not installed. Please install docker-machine before running this script."
  exit 1
fi

if [ $(hash photon; echo $?) -ne 0 ]; then
  echo "ERROR: photon CLI is not installed. Please install Photon Controller (photon) CLI first before running this script."
  exit 1
fi

DESIRED_DOCKER_VERSION=1.12
DOCKER_VERSION=$(docker version --format '{{.Client.Version}}' | cut -f1 -d '-' | cut -f1-2 -d '.')
if [ $(echo $DOCKER_VERSION'>='$DESIRED_DOCKER_VERSION | bc -l) -eq 0 ]; then
  echo "ERROR: Docker version should be >= $DESIRED_DOCKER_VERSION. Exiting!"
  exit 1
fi

if [ "$(find . -iname photon-controller-*.tar | wc -l)" -ne "1" ]; then
  echo "ERROR: Found more or less than 1 Photon Controller docker image tar files. Expected 1"
  exit 1
fi

CREATE_VMS=false
if [ "$1" == "multi" ]; then
  if [ $(docker-machine ls -q --filter=name=vm- | wc -l) -ne 3 ]; then
    CREATE_VMS=true
  fi
else
  if [ $(docker-machine ls -q --filter=name=vm- | wc -l) -lt 1 ]; then
    CREATE_VMS=true
 fi
fi

if [ "$CREATE_VMS" == "true" ]; then
  ./helpers/delete-vms.sh
  ./helpers/make-vms.sh $@
  ./helpers/prepare-docker-machine.sh
fi

eval $(docker-machine env --swarm vm-0)

# Start
./helpers/delete-all-containers.sh
./helpers/load-images.sh

rc=./helpers/make-lw-cluster.sh $@
if [ "$rc" != 0 ]; then
  echo "Could not make Lightwave cluster, please fix the errors and try again."
  exit 1
fi

rc=./helpers/run-haproxy-container.sh $@
if [ "$rc" != 0 ]; then
  echo "Could not run haproxy container, please fix the errors and try again."
  exit 1
fi

rc=./helpers/make-pc-cluster.sh $@
if [ "$rc" != 0 ]; then
  echo "Could not make Photon Controller , please fix the errors and try again."
  exit 1
fi

rc=./helpers/make-users.sh
if [ "$rc" != 0 ]; then
  echo "Could not make Lightwave users , please fix the errors and try again."
  exit 1
fi

if [ $(docker images -qa  esxcloud/management_ui | wc -l) -eq 1 ]; then
  ./helpers/make-ui-cluster.sh
else
  echo "Did not find UI container -- no UI will be available"
fi

LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)

echo "Deployment completed!

IP address of load balancer: $LOAD_BALANCER_IP

Connect 'photon' CLI to above IP and have fun.

photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username photon@photon.local --password Photon123\$
photon deployment show default

If Photon Controller UI container image was available then URL for UI is: https://$LOAD_BALANCER_IP:4343"
