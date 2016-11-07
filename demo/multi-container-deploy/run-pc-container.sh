#!/bin/bash -xe

MEMORY_MB=1024

ENABLE_SYSLOG=False
SYSLOG_ENDPOINT=
NTP_ENDPOINT=

HOST_IP=$1
PEER1_IP=$2
PEER2_IP=$3
LIGHTWAVE_HOST_IP=$4
LOAD_BALANCER_IP=$5
LIGHTWAVE_LOAD_BALANCER_IP=$LOAD_BALANCER_IP
NUMBER=$6
MULTI=$7

LIGHTWAVE_USERNAME=Administrator
LIGHTWAVE_PASSWORD='Admin!23'
LIGHTWAVE_DOMAIN=photon.local
LIGHTWAVE_SECURITY_GROUPS="${LIGHTWAVE_DOMAIN}\\admins"

rm -rf "($PWD)/pc_tmp*"
PC_TMP_DIR=$(mktemp -d "$PWD/pc_tmp.XXXXX")
trap "rm -rf ${PC_TMP_DIR}" EXIT

PHOTON_CONTROLLER_CONFIG_DIR=${PC_TMP_DIR}/config
LOG_DIRECTORY=${PC_TMP_DIR}/log/photon-controller

mkdir -p $PHOTON_CONTROLLER_CONFIG_DIR
mkdir -p $LOG_DIRECTORY

if [ "$MULTI" == "multi" ]; then
  PEERS="  - https://${HOST_IP}:19000\n  - https://${PEER1_IP}:19000\n  - https://${PEER2_IP}:19000"
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
    - datastore1
    imageDataStoreUsedForVMs: "True"
    apifeEndpoint: "https://${HOST_IP}:9000"
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

echo "Starting Photon-Controller container #$NUMBER..."
docker run -d \
  --name photon-controller-${NUMBER} \
  --privileged \
  -v /sys/fs/cgroup:/sys/fs/cgroup:ro \
  --net=lightwave \
  --ip=$HOST_IP \
  --entrypoint=/usr/sbin/init \
  -v ${PHOTON_CONTROLLER_CONFIG_DIR}:/etc/photon-controller \
  vmware/photon-controller-lightwave-client

sleep 15
