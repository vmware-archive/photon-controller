#!/bin/bash -xe

# This script demonstrate upgrading from OLD deployment to NEW deployment.

OLD_MGMT_ENDPOINT=192.168.114.11
OLD_MGMT_LOADBALANCER_PORT=9000
NEW_MGMT_LOADBALANCER_PORT=9001
LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)

# Change path to the new and old CLIs.
NEW_PHOTON_CLI=photon
OLD_PHOTON_CLI=photon

# 1. Pause the back ground tasks in NEW Photon Controller deployment
$NEW_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
$NEW_PHOTON_CLI target login --username photon@photon.local --password Photon123$
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
  sleep 100
done

# 4. Pause the OLD deployment
$OLD_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$OLD_MGMT_LOADBALANCER_PORT -c
$OLD_PHOTON_CLI target login --username photon@photon.local --password Photon123$
$OLD_PHOTON_CLI deployment pause

# 5. Finalize the migration and resume the NEW deployment
$NEW_PHOTON_CLI target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
$NEW_PHOTON_CLI system migration finalize https://$OLD_MGMT_ENDPOINT:19000/core/node-groups/default
$NEW_PHOTON_CLI deployment resume
