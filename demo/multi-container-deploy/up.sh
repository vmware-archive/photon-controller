#!/bin/bash +xe

./delete-vms.sh
./make-vms.sh $@
./prepare-docker-machine.sh

eval $(docker-machine env --swarm mhs-demo0)

# Start
./load-images.sh
./make-lw-cluster.sh $@
./run-haproxy-container.sh $@
./make-pc-cluster.sh $@
./make-users.sh

echo "Deployment completed!

Use following command to get the IP address of load balancer.

LOAD_BALANCER_IP=\$(docker inspect --format '{{ .Node.IP }}' haproxy)

and then connect 'photon' CLI to the load balancer and have fun.

photon target set https://\$LOAD_BALANCER_IP:28080 -c
photon target login --username photon@photon.local --password Photon123\$
photon deployment show default"
