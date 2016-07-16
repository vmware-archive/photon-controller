#!/bin/bash
set -x -e

# Create fresh RPM before creating docker container
SCRIPT_DIR="$(dirname "$0")"
"${SCRIPT_DIR}"/create-rpm.sh

export BRANCH=${GERRIT_BRANCH:-`git rev-parse --abbrev-ref HEAD`}
export VERSION=${BRANCH}

rm -rf "${SCRIPT_DIR}"/build/docker/photon-controller-docker*

packer build "${SCRIPT_DIR}"/packer/docker.json

# Remove old docker image with same name
docker rmi vmware/photon-controller || true

# Build new container
docker build "${SCRIPT_DIR}" -t vmware/photon-controller
