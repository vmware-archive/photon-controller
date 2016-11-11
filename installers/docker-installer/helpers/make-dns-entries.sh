#!/bin/bash -xe
LIGHTWAVE_PASSWORD=$1
LIGHTWAVE_DOMAIN=$2

docker cp ./helpers/make-dns-entries-helper.sh lightwave-1:/
docker exec -t lightwave-1 /make-dns-entries-helper.sh "$LIGHTWAVE_PASSWORD" $LIGHTWAVE_DOMAIN
exit $?
