#!/bin/bash

if [ $# -lt 2 ]; then
    echo "Usage: deploy-lightwave-ova <ESXi hostname/IP> <OVA Path>"
    exit 1
fi

ESX_HOSTNAME=$1
OVA_PATH=$2

VM_NAME=lightwave-1
DATASTORE_NAME=datastore1
NETWORK_NAME='VM Network'

OVERWRITE_VM=--overwrite

VM_IP_0=10.118.102.12
VM_NETMASK_0=255.255.252.0
VM_GATEWAY_0=10.118.102.253
VM_DNS=10.118.98.1,10.118.98.2
VM_NTP=10.118.98.1,10.118.98.2

LW_DOMAIN=photon.local
LW_PASSWORD='Admin!23'

# Note: Ensure LW_HOSTNAME is fully qualified using LW_DOMAIN above
LW_HOSTNAME=lightwave-1.photon.local

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
    --prop:gateway=$VM_GATEWAY_0 \
    --prop:DNS=$VM_DNS \
    --prop:ntp_servers=$VM_NTP \
    --prop:root_password=$VM_ROOT_PASSWORD \
    --prop:lw_domain=$LW_DOMAIN \
    --prop:lw_password=$LW_PASSWORD \
    --prop:lw_hostname=$LW_HOSTNAME \
    $OVA_PATH \
    vi://root@$ESX_HOSTNAME/

