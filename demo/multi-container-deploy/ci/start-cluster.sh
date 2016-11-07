#/bin/bash -xe

# Scritp that starts a PC/LW cluster
# with load balancers to be executed on Linux machine.
# Requirements:
#  * Linux
#  * Docker >= 1.12.0 daemon running locally.

./prepare-docker-machine-helper.sh || true

# Cleanup old deployment
./delete-pc-cluster.sh || true
./delete-lw-cluster.sh || true

# Start Lightwave cluster
./make-lw-cluster.sh

# Start load balancer
./run-haproxy-container.sh

# Start Photon Cluster
./make-pc-cluster.sh

# Make Photon users
./make-users.sh

# Basic sanity test. Need to extend.
./basic-test-helper.sh
rc=$?

# Clean up
./delete-pc-cluster.sh
./delete-lw-cluster.sh

exit $rc
