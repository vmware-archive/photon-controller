#!/bin/bash -xe

HOST_IP=$1
LOAD_BALANCER_IP=$2
LOAD_BALANCER_API_PORT=$3
LOAD_BALANCER_HTTPS_PORT=$4


MGMT_UI_LOGIN_URL=$(docker exec -it photon-controller-0 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.11:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLoginEndpoint | cut -f4 -d '"')
MGMT_UI_LOGOUT_URL=$(docker exec -it photon-controller-0 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.11:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLogoutEndpoint | cut -f4 -d '"')

docker run -d \
    --name photon-ui \
    --net=lightwave \
    --ip=$HOST_IP \
    -e API_ORIGIN=$LOAD_BALANCER_IP \
    -e HTTPS_PORT=$LOAD_BALANCER_HTTPS_PORT \
    -e LOGINREDIRECTENDPOINT=$MGMT_UI_LOGIN_URL \
    -e LOGOUTREDIRECTENDPOINT=$MGMT_UI_LOGOUT_URL \
    esxcloud/management_ui
