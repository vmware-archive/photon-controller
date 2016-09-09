#!/bin/sh -xe

PHOTON_USER_PASSWORD="P@ssword!"
LIGHTWAVE_PASSWORD="Admin!23"
LIGHTWAVE_MASTER_IP=192.168.114.2

PC_PEER0_IP=192.168.114.11
PC_PEER1_IP=192.168.114.12
PC_PEER2_IP=192.168.114.13

docker-machine ls -q | xargs -I {} docker-machine scp ./run-pc-container.sh {}:/tmp/

docker-machine ssh mhs-demo0 /tmp/run-pc-container.sh $PC_PEER0_IP $PC_PEER1_IP $PC_PEER2_IP $LIGHTWAVE_MASTER_IP mhs-demo0
docker-machine ssh mhs-demo1 /tmp/run-pc-container.sh $PC_PEER1_IP $PC_PEER0_IP $PC_PEER2_IP $LIGHTWAVE_MASTER_IP mhs-demo1
docker-machine ssh mhs-demo2 /tmp/run-pc-container.sh $PC_PEER2_IP $PC_PEER0_IP $PC_PEER1_IP $LIGHTWAVE_MASTER_IP mhs-demo2
