#!/bin/bash +xe

LIGHTWAVE_PASSWORD=$1
LIGHTWAVE_DOMAIN=$2

HOST_IP=192.168.114.20
LIGHTWAVE_IP=192.168.114.2

# Use Photon controller container to generate SSL keys,
# because Photon Controller container has all the tools to
# talk with Lightwave and generate the keys. No need to provide
# Peer nodes.
# To export the keys from the container we need to export following.
./helpers/run-pc-container.sh $HOST_IP $HOST_IP I ROCK $LIGHTWAVE_IP x key-generator 0 $LIGHTWAVE_PASSWORD $LIGHTWAVE_DOMAIN 0

# Create pem file for haproxy use
docker exec -t photon-controller-key-generator /bin/bash -c "cat /etc/keys/machine.crt /etc/keys/machine.privkey > /etc/ssl/private/machine.pem"
docker cp ./helpers/haproxy.cfg photon-controller-key-generator:/usr/local/etc/haproxy/

# Remove the temporary key generator container
docker kill photon-controller-key-generator
docker rm photon-controller-key-generator

echo "Starting HAProxy load balancer container #$NUMBER..."
(set -x;
docker run -d \
       --name haproxy \
       -p 9000:9000 \
       -p 9001:9001 \
       -p 443:443 \
       -p 80:80 \
       -p 4343:4343 \
       --net=lightwave \
       --ip=$HOST_IP \
       --volumes-from photon-config-key-generator \
       haproxy \
       haproxy -f /usr/local/etc/haproxy/haproxy.cfg)
