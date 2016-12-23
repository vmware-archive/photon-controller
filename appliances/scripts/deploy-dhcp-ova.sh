#!/bin/bash

if [ $# -lt 2 ]; then
    echo "Usage: deploy-dhcp-ova <ESXi hostname/IP> <OVA Path>"
    exit 1
fi

ESX_HOSTNAME=$1
OVA_PATH=$2

VM_NAME=dhcp-1
DATASTORE_NAME=datastore1
NETWORK_NAME='VM Network'

OVERWRITE_VM=--overwrite

VM_IP_0=192.168.2.1
VM_NETMASK_0=255.255.255.0
VM_GATEWAY_0=192.168.2.253

VM_IP_1=10.118.102.12
VM_NETMASK_1=255.255.252.0
VM_GATEWAY_1=10.118.102.253

VM_ROOT_PASSWORD='Ca$hc0w1'

ovftool \
    --name=$VM_NAME \
    --X:apiVersion=5.5 \
    --X:waitForIp \
    --acceptAllEulas \
    --noSSLVerify \
    --skipManifestCheck \
    $OVERWRITE_VM \
    --powerOffTarget \
    --powerOn \
    --datastore=$DATASTORE_NAME \
    --net:NAT="$NETWORK_NAME" \
    --X:injectOvfEnv \
    --prop:ip0=$VM_IP_0 \
    --prop:netmask0=$VM_NETMASK_0 \
    --prop:gateway0=$VM_GATEWAY_0 \
    --prop:ip1=$VM_IP_1 \
    --prop:netmask1=$VM_NETMASK_1 \
    --prop:gateway1=$VM_GATEWAY_1 \
    --prop:root_password=$VM_ROOT_PASSWORD \
    $OVA_PATH \
    vi://root@$ESX_HOSTNAME/

