#!/bin/bash +xe

# Cleanup old run
docker rmi vmware/photon-controller || true
docker rmi vmware/photon-controller-lightwave-client || true
./delete-pc-cluster.sh
./delete-lw-cluster.sh

# Start
./load-images.sh
./make-lw-cluster.sh
./make-users.sh
./run-haproxy-container.sh
./make-pc-cluster.sh
./make-ui-cluster.sh
