#!/bin/bash -xe

# this should be replaced with building the rpm
rm -rf photon-controller/release
# mkdir -p photon-controller/release
rm -rf log

##
## building the agent vib
##
pushd ../python
rm -rf dist
make vib-only
mkdir -p ../devbox-multi/photon-controller/vib
cp dist/*.vib ../devbox-multi/photon-controller/vib
popd

pushd photon-controller/vib
wget http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/envoy/develop/latest/vmware-envoy-latest.vib
popd

##
## This config is needed for photon-controller to start up
## should be part of the RPM
##
# copy deployer config
rm -rf photon-controller/configurations
mkdir -p photon-controller/configurations
dir=photon-controller/configurations/haproxy
mkdir -p $dir
cp -a ../java/containers/haproxy/config/* $dir

dir=photon-controller/configurations/zookeeper
mkdir -p $dir
cp -a ../java/containers/zookeeper/config/* $dir

dir=photon-controller/configurations/lightwave
mkdir -p $dir
cp -a ../java/deployer/src/dist/configuration-lightwave/* $dir

dir=photon-controller/configurations/management-ui
mkdir -p $dir
cp -a ../java/deployer/src/dist/configuration-management-ui/* $dir

dir=photon-controller/configurations/photon-controller-core
mkdir -p $dir
cp -a ../java/photon-controller-core/src/dist/configuration/* $dir

# copying deployer scripts
rm -rf photon-controller/scripts/deployer
mkdir -p photon-controller/scripts/deployer
cp -r ../java/deployer/src/main/resources/scripts/* photon-controller/scripts/deployer

#-v /devbox_data/java/cluster-manager/backend/src/main/resources/scripts:/usr/lib/esxcloud/photon-controller-core/scripts/clusters \
# copying cluster manager scripts
#rm -rf photon-controller/scripts/cm
#mkdir -p photon-controller/scripts/cm
#cp -r ../java/cluster-manager/backend/src/main/resources/scripts/* photon-controller/scripts/cm

##
## building the java code
## should be replace with building the RPM
##
pushd ../java
rm -rf photon-controller-core/build/container/release
./gradlew distTar
cp -R photon-controller-core/build/container/release ../devbox-multi/photon-controller
popd

# pushd ../java
# ./graldlw rpm
# cp build/x86_64/photon-controller-*-*.rpm ../devbox-multi/photon-controller/release/
# popd

chmod -R +x photon-controller/release/bin

vagrant destroy -f && vagrant up

count=0
while [ 1 -ge 0 ]; do
  if [ `curl -s http://172.31.253.66:19000/core/node-groups/default | grep AVAILABLE | wc -l` -eq 2 ]; then
    break;
  else
    curl -s http://172.31.253.66:19000/core/node-groups/default
    echo
    echo "All nodes are not available yet, sleeping and then retrying"
    sleep 10
    let count=count+1
    if [ $count -ge 10 ]; then
      echo "exeeded retry count"
      exit -1;
    fi
  fi
done

export ESX_IP=192.168.113.128
export API_ADDRESS=172.31.253.65
#export ESX_DATASTORE=datastore1
export ESX_DATASTORE=shared
export ESX_PORT_GROUP="VM VLAN"
export ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE="$HOME/.testdata/from_out_tty.ova"
export LW_PASSWORD='L1ghtWave!'
export LW_DOMAIN_NAME="photon.vmware.com"
export ENABLE_AUTH="true"
export CLOUD_STORE_ADDRESS=172.31.253.66

export PHOTON_AUTH_LS_ENDPOINT="172.31.253.66"
export PHOTON_AUTH_SERVER_PORT="443"
export ENABLE_AUTH=true
export PHOTON_AUTH_SERVER_TENANT="photon.vmware.com"
# export PHOTON_AUTH_ADMIN_GROUPS=
# export PHOTON_USERNAME_ADMIN="Administrator"
# export PHOTON_PASSWORD_ADMIN='L1ghtWave!'
export PHOTON_ADMIN_GROUP="photon.vmware.com\\ESXCloudAdmins"
export PHOTON_USERNAME_ADMIN="ec-admin@photon.vmware.com"
export PHOTON_PASSWORD_ADMIN='Passw0rd!'
export PHOTON_AUTH_LS_ENDPOINT="photon1.photon.vmware.com"
export PHOTON_AUTH_SERVER_PORT=443
export STATS_ENABLED="false"
export RANDOM_GENERATED_DEPLOYMENT_ID=deployment
export PUBLIC_NETWORK_IP=172.31.253.66

# create deployment document
# curl -X POST \
#   -H "Content-type: application/json" -d "{ \
#   \"state\" : \"READY\",
#   \"imageDataStoreNames\" : [\"datastore1\"], \
#   \"imageDataStoreUsedForVMs\" : \"true\", \
#   \"imageId\" : \"none\", \
#   \"projectId\" : \"none\", \
#   \"ntpEndpoint\" : \"1.1.1.1\", \
#   \"virtualNetworkEnabled\" : \"false\", \
#   \"syslogEndpoint\" : \"1.1.1.1\", \
#   \"statsEnabled\" : \"false\", \
#   \"loadBalancerEnabled\": \"false\", \
#   \"loadBalancerAddress\" : \"172.31.253.65:443\", \
#   \"oAuthEnabled\" : \"true\", \
#   \"oAuthTenantName\" : \"esxcloud\", \
#   \"oAuthUserName\" : \"Administrator\", \
#   \"oAuthPassword\" : \"${LW_PASSWORD}\", \
#   \"oAuthServerAddress\" : \"${PHOTON_AUTH_LS_ENDPOINT}\", \
#   \"oAuthServerPort\" : 443, \
#   \"documentSelfLink\" : \"deployment\" \
#   "} \
#   http://172.31.253.66:19000/photon/cloudstore/deployments

# sleep 120
# curl http://172.31.253.66:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.67:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.68:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.65/status
# sleep 120
# curl http://172.31.253.66:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.67:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.68:19000/photon/cloudstore/deployments/deployment
# curl http://172.31.253.65/statusq

## this should be replace with using the photon-cli
# add host
# curl -X POST \
#   -H "Content-type: application/json" -d "{ \
#     \"address\" : \"10.146.36.36\", \
#     \"username\" : \"root\", \
#     \"password\" : \"ca\$hc0w\", \
#     \"usageTags\" : [\"CLOUD\"] \
#   "} \
#   http://172.31.253.65:28080/deployments/deployment/hosts
#
# # wait until host is provisioned
# sleep 120


# configure lightwave
echo "Adding lightwave users."
vagrant ssh photon1 -c /devbox_data/java/containers/lightwave/config/add_lightwave_users.sh\ "${LW_PASSWORD}"
echo "Updating tenant token expiration time."
vagrant ssh photon1 -c /devbox_data/java/containers/lightwave/config/update_token_expiration.sh\ "${LW_PASSWORD}"\ "${LW_DOMAIN_NAME}"

# run integration tests
pushd ../ruby/integration_tests
bundle exec rake cloudstore:seed
bundle exec rake seed:host
# ./ci/run_tests.sh
popd
