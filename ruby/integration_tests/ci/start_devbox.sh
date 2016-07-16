#!/bin/bash -ex

export WORKSPACE=${WORKSPACE:=$(git rev-parse --show-toplevel)}
export DEVBOX=${DEVBOX:="$WORKSPACE/devbox-photon"}
export TESTS=${TESTS:="$WORKSPACE/ruby/integration_tests"}
if [ ! -d "$DEVBOX" ]; then fail "$DEVBOX is not accessible"; fi

# Only continue if all these environment variables are defined
checklist=(PUBLIC_NETWORK_IP PUBLIC_NETWORK_NETMASK PUBLIC_NETWORK_GATEWAY BRIDGE_NETWORK)
for var in "${checklist[@]}"; do
  if [ -z "$(printenv "$var")" ]; then
    echo Cannot start devbox. "$var" is not defined.
    echo This list of env vars must be defined: "${checklist[@]}"
    exit 1
  fi
done

cd "$DEVBOX"

# Installs vagrant-guests-photon
./update_dependencies.sh

# Exporting deployment id generated randomly used to create deployment document in cloudstore:seed
if [ "$(uname)" == "Darwin" ]; then
  export RANDOM_GENERATED_DEPLOYMENT_ID=fixed-test-deployemnt-id
else
  export RANDOM_GENERATED_DEPLOYMENT_ID=$(shuf -i 1000000000-10000000000 -n 1)
fi

if [ -n "$DEPLOYER_TEST" ]; then
  ./prepare-devbox-deployment.sh
  return
fi

# Start fresh devbox and build services
rm -rf "$DEVBOX/log/"
./gradlew :devbox:renewPhoton

# Create deployment create spec
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
      deployment_create_spec_json+=", \
      \"oAuthEnabled\" : true, \
      \"oAuthTenantName\" : \"${LW_DOMAIN_NAME}\", \
      \"oAuthSecurityGroups\" : \"[${LW_DOMAIN_NAME}\\Administrators]\", \
      \"oAuthUserName\" : \"ec-admin@${LW_DOMAIN_NAME}\", \
      \"oAuthPassword\" : \"${LW_PASSWORD}\",\
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
      \"statsStorePort\" : \"${STATS_STORE_PORT}\",
      \"statsStoreEndpoint\" : \"${STATS_STORE_ENDPOINT}\",
      \"statsEnabled\" : \"true\""
   else
      deployment_create_spec_json+=", \
      \"statsEnabled\" : \"false\""
   fi
deployment_create_spec_json+="}"

echo "Deployment create spec ${deployment_create_spec_json}"

# Send a post to create a deployment entity to CloudStore, DEPLOYMENT_SEED_CURL_OPTS are needed for
# an auth enabled deployment containing the private key, certificate, and cacert.
# This is sent directly because for an auth enabled deployment, a token is needed for API access which
# is checked against the auth endpoint in the deployment entity.

echo "Seeding deployment state"
docker_curl="docker exec photon-controller-core curl -sS -w \"%{http_code}\" $DEPLOYMENT_SEED_CURL_OPTS \
            -H \"Content-type: application/json\" -d \"$deployment_create_spec_json\" -o /tmp/out.txt \
            $PROTOCOL://$PUBLIC_NETWORK_IP:19000/photon/cloudstore/deployments"
vagrant ssh -c "$docker_curl; cat /tmp/out.txt"

# Register real agent to devbox
if [ -n "$REAL_AGENT" ]; then

  cd "$TESTS"
  bundle exec rake seed:host

  if [ "$ENABLE_AUTH" == "true" ]; then
    # Sleep for 2 minutes for the agent on the host to become active
    sleep 120
  else
    # Wait for the host monitoring service to detect the newly added host
    bundle exec rake monitor:host
  fi
fi
