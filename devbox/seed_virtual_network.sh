#!/bin/bash -e

# This script generates a root subnet allocator as well as a floating IP DHCP allocator
# from environment variables. The allocator services will be used by other virtual network
# related APIs.
# VIRTUAL_NETWORK_SEED_CURL_OPTS are needed for an auth enabled deployment to send the proper
# credentials to cloud-store. This is sent directly from the container because the proper
# credential files for an auth enabled deployment are located only in the container.

convertIpv4ToLong() {
   local ip=$1
   IFS=. read -r a b c d <<< "$ip"
   echo $((a * 256 ** 3 + b * 256 ** 2 + c * 256 + d))
}

seedCloudStore() {
  local seed_json=$1
  local seed_file=$2
  local cloud_store_path=$3

  echo ${seed_json} > ../tmp/${seed_file}
  docker_curl="docker exec photon-controller-core curl -sS -w \"%{http_code}\" ${VIRTUAL_NETWORK_SEED_CURL_OPTS} \
              -H \"Content-type: application/json\" -d @/devbox_data/tmp/${seed_file} \
              ${PROTOCOL}://${network_ip}:19000/photon/cloudstore/${cloud_store_path}"
  vagrant ssh -c "$docker_curl"
}

if [[ $ENABLE_AUTH == "true" ]]; then
    VIRTUAL_NETWORK_SEED_CURL_OPTS="--cert /etc/keys/machine.crt \
  --key /etc/keys/machine.privkey --capath /etc/ssl/certs"
    PROTOCOL="https"
else
    VIRTUAL_NETWORK_SEED_CURL_OPTS=""
    PROTOCOL="http"
fi

if [[ -n $PUBLIC_NETWORK_IP ]]; then
  network_ip=${PUBLIC_NETWORK_IP}
elif [[ -n $PRIVATE_NETWORK_IP ]]; then
  network_ip=${PRIVATE_NETWORK_IP}
else
  network_ip="172.31.253.66"
fi

if [[ -n $PUBLIC_LW_NETWORK_IP ]]; then
  lw_network_ip=${PUBLIC_LW_NETWORK_IP}
elif [[ -n $PRIVATE_NETWORK_LW_IP ]]; then
  lw_network_ip=${PRIVATE_NETWORK_LW_IP}
else
  lw_network_ip="172.31.253.67"
fi

NETWORK_DHCP_PORT=${NETWORK_DHCP_PORT:=17000}

root_subnet_allocator_json="{ \
  \"rootCidr\" : \"${NETWORK_IP_RANGE}\", \
  \"dhcpAgentEndpoint\" : \"http://${NETWORK_DHCP_SERVER}:${NETWORK_DHCP_PORT}\", \
  \"documentSelfLink\" : \"/photon/cloudstore/subnet-allocators/root-subnet\" \
}"

floating_low_ip=$(convertIpv4ToLong ${NETWORK_EXTERNAL_IP_START})
floating_high_ip=$(convertIpv4ToLong ${NETWORK_EXTERNAL_IP_END})
floating_low_ip_dynamic=`expr $floating_low_ip + 1`
floating_high_ip_dynamic=`expr $floating_high_ip - 1`
floating_size=`expr $floating_high_ip - $floating_low_ip + 1`

floating_ip_allocator_json="{ \
  \"cidr\" : \"${NETWORK_EXTERNAL_IP_RANGE}\", \
  \"lowIp\" : \"${floating_low_ip}\", \
  \"highIp\" : \"${floating_high_ip}\", \
  \"lowIpDynamic\" : \"${floating_low_ip_dynamic}\", \
  \"highIpDynamic\" : \"${floating_high_ip_dynamic}\", \
  \"size\" : \"${floating_size}\", \
  \"doGarbageCollection\" : \"false\", \
  \"subnetId\" : \"floating-ip-dhcp-subnet\",
  \"isFloatingIpSubnet\" : \"true\", \
  \"dhcpAgentEndpoint\" : \"http://${NETWORK_DHCP_SERVER}:${NETWORK_DHCP_PORT}\", \
  \"documentSelfLink\" : \"/photon/cloudstore/dhcp-subnets/floating-ip-dhcp-subnet\" \
}"

echo "Seeding root subnet allocator"
seedCloudStore "${root_subnet_allocator_json}" "root_subnet_allocator.json" "subnet-allocators"

echo "Seeding floating IP allocator"
seedCloudStore "${floating_ip_allocator_json}" "floating_ip_allocator.json" "dhcp-subnets"
