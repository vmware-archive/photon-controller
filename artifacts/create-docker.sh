#!/bin/bash
set -x -e

# Create fresh RPM before creating docker container
./create-rpm.sh

export BRANCH=${GERRIT_BRANCH:-`git rev-parse --abbrev-ref HEAD`}
export VERSION=${BRANCH}

rm -rf build/docker/photon-controller-docker*

packer build ./packer/docker.json

# Remove old docker image with same name
docker rmi vmware/photon-controller || true

# Build new container
docker build . -t vmware/photon-controller
