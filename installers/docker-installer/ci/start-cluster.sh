#/bin/bash -xe

# Scritp that starts a PC/LW cluster
# with load balancers to be executed on Linux machine.
# Requirements:
#  * Linux
#  * Docker >= 1.12.0 daemon running locally.

if [ $(uname) == "Darwin" ]; then
  ./helpers/prepare-docker-machine.sh || true
else
  ./helpers/prepare-docker-machine-helper.sh || true
fi

docker build -t vmware/photon-controller-seed .

docker run --rm --net=host -t \
       -v /var/run/docker.sock:/var/run/docker.sock \
       vmware/photon-controller-seed \
       start-pc.sh -p 'Photon123$' -D

# Basic sanity test. Need to extend.
./helpers/basic-test-helper.sh
rc=$?

# Clean up
./helpers/delete-all-containers.sh

exit $rc
