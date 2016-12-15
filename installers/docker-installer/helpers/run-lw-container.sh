#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

LIGHTWAVE_IP=$1
LIGHTWAVE_MASTER_IP=$2
LIGHTWAVE_PASSWORD=$3
LIGHTWAVE_DEPLOYMENT=$4
NUMBER=$5
LW_CONTAINER_VERSION=$6
PC_CONTAINER_VERSION=$7

CONTAINER_NAME=lightwave-$NUMBER
LIGHTWAVE_DOMAIN=photon.local
LIGHTWAVE_SITE=Default-first-site

mkdir -p tmp
LW_TMP_DIR=$(mktemp -d "$PWD/tmp/lw_tmp.XXXXX")
trap "rm -rf $LW_TMP_DIR" EXIT
trap "tput cnorm" EXIT

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
EOF

docker run -d --volumes-from photon-config-data -v $LIGHTWAVE_CONFIG_DIR:/config --name volume-helper vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/sh -c "while true; do ping 8.8.8.8; done" > /dev/null 2>&1

docker cp $LIGHTWAVE_CONFIG_PATH volume-helper:$LIGHTWAVE_CONFIG_DIR_DATA_VOLUME
docker kill volume-helper > /dev/null 2>&1
docker rm volume-helper > /dev/null 2>&1

rm -rf $LIGHTWAVE_CONFIG_PATH

echo "Starting Lightwave container $NUMBER..."
docker run -d \
           --name ${CONTAINER_NAME} \
           --privileged \
           --net=lightwave \
           --ip=$LIGHTWAVE_IP \
           --tmpfs /run \
           --cap-add=SYS_ADMIN --security-opt=seccomp:unconfined \
           -v /sys/fs/cgroup:/sys/fs/cgroup:ro \
           ${VOLUME_CONFIG} \
           vmware/lightwave-sts:$LW_CONTAINER_VERSION

# Check if lightwave server is up
attempts=1
reachable="false"
total_attempts=50
progress_bar="\|/-"
index=0
check_counter=0
tput civis
while [ $attempts -lt $total_attempts ] && [ $reachable != "true" ]; do
  if [ $(expr $check_counter % 20) -eq 0 ]; then
    http_code=$(docker exec -t $CONTAINER_NAME curl -I -so /dev/null -w "%{response_code}" -s -X GET --insecure https://127.0.0.1) || true
    attempts=$[$attempts+1]
  fi
  # The curl returns 000 when it fails to connect to the lightwave server
  if [ "$http_code" == "000" ]; then
    printf "\rStarting up Lightwave server ($attempts/$total_attempts). Please wait! ${progress_bar:index:1}"
    sleep 0.2
  else
    reachable="true"
    break
  fi
  index=$((index+1))
  if [ $index -eq 4 ]; then
    index=0
  fi
  check_counter=$((check_counter+1))
done

echo ""
tput cnorm

if [ $attempts -eq $total_attempts ]; then
  echo "Could not connect to Lightwave REST client at $node after $total_attempts attempts"
  exit 1
fi

echo "Lightwave server startup compelted!"
