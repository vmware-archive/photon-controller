#!/bin/bash -xe

HOST_IP=$1
LOAD_BALANCER_IP=$2
LOAD_BALANCER_API_PORT=$3
LOAD_BALANCER_HTTPS_PORT=$4


MGMT_UI_LOGIN_URL=$(docker exec -it photon-controller-1 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.12:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLoginEndpoint | cut -f4 -d '"')
MGMT_UI_LOGOUT_URL=$(docker exec -it photon-controller-1 curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --capath /etc/ssl/certs https://192.168.114.12:19000/photon/cloudstore/deployments/default | json_pp | grep oAuthMgmtUiLogoutEndpoint | cut -f4 -d '"')

rm -rf "($PWD)/ui_tmp*"
UI_TMP_DIR=$(mktemp -d "$PWD/ui_tmp.XXXXX")
trap "rm -rf ${UI_TMP_DIR}" EXIT

UI_CONFIG_DIR=${UI_TMP_DIR}/config
LOG_DIRECTORY=${UI_TMP_DIR}/log/ui

mkdir -p $UI_CONFIG_DIR
mkdir -p $LOG_DIRECTORY

cat << EOF > $UI_CONFIG_DIR/run.sh

export LOAD_BALANCER_IP="$LOAD_BALANCER_IP"
export LOAD_BALANCER_API_PORT="$LOAD_BALANCER_API_PORT"
export LOAD_BALANCER_HTTPS_PORT="$LOAD_BALANCER_HTTPS_PORT";
export LOGINREDIRECTENDPOINT="$MGMT_UI_LOGIN_URL"
export LOGOUTREDIRECTENDPOINT="$MGMT_UI_LOGOUT_URL"
export ENABLE_AUTH=false
nginx -g 'daemon off;'
EOF

chmod +x $UI_CONFIG_DIR/run.sh

docker run -d \
    --name photon-ui \
    --net=lightwave \
    --ip=$HOST_IP \
    -e LOAD_BALANCER_IP=$LOAD_BALANCER_IP \
    -e LOAD_BALANCER_API_PORT=$LOAD_BALANCER_API_PORT \
    -e LOAD_BALANCER_HTTPS_PORT=$LOAD_BALANCER_HTTPS_PORT \
    -e LOGINREDIRECTENDPOINT=$MGMT_UI_LOGIN_URL \
    -e LOGOUTREDIRECTENDPOINT=$MGMT_UI_LOGOUT_URL \
    -e ENABLE_AUTH=false \
    esxcloud/management_ui
