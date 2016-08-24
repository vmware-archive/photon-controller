#!/bin/bash -xe
# Copyright 2015 VMware, Inc. All Rights Reserved.
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
JQ_URL=${JQ_URL:-"https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64"}

GERRIT_BRANCH=${GERRIT_BRANCH:-"develop"}

if [ -z "$NO_PHOTON_REBUILD" ]; then
  echo "Building Photon OVA"
  pushd ${SCRIPT_DIR}/../photon-ova
  ./build.sh
  popd
fi

if [ -z "$NO_PHOTON_RPM_REBUILD" ]; then
  echo "Building Photon Controller RPM"
  rm -rf ${SCRIPT_DIR}/photon-controller*.rpm
  pushd ${SCRIPT_DIR}/../../java
  ./gradlew :rpm
  cp ${SCRIPT_DIR}/../../artifacts/build/RPMS/x86_64/photon-controller-*.rpm ${SCRIPT_DIR}
  rm -rf photon-controller-debuginfo*.rpm
  popd
fi

rm -rf ${SCRIPT_DIR}/photon/config-templates
mkdir -p ${SCRIPT_DIR}/photon/config-templates
cp ${SCRIPT_DIR}/../../java/photon-controller-core/src/dist/configuration/photon-controller-core.yml ${SCRIPT_DIR}/photon/config-templates
cp ${SCRIPT_DIR}/../../java/photon-controller-core/src/dist/configuration/photon-controller-core_release.json ${SCRIPT_DIR}/photon/config-templates
cp ${SCRIPT_DIR}/../../java/photon-controller-core/src/dist/configuration/run.sh ${SCRIPT_DIR}/photon/config-templates
cp ${SCRIPT_DIR}/../../java/photon-controller-core/src/dist/configuration/swagger-config.js ${SCRIPT_DIR}/photon/config-templates
cp ${SCRIPT_DIR}/../../java/photon-controller-core/src/dist/configuration/photon-controller-core_example.json ${SCRIPT_DIR}/photon/config-templates

export SOURCE_OVA=${SCRIPT_DIR}/../photon-ova/build/`basename ${SCRIPT_DIR}/../photon-ova/build/photon*.ova`

if [ -d ./build ] ; then
  rm -rf ./build/*
else
  mkdir -p ./build
fi

export PACKER_LOG=1

photon_rpm=`basename ${SCRIPT_DIR}/photon-controller-*.x86_64.rpm`

packer build -force \
  -var "photon_rpm=$photon_rpm" \
  -var "jq_url=$JQ_URL" \
  photon-controller-packer.json

# make ova vmware compatible
pushd build
${SCRIPT_DIR}/../scripts/toVMwareOva.sh photon-controller-vb photon-controller ${SCRIPT_DIR}/add-ovf-params.sh
popd
