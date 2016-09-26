#!/bin/sh -xe

if [ "$(find . -iname photon-controller-*.tar | wc -l)" -ne "1" ]; then
  echo "ERROR: Found more than 1 photon-controller docker tar files. Expected 1"
  exit 1
fi

find . -iname photon-controller-*.tar | xargs docker load -i
docker load < ./vmware-lightwave-sts.tar
docker tag vmware/photon-controller vmware/photon-controller-lightwave-client || true
