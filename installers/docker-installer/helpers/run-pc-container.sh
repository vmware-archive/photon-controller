#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

MEMORY_MB=1024
ENABLE_SYSLOG=false

HOST_IP=$1
HOSTNAME=$2
PEER0_IP=$2
PEER1_IP=$3
PEER2_IP=$4
LIGHTWAVE_HOST_IP=$5
LOAD_BALANCER_IP=$6
LIGHTWAVE_LOAD_BALANCER_IP=$LOAD_BALANCER_IP
NUMBER=$7
MULTI_CONTAINER=$8
LIGHTWAVE_PASSWORD=$9
LIGHTWAVE_DOMAIN=${10}
ENABLE_FQDN=${11}
PC_CONTAINER_VERSION=${12}
IMAGE_DATASTORE_NAMES=${13}
SYSLOG_ENDPOINT=${14}
NTP_ENDPOINT=${15}

if [ "${SYSLOG_ENDPOINT}TEST" != "TEST" ]; then
  ENABLE_SYSLOG=true
fi

LIGHTWAVE_USERNAME=Administrator
LIGHTWAVE_SECURITY_GROUPS="${LIGHTWAVE_DOMAIN}\\admins"
FQDN_SETTING=""
if [ $ENABLE_FQDN -eq 1 ]; then
  FQDN_SETTING="--hostname ${HOSTNAME} --dns ${LIGHTWAVE_HOST_IP} --dns-search ${LIGHTWAVE_DOMAIN}"
fi

mkdir -p tmp
rm -rf "($PWD)/tmp/pc_tmp*"
PC_TMP_DIR=$(mktemp -d "$PWD/tmp/pc_tmp.XXXXX")

echo "Creating configuration data container..."
# Create data container for sharing config data between containers.
docker create \
  -v /etc/photon-controller \
  -v /usr/local/etc/haproxy \
  -v /etc/ssl/private \
  --name photon-config-${NUMBER} \
   vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/true

PHOTON_CONTROLLER_CONFIG_DIR=${PC_TMP_DIR}/config
PHOTON_CONTROLLER_CONFIG_DIR_DATA_VOLUME="/etc/photon-controller"
VOLUME_CONFIG="--volumes-from photon-config-${NUMBER}"

mkdir -p $PHOTON_CONTROLLER_CONFIG_DIR

if [ "$MULTI_CONTAINER" == "1" ]; then
  PEERS="  - https://${PEER0_IP}:19000
  - https://${PEER1_IP}:19000
  - https://${PEER2_IP}:19000"
else
  PEERS="  - https://${HOST_IP}:19000"
fi

cat << EOF > $PHOTON_CONTROLLER_CONFIG_DIR/photon-controller-core.yml

xenon:
  bindAddress: "0.0.0.0"
  securePort: 19000
  keyFile: "/etc/keys/machine.privkey"
  certificateFile: "/etc/keys/machine.crt"
  port: 0
  registrationAddress: "${HOST_IP}"
  storagePath: "/etc/esxcloud/cloud-store/sandbox_19000"
  peerNodes:
${PEERS}

deployer:
  deployer:
    defaultDeploymentEnabled: true
    loadBalancerEnabled: true
    loadBalancerAddress: ${LOAD_BALANCER_IP}
    imageDataStoreNames:
    - ${IMAGE_DATASTORE_NAMES}
    imageDataStoreUsedForVMs: "True"
    apifeEndpoint: "https://${PEER0_IP}:9000"
    configDirectory: "/etc/esxcloud-deployer/configurations/"
    maxMemoryGb: 10000
    maxVmCount: 1000
    tenantName: "photon-controller"
    projectName: "photon-controller"
    resourceTicketName: "photon-controller-rt"
    scriptDirectory: "/usr/lib/esxcloud/photon-controller-core/scripts"
    scriptLogDirectory: "/var/log/photon-controller/script_logs"
    sharedSecret: "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    vibDirectory: "/var/esxcloud/packages"
    enableAuth: true
    vibUninstallOrder:
    - photon-controller-agent
    - envoy
    memoryMb: ${MEMORY_MB}
    enableSyslog: "${ENABLE_SYSLOG}"
    syslogEndpoint: "${SYSLOG_ENDPOINT}"
    logDirectory: /var/log/photon-controller/
    keyStorePath: /keystore.jks
    keyStorePassword: ${LIGHTWAVE_PASSWORD}

photon-controller-logging:
  file:
    enabled: true
    currentLogFilename: /var/log/photon-controller/photon-controller.log
    archivedLogFilenamePattern: /var/log/photon-controller/photon-controller-%d.log.gz
    logFormat: "%-5p [%d{ISO8601}] %c: %m\n%ex"
  syslog:
    enabled: ${ENABLE_SYSLOG}
    host: "${SYSLOG_ENDPOINT}"
    logFormat: "%-5p [%d{ISO8601}] [photon-controller] [%property{instance}]%X{request}%X{task} %c: %m\n%ex"
    facility: LOCAL0

apife:
  type: simple
  minThreads: 8
  maxThreads: 512
  applicationContextPath: /
  registerDefaultExceptionMappers: false
  connector:
    type: https
    port: 9000
    bindHost: "0.0.0.0"
    keyStorePath: /keystore.jks
    keyStorePassword: ${LIGHTWAVE_PASSWORD}
    validateCerts: false
    supportedProtocols: [TLSv1.1, TLSv1.2]
    excludedProtocols: [TLSv1, SSLv2Hello, SSLv3]

use_virtual_network: false

# to add console logging add '- type: console' below
logging:
  appenders:
    - type: file
      currentLogFilename: /var/log/photon-controller/photon-controller.log
      archive: true
      archivedLogFilenamePattern: /var/log/photon-controller/photon-controller-%d.log.gz
      archivedFileCount: 5
      logFormat: "%-5p [%d{ISO8601}] %c: %m\n%ex"

image:
  use_esx_store: true

auth:
  enableAuth: true
  sharedSecret: f81d4fae-7dec-11d0-a765-00a0c91e6bf6
  authServerAddress: ${LIGHTWAVE_HOST_IP}
  authLoadBalancerAddress: ${LIGHTWAVE_LOAD_BALANCER_IP}
  authServerPort: 443
  authDomain: ${LIGHTWAVE_DOMAIN}
  authUserName: ${LIGHTWAVE_USERNAME}
  authPassword: ${LIGHTWAVE_PASSWORD}
  authSecurityGroups:
    - ${LIGHTWAVE_SECURITY_GROUPS}
EOF

docker run -d --volumes-from photon-config-${NUMBER} --name volume-helper vmware/photon-controller-seed:$PC_CONTAINER_VERSION /bin/sh -c "while true; do ping 8.8.8.8; done" > /dev/null 2>&1

docker cp $PHOTON_CONTROLLER_CONFIG_DIR/photon-controller-core.yml volume-helper:$PHOTON_CONTROLLER_CONFIG_DIR_DATA_VOLUME > /dev/null 2>&1
docker kill volume-helper > /dev/null 2>&1
docker rm volume-helper > /dev/null 2>&1

rm -rf $PHOTON_CONTROLLER_CONFIG_DIR
rm -rf tmp

echo "Starting Photon-Controller container $NUMBER..."
docker run -d \
  --name photon-controller-${NUMBER} \
  --privileged \
  -v /sys/fs/cgroup:/sys/fs/cgroup:ro \
  --net=lightwave \
  $FQDN_SETTING \
  --ip=$HOST_IP \
  --entrypoint=/usr/sbin/init \
  ${VOLUME_CONFIG} \
  vmware/photon-controller:$PC_CONTAINER_VERSION

sleep 15
