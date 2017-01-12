#!/bin/bash
# Script that runs in Pod and updates $PHOTON_CONF_FILE with username/passwrod
# which are retrieved from Kubernetes API server as secret.
# User is reponsible for create a secret called 'photon-keys' with username/password in it.
# Following is the example of commands to create such a secret.
#     echo "admin" > username
#     echo "password" > password
#     kubectl create secret generic photon-keys --from-file=./username --from-file=./password
# As soon as secret is created, this script ,which is running infinite loop, will fetch the secret
# and update the $PHOTON_CONF_FILE with it.

PHOTON_CONF_FILE="/etc/photon/photon.cfg"
PHTON_BASE_FILE="./photon.cfg"

KUBE_TOKEN=""
username=""
password=""
WAIT=600

while true
do
  # If photon.cfg is not availabel at desired destination then copy our default.
  if [ ! -f $PHOTON_CONF_FILE ]; then
    cp -f $PHOTON_BASE_FILE $PHOTON_CONF_FILE
  fi

  KUBE_TOKEN=$(</var/run/secrets/kubernetes.io/serviceaccount/token)
  if [ "$KUBE_TOKEN" == "" ]; then
    echo "KUBE_TOKEN not found"
    sleep $WAIT
    continue
  fi

  response=$(curl -sSk -H "Authorization: Bearer $KUBE_TOKEN" https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_PORT_443_TCP_PORT/api/v1/namespaces/default/secrets/photon-keys)
  username=$(echo $response | jq '.data.username' | tr -d '"' | base64 --decode)
  password=$(echo $response | jq '.data.password' | tr -d '"' | base64 --decode)

  if [ "$username" == "null" ]; then
    echo "Username not found in secret."
    sleep $WAIT
    continue
  fi

  if [ "$password" == "null" ]; then
    echo "Password not found in secret."
    sleep $WAIT
    continue
  fi

  # Update the photon config file with username/password

  sed -i -n '/username/!p' $PHOTON_CONF_FILE
  sed -i -n '/password/!p' $PHOTON_CONF_FILE
  echo "username = $username" >> $PHOTON_CONF_FILE
  echo "password = $password" >> $PHOTON_CONF_FILE

  echo "Updated photon config file. Sleeping for $WAIT seconds."
  sleep $WAIT
done
