#/bin/bash -xe

# Scritp that starts a PC/LW cluster
# with load balancers to be executed on Linux machine.
# Requirements:
#  * Linux
#  * Docker >= 1.12.0 daemon running locally.

../helpers/prepare-docker-machine-helper.sh || true

# Cleanup old deployment
../helpers/delete-pc-cluster.sh || true
../helpers/delete-lw-cluster.sh || true

# Start Lightwave cluster
../helpers/make-lw-cluster.sh

# Start load balancer
../helpers/run-haproxy-container.sh

# Start Photon Cluster
../helpers/make-pc-cluster.sh

# Make Photon users
../helpers/make-users.sh

# Basic sanity test. Need to extend.
../helpers/basic-test-helper.sh
rc=$?

# Clean up
../helpers/delete-all-containers.sh

exit $rc
