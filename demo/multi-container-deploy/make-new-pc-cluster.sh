#!/bin/bash -xe

export PHOTON_PEER_0=192.168.114.31
export PHOTON_PEER_1=192.168.114.32
export PHOTON_PEER_2=192.168.114.33
export PC_NAME_POSTFIX=new-

./make-pc-cluster.sh
