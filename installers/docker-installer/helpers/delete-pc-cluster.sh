#!/bin/bash

docker ps -qa --filter "name=photon-controller" | xargs docker kill
docker ps -qa --filter "name=photon-controller" | xargs docker rm
