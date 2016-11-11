#!/bin/bash

docker ps -qa --filter "name=photon-controller" | xargs docker kill
docker ps -qa --filter "name=photon-controller" | xargs docker rm
docker ps -qa --filter "name=haproxy" | xargs docker kill
docker ps -qa --filter "name=haproxy" | xargs docker rm
docker ps -qa --filter "name=lightwave" | xargs docker kill
docker ps -qa --filter "name=lightwave" | xargs docker rm
docker ps -qa --filter "name=photon-ui" | xargs docker kill
docker ps -qa --filter "name=photon-ui" | xargs docker rm
docker network rm lightwave
