#!/bin/bash +xe

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
  ./delete-vms.sh
  ./make-vms.sh $@
  ./prepare-docker-machine.sh
fi

eval $(docker-machine env --swarm vm-0)

# Start
./delete-all-containers.sh
./load-images.sh
./make-lw-cluster.sh $@
./run-haproxy-container.sh $@
./make-pc-cluster.sh $@
./make-users.sh

if [ $(docker images -qa  esxcloud/management_ui | wc -l) -eq 1 ]; then
  ./make-ui-cluster.sh
fi

LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)

echo "Deployment completed!

IP address of load balancer: $LOAD_BALANCER_IP

Connect 'photon' CLI to above IP and have fun.

photon target set https://$LOAD_BALANCER_IP:9000 -c
photon target login --username photon@photon.local --password Photon123\$
photon deployment show default

If Photon Controller UI container image was available then URL for UI is: https://$LOAD_BALANCER_IP:4343"
