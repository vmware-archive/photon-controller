#!/bin/bash -xe

HOST_IP=$1
LOAD_BALANCER_IP=$2
LOAD_BALANCER_API_PORT=$3
LOAD_BALANCER_HTTPS_PORT=$4
MGMT_UI_LOGIN_URL="https://192.168.77.135/openidconnect/oidc/authorize/photon.local?scope=rs_esxcloud+at_groups+openid&response_type=id_token+token&correlation_id=LIEyuObXpMN7YUyeaRHARVX9Ru-vIdO-tO-cBDvtMes&redirect_uri=https%3A%2F%2F192.168.77.135%3A4343%2Flogin_redirect.html&state=L&nonce=1&client_id=c5a6cc98-0dca-4701-be87-5233fb0cad07&response_mode=fragment"
MGMT_UI_LOGOUT_URL="https://192.168.77.135/openidconnect/logout/photon.local?id_token_hint=[ID_TOKEN_PLACEHOLDER]&correlation_id=mm_7kTtC0kJSGk6gGa22sm3RfwlTVnRo1TyQ9Xaxobg&state=E&post_logout_redirect_uri=https%3A%2F%2F192.168.77.135%3A4343%2Flogout_redirect.html"

rm -rf "($PWD)/ui_tmp*"
UI_TMP_DIR=$(mktemp -d "$PWD/ui_tmp.XXXXX")
#trap "rm -rf ${UI_TMP_DIR}" EXIT

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
    -p 80:20000 \
    -p 443:20001 \
    -e LOAD_BALANCER_IP=$LOAD_BALANCER_IP \
    -e LOAD_BALANCER_API_PORT=$LOAD_BALANCER_API_PORT \
    -e LOAD_BALANCER_HTTPS_PORT=$LOAD_BALANCER_HTTPS_PORT \
    -e LOGINREDIRECTENDPOINT=$MGMT_UI_LOGIN_URL \
    -e LOGOUTREDIRECTENDPOINT=$MGMT_UI_LOGOUT_URL \
    -e ENABLE_AUTH=false \
    esxcloud/management_ui
