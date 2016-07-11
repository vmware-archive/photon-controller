#!/bin/bash -xe

photon_vm_hostname=$1
lb_ip=$2
vm_ip=$3

domain="photon.vmware.com"

##
## build confguration files
##
PHOTON_SWAGGER_LOGIN_URL="https://${vm_ip}/api/login-redirect.html"
PHOTON_SWAGGER_LOGOUT_URL="https://${vm_ip}/api/login-redirect.html"
API_SHARED_SECRET="f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
CONFIG_EXTS="{yml,config,js,sh,sql,cfg,json}"
dynamic_params_file="/home/vagrant/dynamic-params.json"
peer_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":19000},{"peerAddress" : "172.31.253.67", "peerPort":19000},{"peerAddress" : "172.31.253.68", "peerPort":19000}]'
peer_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":19000},{"peerAddress" : "172.31.253.67", "peerPort":19000}]'

# peer_nodes = '[{"peerAddress" : "172.31.253.66", "peerPort":19000}]'
#api_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":9000},{"peerAddress" : "172.31.253.67", "peerPort":9000},{"peerAddress" : "172.31.253.68", "peerPort":9000}]'
#ui_nodes='[{"peerAddress" : "172.31.253.66", "peerPort":4433},{"peerAddress" : "172.31.253.67", "peerPort":4433},{"peerAddress" : "172.31.253.68", "peerPort":4433}]'

dynamic_params="{ \
  \"TASK_EXPIRATION_THRESHOLD\": \"3 minutes\", \
  \"TASK_EXPIRATION_SCAN_INTERVAL\": \"3 minutes\", \
  \"LOAD_BALANCER_SERVERS\": [ { \"serverName\": \"lb\", \"serverAddress\": \"${lb_ip}:80\" } ], \
  \"ZOOKEEPER_INSTANCES\": [ { \"zookeeperInstance\": \"server.1=${lb_ip}:2888:3888\" } ], \
  \"SHARED_SECRET\": \"${API_SHARED_SECRET}\", \
  \"ENABLE_AUTH\" : \"true\", \
  \"AUTH_SERVER_TENANT\" : \"photon.vmware.com\", \
  \"AUTH_SERVER_PORT\" : \"443\", \
  \"AUTH_SERVER_ADDRESS\" : \"photon1.photon.vmware.com\", \
  \"SWAGGER_LOGIN_URL\" : \"${PHOTON_SWAGGER_LOGIN_URL}\", \
  \"SWAGGER_LOGOUT_URL\" : \"${PHOTON_SWAGGER_LOGOUT_URL}\", \
  \"APIFE_PORT\" : \"9000\", \
  \"USE_ESX_STORE\" : \"false\", \
  \"ENABLE_SYSLOG\" : \"false\", \
  \"PHOTON_CONTROLLER_PEER_NODES\" : ${peer_nodes}, \
  \"memoryMb\" : \"2048\", \
  \"REGISTRATION_ADDRESS\" : \"${vm_ip}\", \
  \"APIFE_IP\": \"${vm_ip}\", \
  \"MGMT_API_HTTP_SERVERS\": [ { \"serverName\": \"devbox-photon-controller-core\", \"serverAddress\": \"${vm_ip}:9000\" } ], \
  \"MGMT_UI_HTTP_SERVERS\": [ { \"serverName\": \"devbox-management-ui-http\", \"serverAddress\": \"${vm_ip}:80\" } ], \
  \"MGMT_UI_HTTPS_SERVERS\": [ { \"serverName\": \"devbox-management-ui-https\", \"serverAddress\": \"${vm_ip}:443\" } ], \
  \"LOG_DIRECTORY\": \"/var/log/photon/${photon_vm_hostname}\" \
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

# config build dir is created by vagrant file
config_build_dir="/configuration"
config_dir="/devbox_data/java/photon-controller-core/src/dist/configuration"
config_file="${config_dir}/photon-controller-core_test.json"

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


# Setup hostname for this host, which is required to run the Lightwave server.
sudo sed -i '/photon-.*/d' /etc/hosts
sudo hostnamectl set-hostname ${photon_vm_hostname}.${domain}
sudo hostnamectl set-hostname --static ${photon_vm_hostname}.${domain}
sudo sh -c 'echo "172.31.253.66  photon1.photon.vmware.com" >> /etc/hosts'
sudo sh -c 'echo "172.31.253.67  photon2.photon.vmware.com" >> /etc/hosts'
sudo sh -c 'echo "172.31.253.68  photon3.photon.vmware.com" >> /etc/hosts'

##
## building the docker container
##
docker build -t photon/photon-controller-core /vagrant/lightwave-base
docker build -t photon/photon-controller /vagrant/photon-controller
mkdir -p /vagrant/${photon_vm_hostname}
sudo docker save photon/photon-controller > /vagrant/${photon_vm_hostname}/photon-controller.tar

##
## start docker container
##
docker run -d \
  --name photon-controller \
  --net="host" \
  --privileged=true \
  --entrypoint=/usr/sbin/init \
  -v /devbox_data/tmp:/devbox_data/tmp \
  -v /vagrant/log/${photon_vm_hostname}:/vagrant/log/photon/${photon_vm_hostname} \
  -v /var/log:/var/log \
  -v /sys/fs/cgroup:/sys/fs/cgroup \
  -v ${config_build_dir}:/etc/esxcloud \
  -v ${config_build_dir}:/var/lib/vmware/config \
  photon/photon-controller

docker restart `docker ps | tail -n1 | awk '{print $1}'`
