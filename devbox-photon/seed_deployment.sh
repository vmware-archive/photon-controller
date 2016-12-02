#!/bin/bash -e

# This script generates a deployment create spec from set environment variables
# and creates a deployment entity to CloudStore.
# DEPLOYMENT_SEED_CURL_OPTS are needed for an auth enabled deployment to send the proper credentials.
# This is sent directly from the container because the proper credential files for an auth enabled
# deployment are located only in the container.

if [[ $ENABLE_AUTH == "true" ]]; then
    DEPLOYMENT_SEED_CURL_OPTS="--cert /etc/keys/machine.crt \
  --key /etc/keys/machine.privkey --capath /etc/ssl/certs"
    PROTOCOL="https"
else
    DEPLOYMENT_SEED_CURL_OPTS=""
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

deployment_create_spec_json="{ \
   \"state\" : \"READY\", \
   \"imageDataStoreNames\" : [\"${ESX_DATASTORE}\"], \
   \"imageDataStoreUsedForVMs\" : true, \
   \"imageId\" : \"none\", \
   \"projectId\" : \"none\", \
   \"documentSelfLink\" : \"test-deployment\""

   if [[ $ENABLE_AUTH == "true" ]]; then
      # Security Groups is escaped twice to format the security group as
      # "<Lightwave tenant>\<Lightwave Group>". The slashes are escaped when
      # writing to a file and escaped again when sending the curl to the
      # deployment.
      deployment_create_spec_json+=", \
      \"oAuthEnabled\" : true, \
      \"oAuthTenantName\" : \"${LW_DOMAIN_NAME}\", \
      \"oAuthSecurityGroups\" : [\"${LW_DOMAIN_NAME}\\\\Administrators\"], \
      \"oAuthUserName\" : \"ec-admin@${LW_DOMAIN_NAME}\", \
      \"oAuthPassword\" : \"${LW_PASSWORD}\", \
      \"oAuthServerPort\" : 443, \
      \"oAuthServerAddress\" : \"${lw_network_ip}\""
   else
      deployment_create_spec_json+=", \
      \"oAuthEnabled\" : false"
   fi
   if [[ $STATS_ENABLED == "true" ]]; then
      deployment_create_spec_json+=", \
      \"statsStorePort\" : \"${STATS_STORE_PORT}\", \
      \"statsStoreEndpoint\" : \"${STATS_STORE_ENDPOINT}\", \
      \"statsEnabled\" : true"
   else
      deployment_create_spec_json+=", \
      \"statsEnabled\" : false"
   fi
   if [[ $ENABLE_NSX == "true" ]]; then
      deployment_create_spec_json+=", \
      \"networkManagerAddress\" : \"${NETWORK_MANAGER_ADDRESS}\", \
      \"networkManagerUsername\" : \"${NETWORK_MANAGER_USERNAME}\", \
      \"networkManagerPassword\" : \"${NETWORK_MANAGER_PASSWORD}\", \
      \"networkTopRouterId\" : \"${NETWORK_TOP_ROUTER_ID}\", \
      \"networkZoneId\" : \"${NETWORK_ZONE_ID}\", \
      \"networkEdgeIpPoolId\" : \"${NETWORK_EDGE_IP_POOL_ID}\", \
      \"networkHostUplinkPnic\" : \"${NETWORK_HOST_UPLINK_PNIC}\", \
      \"edgeClusterId\" : \"${NETWORK_EDGE_CLUSTER_ID}\", \
      \"dhcpRelayProfileId\": \"${NETWORK_DHCP_RELAY_PROFILE_ID}\", \
      \"dhcpRelayServiceId\": \"${NETWORK_DHCP_RELAY_SERVICE_ID}\", \
      \"ipRange\" : \"${NETWORK_IP_RANGE}\", \
      \"floatingIpRange\" : {\"start\" : \"${NETWORK_EXTERNAL_IP_START}\", \"end\" : \"${NETWORK_EXTERNAL_IP_END}\"}, \
      \"dhcpServers\" : [\"${NETWORK_DHCP_SERVER}\"], \
      \"sdnEnabled\" : true"
   else
      deployment_create_spec_json+=", \
      \"sdnEnabled\" : false"
   fi
deployment_create_spec_json+="}"

echo ${deployment_create_spec_json} > ../tmp/deployment_create_spec.json

echo "Seeding deployment state"
docker_curl="docker exec photon-controller-core curl -sS -w \"%{http_code}\" ${DEPLOYMENT_SEED_CURL_OPTS} \
            -H \"Content-type: application/json\" -d @/devbox_data/tmp/deployment_create_spec.json \
            ${PROTOCOL}://${network_ip}:19000/photon/cloudstore/deployments"
vagrant ssh -c "$docker_curl"
exit 0
