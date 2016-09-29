#!/bin/sh -xe

docker cp ./make-users-helper.sh photon-controller-1:/

docker exec -t photon-controller-1 /make-users-helper.sh
