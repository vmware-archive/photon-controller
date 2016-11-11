#!/bin/bash -xe

if [ $(uname) == "Darwin" ]; then
  wget -nv -O ./photon https://github.com/vmware/photon-controller/releases/download/v1.1.0/photon-darwin64-1.1.0-5de1cb7
else
  wget -nv -O ./photon https://github.com/vmware/photon-controller/releases/download/v1.1.0/photon-linux64-1.1.0-5de1cb7
fi

chmod +x photon

if [ $(uname) == "Darwin" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LOAD_BALANCER_IP=$(docker-machine ip vm-0) || true
else
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LOAD_BALANCER_IP=$(ip route get 8.8.8.8 | awk 'NR==1 {print $NF}')
fi

./photon target set https://${LOAD_BALANCER_IP}:9000 -c
./photon target login --username photon@photon.local --password Photon123$
deployment_ready=$(./photon deployment show | grep READY | wc -l)

if [ $deployment_ready -ne 1 ]; then
  echo "ERROR: Deployment not ready"
  exit 1
fi
