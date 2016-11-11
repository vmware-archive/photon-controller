#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

USERNAME=$1
PASSWORD=$2
LIGHTWAVE_PASSWORD=$3

docker cp ./helpers/make-users-helper.sh photon-controller-1:/
docker exec -t photon-controller-1 /make-users-helper.sh $USERNAME "$PASSWORD" "$LIGHTWAVE_PASSWORD"
exit 0
