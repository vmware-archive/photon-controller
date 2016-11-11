#!/bin/bash -xe

LIGHTWAVE_PASSWORD=$1
LIGHTWAVE_DOMAIN=$2

/opt/vmware/bin/vmdns-cli add-record --zone $LIGHTWAVE_DOMAIN --type A --hostname pc-0 --ip 192.168.114.11 --server localhost --username Administrator --domain $LIGHTWAVE_DOMAIN --password $LIGHTWAVE_PASSWORD
/opt/vmware/bin/vmdns-cli add-record --zone $LIGHTWAVE_DOMAIN --type A --hostname pc-1 --ip 192.168.114.12 --server localhost --username Administrator --domain $LIGHTWAVE_DOMAIN --password $LIGHTWAVE_PASSWORD
/opt/vmware/bin/vmdns-cli add-record --zone $LIGHTWAVE_DOMAIN --type A --hostname pc-2 --ip 192.168.114.13 --server localhost --username Administrator --domain $LIGHTWAVE_DOMAIN --password $LIGHTWAVE_PASSWORD
