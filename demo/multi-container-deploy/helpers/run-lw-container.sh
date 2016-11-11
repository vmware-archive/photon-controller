#!/bin/bash +xe

LIGHTWAVE_IP=$1
LIGHTWAVE_MASTER_IP=$2
LIGHTWAVE_PASSWORD=$3
LIGHTWAVE_DEPLOYMENT=$4
NUMBER=$5
LW_CONTAINER_VERSION=$6

CONTAINER_NAME=lightwave-$NUMBER
LIGHTWAVE_DOMAIN=photon.local
LIGHTWAVE_SITE=Default-first-site

mkdir -p tmp
LW_TMP_DIR=$(mktemp -d "$PWD/tmp/lw_tmp.XXXXX")
trap "rm -rf $LW_TMP_DIR" EXIT

LIGHTWAVE_CONFIG_DIR=${LW_TMP_DIR}/config
VOLUME_CONFIG="-v $LIGHTWAVE_CONFIG_DIR:/var/lib/vmware/config"

LIGHTWAVE_CONFIG_DIR_DATA_VOLUME="/var/lib/vmware/config/"
VOLUME_CONFIG="--volumes-from photon-config-data"

LIGHTWAVE_CONFIG_PATH=${LIGHTWAVE_CONFIG_DIR}/lightwave-server.cfg

mkdir -p $LIGHTWAVE_CONFIG_DIR

cat << EOF > $LIGHTWAVE_CONFIG_PATH
deployment=$LIGHTWAVE_DEPLOYMENT
domain=$LIGHTWAVE_DOMAIN
admin=Administrator
password=$LIGHTWAVE_PASSWORD
site-name=$LIGHTWAVE_SITE
hostname=$LIGHTWAVE_IP
first-instance=false
replication-partner-hostname=$LIGHTWAVE_MASTER_IP
disable-dns=1
EOF

(set -x;
docker run -d --volumes-from photon-config-data -v $LIGHTWAVE_CONFIG_DIR:/config --name volume-helper busybox /bin/sh -c "while true; do ping 8.8.8.8; done")

docker cp $LIGHTWAVE_CONFIG_PATH volume-helper:$LIGHTWAVE_CONFIG_DIR_DATA_VOLUME
docker kill volume-helper > /dev/null 2>&1
docker rm volume-helper > /dev/null 2>&1

rm -rf $LIGHTWAVE_CONFIG_PATH

echo "Starting Lightwave container $NUMBER..."
(set -x;
docker run -d \
           --name ${CONTAINER_NAME} \
           --privileged \
           --net=lightwave \
           --ip=$LIGHTWAVE_IP \
           --cap-add=SYS_ADMIN --security-opt=seccomp:unconfined \
           -v /sys/fs/cgroup:/sys/fs/cgroup:ro \
           ${VOLUME_CONFIG} \
           vmware/lightwave-sts:$LW_CONTAINER_VERSION)

#docker exec -t ${CONTAINER_NAME} systemctl --no-pager > /dev/null 2>&1
#if [ $? -ne 0 ]; then
#  echo "Your system is not prepared to run container with systemd. Run ./helpers/prepare-docker-machine.sh script and try again."
#fi

# Check if lightwave server is up
attempts=1
reachable="false"
total_attempts=50
while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
  http_code=$(docker exec -t $CONTAINER_NAME curl -I -so /dev/null -w "%{response_code}" -s -X GET --insecure https://127.0.0.1) || true
  # The curl returns 000 when it fails to connect to the lightwave server
  if [ "$http_code" == "000" ]; then
    echo "Starting up Lightwave server $CONTAINER_NAME (attempt $attempts/$total_attempts). Please wait!"
    attempts=$[$attempts+1]
    sleep 5
  else
    reachable="true"
    break
  fi
done
if [ $attempts -eq $total_attempts ]; then
  echo "Could not connect to Lightwave REST client at $node after $total_attempts attempts"
  exit 1
fi
