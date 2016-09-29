#!/bin/bash -xe

HOST_IP=192.168.114.20

rm -rf "($PWD)/ngnix_tmp*"
rm -rf keys/
PC_TMP_DIR=$(mktemp -d "$PWD/nginx_tmp.XXXXX")
#trap "rm -rf ${PC_TMP_DIR}" EXIT

NGINX_DIR=${PC_TMP_DIR}/config
NGINX_LOG_DIR=${PC_TMP_DIR}/log/nginx

mkdir -p $NGINX_DIR
mkdir -p $NGINX_LOG_DIR

cp nginx.conf $NGINX_DIR/


# Use Photon controller container to generate SSL keys,
# because Photon Controller container has all the tools to
# talk with Lightwave and generate the keys. No need to provide
# Peer nodes.
# To export the keys from the container we need to export following.
export EXPORT_KEYS=1
docker kill photon-controller-key-generator || true
docker rm photon-controller-key-generator || true
./run-pc-container.sh $HOST_IP x x 192.168.114.2 x key-generator

# Create pem file for haproxy use
cat keys/machine.crt keys/machine.privkey > keys/machine.pem

# Remove the temporary key generator container
docker kill photon-controller-key-generator
docker rm photon-controller-key-generator

echo "Starting Nginx load balancer container #$NUMBER..."
docker run -d \
       --name haproxy \
       -p 28080:28080 \
       -p 443:443 \
       -p 80:80 \
       -p 4343:4343 \
       --net=lightwave \
       --ip=192.168.114.20 \
       -v `pwd`/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro \
       -v `pwd`/keys:/etc/ssl/private \
       haproxy \
       haproxy -f /usr/local/etc/haproxy/haproxy.cfg
