#!/bin/bash -xe

wget -nv https://github.com/vmware/photon-controller/releases/download/v1.0.0/photon-linux64
mv photon-linux64 /usr/bin/photon
chmod +x /usr/bin/photon
photon target set https://192.168.114.11:9000 -c
photon target login --username photon@photon.local --password Photon123$
deployment_ready=$(photon deployment show | grep READY | wc -l)

if [ "$deployment_ready" != "1" ]; then
  echo "ERROR: Deployment not ready"
  exit 1
fi
