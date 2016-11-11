#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

export PHOTON_PEER_0=192.168.114.31
export PHOTON_PEER_1=192.168.114.32
export PHOTON_PEER_2=192.168.114.33
export PC_NAME_POSTFIX=new-

./helpers/make-pc-cluster.sh
