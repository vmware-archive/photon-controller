#!/bin/bash -xe

# This script demonstrate upgrading from OLD deployment to NEW deployment.

USERNAME=$1
PASSWORD=$2
LIGHTWAVE_DOMAIN=$3


OLD_MGMT_ENDPOINT=192.168.114.11
OLD_MGMT_LOADBALANCER_PORT=9000
NEW_MGMT_LOADBALANCER_PORT=9001

# Change path to the new and old CLIs.
NEW_PHOTON_CLI=photon
OLD_PHOTON_CLI=photon


# Get Load balancer IP from  swarm multi-host setup.
LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy) || true

if [ "${LOAD_BALANCER_IP}TEST" ==  "TEST" ]; then
  # Get Load balancer IP from docker-machine if this is one VM setup.
  LOAD_BALANCER_IP=$(docker-machine ip vm-0) || true
fi

if [ "${LOAD_BALANCER_IP}TEST" ==  "TEST" ]; then
  # User localhost as load balancer if this is a local setup running all containers locally without a VM.
  LOAD_BALANCER_IP=127.0.0.1
fi

# 1. Pause the back ground tasks in NEW Photon Controller deployment
$NEW_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
$NEW_PHOTON_CLI target login --username ${USERNAME}@${LIGHTWAVE_DOMAIN} --password "$PASSWORD"
$NEW_PHOTON_CLI deployment pause-background-tasks default

# 2. Start migration from OLD deployment to NEW deployment
$NEW_PHOTON_CLI system migration prepare https://$OLD_MGMT_ENDPOINT:19000/core/node-groups/default

# 3. Wait for at-least 1 migration cycle to complete.
migrated_cycles=$($NEW_PHOTON_CLI deployment show | grep "Completed data migration cycles" | cut -f2 -d ':')
total_attempts=50
attempts=0
while [ $attempts -lt $total_attempts ] && [ $migrated_cycles -eq 0 ]; do
  migrated_cycles=$($NEW_PHOTON_CLI deployment show | grep "Completed data migration cycles" | cut -f2 -d ':')
  echo "Waiting for migration to complete (attempt $attempts/$total_attempts), will try again."
  attempts=$[$attempts+1]
  sleep 30
done

# 4. Pause the OLD deployment
$OLD_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$OLD_MGMT_LOADBALANCER_PORT -c
$OLD_PHOTON_CLI target login --username ${USERNAME}@${LIGHTWAVE_DOMAIN} --password "$PASSWORD"
$OLD_PHOTON_CLI deployment pause

# 5. Finalize the migration and resume the NEW deployment
$NEW_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
$NEW_PHOTON_CLI system migration finalize https://$OLD_MGMT_ENDPOINT:19000/core/node-groups/default
$NEW_PHOTON_CLI deployment resume
