# Manually installing PhotonController plus Lightwave container

## make the container avaibale to docker on your host
- Obtain the container at ...
- Install the container using `docker ...`

## Configure the container
There are three parts to the configuration:

1. Configure the host os
- Configure Lightwave
- Configure PhotonController

### Host OS configuration
In order for lightwave to function properly it needs to be able to resolve (and
be able to do a reverse dns lookup) of the hostname used in the lightwave
configuration.
This can either be done through setting up a DNS server or by making the
necessary entries in ``/etc/hosts``. However those hostnames should be resolvable
by hosts interacting with Photon-Controller.

### Configure PhotonController
To configure Photon-Controller for HA you need to provide the list of Photon-Controller
nodes in the configuration file, note the list can contain all nodes.

- ``PHOTON_CONTROLLER_IP`` ip/hostname of Photon-Controller instance
- ``ENABLE_SYSLOG`` true if you want to log to a syslog endpoint otherwise false
- ``SYSLOG_ENDPOINT`` ip/hostname of syslog endpoint
- ``VM_IP`` ip of the host
- ``LIGHTWAVE_MASTER_HOST_NAME`` this needs to match the host name used to configure the lightwave master node.
- ``LIGHWAVE_TENANT`` this needs to match the domain set in the lightwave config files.

filename: ``photon-controller-core.yml``
```
xenon:
  bindAddress: "0.0.0.0"
  port: 19000
  registrationAddress: "${HOST_IP}"
  storagePath: "/etc/esxcloud/cloud-store/sandbox_19000"
  peerNodes:
  - http://${PHOTON_CONTROLLER_IP1}:19000
  - http://${PHOTON_CONTROLLER_IP2}:19000

deployer:
  deployer:
    apifeEndpoint: "http://127.0.0.1:9000"
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
    enableSyslog: "${ENABLE_SYSLOG}"
    syslogEndpoint: "${SYSLOG_ENDPOINT}"

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
    type: http
    port: 9000
    bindHost: "0.0.0.0"

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
  use_esx_store: false

auth:
  enable_auth: true
  sharedSecret: f81d4fae-7dec-11d0-a765-00a0c91e6bf6
  auth_server_address: ${LIGHTWAVE_MASTER_HOST_NAME}
  auth_server_port: 443
  tenant: ${LIGHWAVE_TENANT}
```

### Configure Lightwave Master
- ``$DOMAIN`` e.g. photon.vmware.com
- ``$LIGHT_WAVE_ADMIN_PW`` the password must contain one upper case, one lower case, one number and on special character with a minimum length of 8, otherwise lightwave will not start up properly.
- ``$HOSTNAME`` fully qualified hostname of the host running this Lightwave instance

filename: ``lightwave-server.cfg``
```
deployment=standalone
domain=$DOMAIN
admin=Administrator
password=$LIGHT_WAVE_ADMIN_PW
site-name=Default-first-site
first-instance=true
hostname=$HOSTNAME
```

### Configure Lightwave Partner
- ``$DOMAIN`` e.g. photon.vmware.com
- ``$LIGHT_WAVE_ADMIN_PW`` the password must contain one upper case, onelower case, one number and on special character with a minimum length of 8, otherwise lightwave will not start up properly.
- ``$HOSTNAME`` fully qualified hostname of the host running this Lightwave instance, e.g. photon1.photon.vmware.com
- ``$MASTER_HOSTNAME`` fully qualified hostname of the host running the Lightwave master instance, e.g. photon1.photon.vmware.com

filename: ``lightwave-server.cfg``
```
deployment=partner
domain=$DOMAIN
admin=Administrator
password=$LIGHT_WAVE_ADMIN_PW
site-name=Default-first-site
hostname=$HOSTNAME
first-instance=false
replication-partner-hostname=$MASTER_HOSTNAME
```

## Starting the container
The container requires access to the config files of both photon-contoller and
lightwave, which can but don't necessarily need to be located in different
directories on the host os. Also Lightwave requires access to ``/sys/fs/cgroup``.

