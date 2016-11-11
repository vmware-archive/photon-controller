#!/bin/bash -xe

docker-machine ls -q | xargs -I {} docker-machine scp ./helpers/prepare-docker-machine-helper.sh {}:/tmp/
docker-machine ls -q | xargs -I {} docker-machine ssh {} /tmp/prepare-docker-machine-helper.sh
