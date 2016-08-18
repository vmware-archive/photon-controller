#!/bin/bash -e

# This script generates a deployment create spec from set environment variables
# and creates a deployment entity to CloudStore.
# DEPLOYMENT_SEED_CURL_OPTS are needed for an auth enabled deployment to send the proper credentials.
# This is sent directly from the container because the proper credential files for an auth enabled
# deployment are located only in the container.

if [[ $ENABLE_AUTH == 'true' ]]; then
    DEPLOYMENT_SEED_CURL_OPTS="--cert /etc/keys/machine.crt \
  --key /etc/keys/machine.privkey --cacert /etc/keys/cacert.crt"
    PROTOCOL="https"
else
    DEPLOYMENT_SEED_CURL_OPTS=""
    PROTOCOL="http"
fi

deployment_create_spec_json="{ \
   \"state\" : \"READY\", \
   \"imageDataStoreNames\" : [\"${ESX_DATASTORE}\"], \
   \"imageDataStoreUsedForVMs\" : \"true\", \
   \"imageId\" : \"none\", \
   \"projectId\" : \"none\",\
   \"virtualNetworkEnabled\" : \"false\", \
   \"documentSelfLink\" : \"test-deployment\""

   if [[ $ENABLE_AUTH == 'true' ]]; then
      # Security Groups is escaped twice to format the security group as
      # "<Lightwave tenant>\\<Lightwave Group>". The slashes are escaped when
      # writing to a file and escaped again when sending the curl to the
      # deployment.
      deployment_create_spec_json+=", \
      \"oAuthEnabled\" : true, \
      \"oAuthTenantName\" : \"${LW_DOMAIN_NAME}\", \
      \"oAuthSecurityGroups\" : [\"${LW_DOMAIN_NAME}\\\\\\\\Administrators\"], \
      \"oAuthUserName\" : \"ec-admin@${LW_DOMAIN_NAME}\", \
      \"oAuthPassword\" : \"${LW_PASSWORD}\", \
      \"oAuthServerPort\" : 443"
   else
      deployment_create_spec_json+=", \
      \"oAuthEnabled\" : false"
   fi
   if [ -n $PUBLIC_LW_NETWORK_IP ]; then
      deployment_create_spec_json+=", \
      \"oAuthServerAddress\" : \"${PUBLIC_LW_NETWORK_IP}\""
   else
      private_network_lw_ip = ${PRIVATE_NETWORK_LW_IP} || "172.31.253.67"
      deployment_create_spec_json+="', \
      \"oAuthServerAddress\" : \"${private_network_lw_ip}\""
   fi
   if [[ $STATS_ENABLED == "true" ]]; then
      deployment_create_spec_json+=", \
      \"statsStorePort\" : \"${STATS_STORE_PORT}\", \
      \"statsStoreEndpoint\" : \"${STATS_STORE_ENDPOINT}\", \
      \"statsEnabled\" : \"true\""
   else
      deployment_create_spec_json+=", \
      \"statsEnabled\" : \"false\""
   fi
deployment_create_spec_json+="}"

echo ${deployment_create_spec_json} > ../tmp/deployment_create_spec.json

echo "Seeding deployment state"
docker_curl="docker exec photon-controller-core curl -sS -w \"%{http_code}\" $DEPLOYMENT_SEED_CURL_OPTS \
            -H \"Content-type: application/json\" -d @/devbox_data/tmp/deployment_create_spec.json \
            $PROTOCOL://$PUBLIC_NETWORK_IP:19000/photon/cloudstore/deployments"
vagrant ssh -c "$docker_curl"
exit 0
