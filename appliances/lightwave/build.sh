#!/bin/bash -xe

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

GERRIT_BRANCH=${GERRIT_BRANCH:-"develop"}

if [ -z $NO_PHOTON_REBUILD ]; then
  echo "Building Photon OVA"
  pushd ${SCRIPT_DIR}/../photon-ova
  build.sh
  popd
fi

export SOURCE_OVA=${SCRIPT_DIR}/../photon-ova/build/`basename ${SCRIPT_DIR}/../photon-ova/build/photon*.ova`

if [ -d ./build ] ; then
  rm -rf ./build/*
else
  mkdir -p ./build
fi

export PACKER_LOG=1

packer build -force lightwave.json

# make ova vmware compatible
pushd build
${SCRIPT_DIR}/../scripts/toVMwareOva.sh lightwave-vb lightwave ${SCRIPT_DIR}/add-ovf-params.sh
popd
