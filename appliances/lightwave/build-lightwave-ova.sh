#!/bin/bash -xe
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

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

packer build -force lightwave-packer.json

# make ova vmware compatible
pushd build
${SCRIPT_DIR}/../scripts/toVMwareOva.sh lightwave-vb lightwave ${SCRIPT_DIR}/add-ovf-params.sh
popd
