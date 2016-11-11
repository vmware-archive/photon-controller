#!/bin/bash +xe

echo "Cleaning up old containers..."

docker ps -q --filter "name=photon-controller" | xargs docker kill > /dev/null 2>&1
docker ps -qa --filter "name=photon-controller" | xargs docker rm > /dev/null 2>&1
docker ps -q --filter "name=haproxy" | xargs docker kill > /dev/null 2>&1
docker ps -qa --filter "name=haproxy" | xargs docker rm > /dev/null 2>&1
docker ps -q --filter "name=lightwave" | xargs docker kill > /dev/null 2>&1
docker ps -qa --filter "name=lightwave" | xargs docker rm > /dev/null 2>&1
docker ps -q --filter "name=photon-controller-ui" | xargs docker kill > /dev/null 2>&1
docker ps -qa --filter "name=photon-controller-ui" | xargs docker rm > /dev/null 2>&1
docker ps -q --filter "name=photon-config" | xargs docker kill > /dev/null 2>&1
docker ps -qa --filter "name=photon-config" | xargs docker rm > /dev/null 2>&1

echo "Cleaning up old networks..."
docker network ls -q --filter "name=lightwave" | xargs docker network rm > /dev/null 2>&1 || true
