#!/bin/bash -xe

photon_vm_hostname=$1
lb_ip=$2
vm_ip=$3

##
## build confguration files
##
API_SHARED_SECRET="f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
CONFIG_EXTS="{yml,config,js,sh,sql,cfg,json}"
dynamic_params_file="/home/vagrant/dynamic-params.json"
peer_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":19000},{"peerAddress" : "172.31.253.67", "peerPort":19000},{"peerAddress" : "172.31.253.68", "peerPort":19000}]'
# peer_nodes = '[{"peerAddress" : "172.31.253.66", "peerPort":19000}]'
api_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":9000},{"peerAddress" : "172.31.253.67", "peerPort":9000},{"peerAddress" : "172.31.253.68", "peerPort":9000}]'
ui_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":4433},{"peerAddress" : "172.31.253.67", "peerPort":4433},{"peerAddress" : "172.31.253.68", "peerPort":4433}]'

dynamic_params="{ \
  \"TASK_EXPIRATION_THRESHOLD\": \"3 minutes\", \
  \"TASK_EXPIRATION_SCAN_INTERVAL\": \"3 minutes\", \
  \"LOAD_BALANCER_SERVERS\": [ { \"serverName\": \"lb\", \"serverAddress\": \"${lb_ip}:80\" } ], \
  \"ZOOKEEPER_INSTANCES\": [ { \"zookeeperInstance\": \"server.1=${lb_ip}:2888:3888\" } ], \
  \"SHARED_SECRET\": \"${API_SHARED_SECRET}\", \
  \"ENABLE_AUTH\" : \"false\", \
  \"APIFE_PORT\" : \"9000\", \
  \"USE_ESX_STORE\" : \"false\", \
  \"ENABLE_SYSLOG\" : \"false\", \
  \"DEPLOYER_PEER_NODES\" : ${peer_nodes}, \
  \"CLOUDSTORE_PEER_NODES\" : ${peer_nodes}, \
  \"memoryMb\" : \"1024\", \
  \"REGISTRATION_ADDRESS\" : \"${vm_ip}\", \
  \"APIFE_IP\": \"${vm_ip}\", \
  \"MGMT_API_HTTP_SERVERS\": [ { \"serverName\": \"devbox-photon-controller-core\", \"serverAddress\": \"${vm_ip}:9000\" } ], \
  \"MGMT_UI_HTTP_SERVERS\": [ { \"serverName\": \"devbox-management-ui-http\", \"serverAddress\": \"${vm_ip}:80\" } ], \
  \"MGMT_UI_HTTPS_SERVERS\": [ { \"serverName\": \"devbox-management-ui-https\", \"serverAddress\": \"${vm_ip}:443\" } ], \
  \"LOG_DIRECTORY\": \"/vagrant/log/${photon_vm_hostname}\" \
  }"

echo "${dynamic_params}" > ${dynamic_params_file}
cat ${dynamic_params_file}

# Validate dynamic_params file, catch errors early
echo "Validating dynamic params JSON file:"
cat ${dynamic_params_file}
jsonlint ${dynamic_params_file}
if [[ $? != 0 ]]; then
  echo "Validation failed for file:"
  exit 1
fi

config_build_dir="/configuration"
config_dir="/devbox_data/java/photon-controller-core/src/dist/configuration"
config_file="${config_dir}/photon-controller-core_test.json"

sudo rm -rf ${config_build_dir}
sudo mkdir -p ${config_build_dir}
sudo chown -R vagrant:vagrant ${config_build_dir}
combined_params=$(jq -s '.[0] * (.[1].dynamicParameters | with_entries(select (.key != "DEPLOYMENT_ID")))' ${dynamic_params_file} ${config_file})
set +e
files=`eval echo ${config_dir}/*.${CONFIG_EXTS}`
set -e
for file in ${files}
do
  if [ -f $file ]; then
    output="${config_build_dir}/$(basename $file)"
    mustache - $file <<< "$combined_params" > "$output"
    if [[ "${file#*.}" == "sh" ]]; then
      chmod +x "$output"
    fi
  fi
done

##
## This config is needed for photon-controller to start up
## should be part of the RPM
##
# copy deployer config
dir=/etc/esxcloud-deployer/configurations/haproxy
sudo mkdir -p $dir
sudo cp -a /devbox_data/java/containers/haproxy/config/* $dir

dir=/etc/esxcloud-deployer/configurations/zookeeper
sudo mkdir -p $dir
sudo cp -a /devbox_data/java/containers/zookeeper/config/* $dir

dir=/etc/esxcloud-deployer/configurations/lightwave
sudo mkdir -p $dir
sudo cp -a /devbox_data/java/deployer/src/dist/configuration-lightwave/* $dir

dir=/etc/esxcloud-deployer/configurations/management-ui
sudo mkdir -p $dir
sudo cp -a /devbox_data/java/deployer/src/dist/configuration-management-ui/* $dir

dir=/etc/esxcloud-deployer/configurations/photon-controller-core
sudo mkdir -p $dir
sudo cp -a /devbox_data/java/photon-controller-core/src/dist/configuration/* $dir

## this should be part of the RPM
# copy the externally build vib to the right folder
sudo mkdir -p /var/esxcloud/packages
sudo chown vagrant:vagrant /var/esxcloud/packages
cp /devbox_data/python/dist/*.vib /var/esxcloud/packages


# Setup hostname for this host, which is required to run the Lightwave server.
sudo sed -i s/photon-.*/"lightwave.devbox ${photon_vm_hostname}"/ /etc/hosts
sudo hostnamectl set-hostname ${photon_vm_hostname}
sudo hostnamectl set-hostname --static ${photon_vm_hostname}

##
## building the docker container
##
docker build -t photon/photon-controller-core /devbox_data/devbox-voltron/lightwave-base
docker build -t photon/photon-controller /devbox_data/devbox-voltron/photon-controller

##
## start docker container
##
docker run -d \
  --name photon-controller \
  --net="host" \
  --privileged=true \
  --entrypoint=/usr/sbin/init \
  -v /devbox_data/tmp:/devbox_data/tmp \
  -v /vagrant/log/${photon_vm_hostname}:/vagrant/log/${photon_vm_hostname} \
  -v /var/esxcloud:/var/esxcloud \
  -v /var/log:/var/log \
  -v /var/esxcloud/packages:/var/esxcloud/packages \
  -v /etc/esxcloud-deployer:/etc/esxcloud-deployer \
  -v /devbox_data/java/photon-controller-core/src/main/resources/scripts:/usr/lib/esxcloud/photon-controller-core/scripts \
  -v /devbox_data/java/cluster-manager/backend/src/main/resources/scripts:/usr/lib/esxcloud/photon-controller-core/scripts/clusters \
  -v /var/photon/images:/var/photon/images \
  -v /sys/fs/cgroup:/sys/fs/cgroup \
  -v ${config_build_dir}:/etc/esxcloud \
  photon/photon-controller
