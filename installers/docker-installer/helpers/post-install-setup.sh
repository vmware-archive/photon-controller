#!/bin/bash +xe

LOAD_BALANCER_IP=$1
USERNAME=$2
PASSWORD=$3
LIGHTWAVE_DOMAIN=$4

photon target set https://$LOAD_BALANCER_IP:9000 -c > /dev/null
photon target login --username ${USERNAME}@${LIGHTWAVE_DOMAIN} --password ${PASSWORD} > /dev/null

echo "Creating Tenant (cloud)..."
photon -n tenant create cloud > /dev/null
photon tenant set cloud > /dev/null

echo "Creating Resource Tickets..."
photon -n resource-ticket create --name cloud-resources --limits "vm.memory 2000 GB, vm 1000 COUNT" > /dev/null

echo "Creating Project (cloud-staging)..."

photon -n project create --resource-ticket cloud-resources --name cloud-staging --limits "vm.memory 1000 GB, vm 500 COUNT" > /dev/null
photon -n project set cloud-staging > /dev/null

echo "Creating Flavors (vm-small, vm-medium, vm-large)..."
photon -n flavor create --name "vm-small" --kind "vm" --cost "vm 1.0 COUNT, vm.cpu 1.0 COUNT, vm.memory 2.0 GB" > /dev/null
photon -n flavor create --name "cloud-disk" --kind "ephemeral-disk" --cost "ephemeral-disk 1.0 COUNT" > /dev/null
photon -n flavor create --name "vm-medium" --kind "vm" --cost "vm 1.0 COUNT, vm.cpu 2.0 COUNT, vm.memory 4.0 GB" > /dev/null
photon -n flavor create --name "vm-large" --kind "vm" --cost "vm 1.0 COUNT, vm.cpu 4.0 COUNT, vm.memory 8.0 GB" > /dev/null
