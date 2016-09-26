#!/bin/sh -xe

MEMORY_MB=1024

ENABLE_SYSLOG=False
SYSLOG_ENDPOINT=
NTP_ENDPOINT=
IMAGE_DATASTORE_NAME=datastore1
PHOTON_USER=photon
PHOTON_USER_PASSWORD='P@ssword!'
PHOTON_USER_FIRST_NAME="Light"
PHOTON_USER_LAST_NAME="Wave"


HOST_IP=$1
PEER1_IP=$2
PEER2_IP=$3
LIGHTWAVE_MASTER_HOST_NAME=$4
NUMBER=$5

LIGHTWAVE_PASSWORD='Admin!23'
LIGHTWAVE_TENANT=photon.local
LIGHTWAVE_DOMAIN_CONTROLLER=$LIGHTWAVE_MASTER_HOST_NAME
LIGHTWAVE_SUBJECT_ALT_NAME=$HOST_IP

rm -rf "$(PWD)/pc_tmp*"
PC_TMP_DIR=$(mktemp -d "$PWD/pc_tmp.XXXXX")
trap "rm -rf ${PC_TMP_DIR}" EXIT

PHOTON_CONTROLLER_CONFIG_DIR=${PC_TMP_DIR}/config
LOG_DIRECTORY=${PC_TMP_DIR}/log/photon-controller

mkdir -p $PHOTON_CONTROLLER_CONFIG_DIR
mkdir -p $LOG_DIRECTORY

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
  - https://${HOST_IP}:19000
  - https://${PEER1_IP}:19000
  - https://${PEER2_IP}:19000

deployer:
  deployer:
    createDefaultDeployment: "True"
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
    installDirectory: "/usr/lib/esxcloud/photon-controller-core"
    enableSyslog: "${ENABLE_SYSLOG}"
    syslogEndpoint: "${SYSLOG_ENDPOINT}"
    logDirectory: /var/log/photon-controller/
    keyStorePath: /keystore.jks
    keyStorePassword: ${LIGHTWAVE_PASSWORD}
    lightwaveDomain: ${LIGHTWAVE_TENANT}
    lightwaveHostname: ${LIGHTWAVE_MASTER_HOST_NAME}
    lightwaveDomainController: ${LIGHTWAVE_DOMAIN_CONTROLLER}
    lightwaveDisableVmafdListener: True
    lightwaveSubjectAltName: ${LIGHTWAVE_SUBJECT_ALT_NAME}

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
# if you are using syslog
#    - type: syslog
#      host: "${SYSLOG_ENDPOINT}"
#      logFormat: "%-5p [%d{ISO8601}] [photon-controller] %X{request}%X{task} %c: %m\n%ex"
#      facility: LOCAL0

image:
  use_esx_store: true

auth:
  enable_auth: true
  sharedSecret: f81d4fae-7dec-11d0-a765-00a0c91e6bf6
  auth_server_address: ${LIGHTWAVE_MASTER_HOST_NAME}
  auth_server_port: 443
  tenant: ${LIGHTWAVE_TENANT}

EOF

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
