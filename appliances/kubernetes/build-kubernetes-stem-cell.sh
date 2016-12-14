#!/bin/bash -e
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
export SOURCE_OVA=${SCRIPT_DIR}/../photon-ova/build/photon-ova-virtualbox.ova
export FORCE_REBUILD_PHOTON=0

if [ ! -n "${KUBERNETES_VERSION}" ]; then
  export KUBERNETES_VERSION=1.5.1
fi

while [ $# -gt 0 ]; do
  case "$1" in
    -r|--rebuild-photon)
      FORCE_REBUILD_PHOTON=1
      ;;
    -d|--debug)
      export PACKER_LOG=1
      ;;
    *)
      break
      ;;
  esac
  shift
done

if [[ ! -f "${SOURCE_OVA}" ]]; then
  echo "Photon OVA does not exist, forcing rebuild"
  FORCE_REBUILD_PHOTON=1
fi

if [[ "${FORCE_REBUILD_PHOTON}" -eq 1 ]]; then
  echo "Building Photon OVA..."
  pushd ${SCRIPT_DIR}/../photon-ova
  ./build.sh
  popd
else
  echo "Photon OVA exists, no need to rebuild"
fi

# Checkout kube-deploy/docker-multinode. This is the script that is
# used to start Kubernetes on the VMs.  We're using a custom version
# that allows us to run etcd on a node other than the master.

rm -rf kube-deploy
git clone https://github.com/vmware/kube-deploy
cd kube-deploy; git checkout pc-1.1; cd ..

# Download the kubectl binary and change permissions
wget https://storage.googleapis.com/kubernetes-release/release/v${KUBERNETES_VERSION}/bin/linux/amd64/kubectl
chmod +x kubectl

# Run the Packer build, but first clean up previous build artifacts
mkdir -p ./build
rm -rf ./build/*
packer build -force kubernetes.json

# Make OVA VMware compatible
cd build
${SCRIPT_DIR}/../scripts/toVMwareOva.sh kubernetes-virtualbox kubernetes
BRANCH=${GERRIT_BRANCH:-`git rev-parse --abbrev-ref HEAD`}
COMMIT=`git rev-parse --short HEAD`
pushd ${SCRIPT_DIR}
PC_VERSION=`cat ../../VERSION`
popd
mv kubernetes.ova kubernetes-${KUBERNETES_VERSION}-pc-${PC_VERSION}-${COMMIT}.ova
