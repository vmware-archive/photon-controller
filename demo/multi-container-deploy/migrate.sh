#!/bin/bash -xe

OLD_MGMT_ENDPOINT=192.168.114.11
OLD_MGMT_LOADBALANCER_PORT=28080
NEW_MGMT_LOADBALANCER_PORT=28090
LOAD_BALANCER_IP=$(docker inspect --format '{{ .Node.IP }}' haproxy)

# Pause the NEW deployment and start migration
#photon target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
#photon target login --username photon@photon.local --password Photon123$
#photon deployment pause-background-tasks default
#photon system migration prepare https://$OLD_MGMT_ENDPOINT:19000/core/node-groups/default
#photon deployment show default

#sleep 240

# Pause the old deployment
photon target set https://$LOAD_BALANCER_IP:$OLD_MGMT_LOADBALANCER_PORT -c
photon target login --username photon@photon.local --password Photon123$
photon deployment pause

# Finalize the migration and resume the NEW deployment
photon target set https://$LOAD_BALANCER_IP:$NEW_MGMT_LOADBALANCER_PORT -c
photon system migration finalize https://$OLD_MGMT_ENDPOINT:19000/core/node-groups/default
photon deployment resume
