#!/bin/bash -xe

HOST_IP=192.168.114.20
LIGHTWAVE_IP=192.168.114.2

rm -rf keys/

# Use Photon controller container to generate SSL keys,
# because Photon Controller container has all the tools to
# talk with Lightwave and generate the keys. No need to provide
# Peer nodes.
# To export the keys from the container we need to export following.
export EXPORT_KEYS=1
docker kill photon-controller-key-generator || true
docker rm photon-controller-key-generator || true
./run-pc-container.sh $HOST_IP I ROCK $LIGHTWAVE_IP x key-generator

# Create pem file for haproxy use
cd keys;
sudo chown $USER:$USER *
cd ..
cat keys/machine.crt keys/machine.privkey > keys/machine.pem

# Remove the temporary key generator container
docker kill photon-controller-key-generator
docker rm photon-controller-key-generator

echo "Starting HAProxy load balancer container #$NUMBER..."
docker run -d \
       --name haproxy \
       -p 28080:28080 \
       -p 443:443 \
       -p 80:80 \
       -p 4343:4343 \
       --net=lightwave \
       --ip=$HOST_IP \
       -v `pwd`/haproxy.cfg:/usr/local/etc/haproxy/haproxy.cfg:ro \
       -v `pwd`/keys:/etc/ssl/private \
       haproxy \
       haproxy -f /usr/local/etc/haproxy/haproxy.cfg