Place the lightwave configuration into ``${LIGHTWAVE_CONFIG_DIR}``, the
configuration file name must be ``lightwave-server.cfg``.
Place the photon-contrller configuration into ``${PHOTON_CONTROLLER_CONFIG_DIR}``,
the configuration file name must be ``photon-controller-core.yml``.

```
docker run -d \
  --name photon-controller \
  --net="host" \
  --privileged=true \
  --entrypoint=/usr/sbin/init \
  -v ${LOG_DIRECTORY}:/var/log \
  -v /sys/fs/cgroup:/sys/fs/cgroup \
  -v ${PHOTON_CONTROLLER_CONFIG_DIR}:/etc/esxcloud \
  -v ${LIGHTWAVE_CONFIG_DIR}:/var/lib/vmware/config \
  photon/photon-controller
```

## Creating a Deployment document
Once all nodes specified in the ``photon-controller-core.yml`` configuration
file have been setup you will need to create a deployment document to make
photon controller operational.

- ``IMAGE_DATASTORE_NAME`` - name of the esxi datavolume used as image datastore (can later be edited through cli/api).
- ``NTP_ENDPOINT`` - ip/hostname for ntp server used to set time on the ESX hosts
- ``SYSLOG_ENDPOINT`` - endpoint of syslog server (optional)
- ``LW_ENDPOINT`` - ip of the lightwave master
- ``LIGHWAVE_TENANT`` - needs to match the domain set in the lightwave config file.
- ``LW_PASSWORD`` - needs to match the password in the lightwave config file.

After filling in all values mentioned above into the command below you should
be able to execute the ``curl`` command to generate the deployment document.

```
curl -X POST \
  -H "Content-type: application/json" -d "{ \
  \"state\" : \"READY\",
  \"imageDataStoreNames\" : [\"${IMAGE_DATASTORE_NAME}\"], \
  \"imageDataStoreUsedForVMs\" : \"true\", \
  \"imageId\" : \"none\", \
  \"projectId\" : \"none\", \
  \"ntpEndpoint\" : \"${NTP_ENDPOINT}\", \
  \"virtualNetworkEnabled\" : \"false\", \
  \"syslogEndpoint\" : \"${SYSLOG_ENDPOINT}\", \
  \"statsEnabled\" : \"false\", \
  \"loadBalancerEnabled\": \"false\", \
  \"loadBalancerAddress\" : \"172.31.253.65:443\", \
  \"oAuthEnabled\" : \"true\", \
  \"oAuthTenantName\" : \"${LIGHWAVE_TENANT}\", \
  \"oAuthUserName\" : \"Administrator\", \
  \"oAuthPassword\" : \"${LW_PASSWORD}\", \
  \"oAuthServerAddress\" : \"${LW_ENDPOINT}\", \
  \"oAuthServerPort\" : 443, \
  \"documentSelfLink\" : \"deployment\" \
  "} \
  http://${PHOTON_CONTROLLER_IP}:19000/photon/cloudstore/deployments
```

## Create users in Lightwave
To add users you can use ``dir-cli`` inside the photon-contrller container
```
# $admin_password is the password defined in the Lightwave configuration file
/opt/vmware/bin/dir-cli ssogroup create --name GROUP_NAME --password $admin_password
/opt/vmware/bin/dir-cli user create --account USER_NAME --user-password USER_PASSWORD --first-name FIRST_NAME --last-name LAST_NAME --password $admin_password
/opt/vmware/bin/dir-cli group modify --name GROUP_NAME --add USER_NAME --password $admin_password
```
These three commands will create a group GROUP_NAME and a user USER_NAME with password USER_PASSWORD and add USER_NAME to the group GROUP_NAME.


## Registering ESX hosts with Photon-Controller
Using the photon cli:

```
# for https endpoint
./photon target --nocertcheck set https://photon-loadbalancer
# for http endpoint
./photon target --nocertcheck set http://photon-loadbalancer:port
# use the a user and password you created in lightwave, USER_NAME@DOMAIN
./photon target login
# follow the prompt, to obtain the deployment id you can run photon deployment list
# if you used the curl command to create the deployment your deployment id will
# be "deployment" without the quotes
./photon host create
```
