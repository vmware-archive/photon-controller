#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

HOST_IP=$1
PC_CONTAINER_URL=$2
LOAD_BALANCER_API_PORT=$3
LOAD_BALANCER_HTTPS_PORT=$4
UI_CONTAINER_VERSION=$5

MGMT_UI_LOGIN_URL=$(docker exec -t photon-controller-1 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.11:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLoginEndpoint | cut -f4 -d '"')
MGMT_UI_LOGOUT_URL=$(docker exec -t photon-controller-1 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.11:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLogoutEndpoint | cut -f4 -d '"')
MGMT_UI_LOGIN_URL=$(echo $MGMT_UI_LOGIN_URL | sed -e 's/\(correlation_id\)[^&]*\&//g' |  sed -e 's/\(state\=\)[^&]*\&//g' |  sed -e 's/\(nonce\=\)[^&]*\&//g')
MGMT_UI_LOGOUT_URL=$(echo $MGMT_UI_LOGIN_URL | sed -e 's/\(correlation_id\)[^&]*\&//g' |  sed -e 's/\(state\=\)[^&]*\&//g' |  sed -e 's/\(nonce\=\)[^&]*\&//g')

echo "Creating Photon Controller UI container..."
docker run -d \
    --name photon-controller-ui \
    --net=lightwave \
    --ip=$HOST_IP \
    -e API_ORIGIN=$PC_CONTAINER_URL \
    -e HTTPS_PORT=$LOAD_BALANCER_HTTPS_PORT \
    -e LOGINREDIRECTENDPOINT=$MGMT_UI_LOGIN_URL \
    -e LOGOUTREDIRECTENDPOINT=$MGMT_UI_LOGOUT_URL \
    vmware/photon-controller-ui:$UI_CONTAINER_VERSION
