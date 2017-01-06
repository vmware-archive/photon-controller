#!/bin/bash -xe

if [ $# -lt 2 ]; then
    echo "Usage: deploy-haproxy-ova.sh <ESXi hostname/IP> <OVA Path>"
    exit 1
fi

ESX_HOSTNAME=$1
OVA_PATH=$2

VM_NAME=haproxy
DATASTORE_NAME=datastore1
NETWORK_NAME='VM Network'

OVERWRITE_VM=--overwrite

VM_IP_0=10.118.102.15
VM_NETMASK_0=255.255.255.0
VM_GATEWAY_0=10.118.102.253

VM_ROOT_PASSWORD='changeme'

VM_DNS=10.118.98.1,10.118.98.2
PC_HOSTS=10.118.102.246
PC_PASSWORD='Ca$hc0w1'

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
    --prop:gateway=$VM_GATEWAY_0 \
    --prop:DNS="$VM_DNS"\
    --prop:root_password=$VM_ROOT_PASSWORD \
    --prop:photon_password=$PC_PASSWORD \
    --prop:lb_hosts=$PC_HOSTS \
    $OVA_PATH \
    vi://root@$ESX_HOSTNAME/
