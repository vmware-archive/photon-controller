#!/bin/bash -xe

docker kill haproxy
docker rm haproxy
rm -rf keys/

sed -i -e 's/0.0.0.0:9000/OLD_DEPLOYMENT/g' haproxy.cfg
sed -i -e 's/0.0.0.0:9001/NEW_DEPLOYMENT/g' haproxy.cfg
sed -i -e 's/OLD_DEPLOYMENT/0.0.0.0:9001/g' haproxy.cfg
sed -i -e 's/NEW_DEPLOYMENT/0.0.0.0:9000/g' haproxy.cfg

./helpers/run-haproxy-container.sh
