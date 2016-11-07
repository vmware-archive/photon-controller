#!/bin/bash -xe

if [ "$(find . -iname photon-controller-*.tar | wc -l)" -ne "1" ]; then
  echo "ERROR: Found more or less than 1 photon-controller docker tar files. Expected 1"
  exit 1
fi

if [ "$(find . -iname esxcloud-management-ui.tar | wc -l)" -eq "1" ]; then
  docker load < esxcloud-management-ui.tar
fi

find . -iname photon-controller-*.tar | xargs docker load -i
docker tag vmware/photon-controller vmware/photon-controller-lightwave-client || true
docker load < ./vmware-lightwave-sts.tar
