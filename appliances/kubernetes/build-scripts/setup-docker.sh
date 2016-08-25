#!/bin/bash -xe
# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

# The docker.service file in Photon has an ExecStart line with a continuation.
# This breaks docker-multinode, which will edit this file on the when we start
# up flannel. We fix this by joining lines that end with backslash.
DS=/usr/lib/systemd/system/docker.service
awk '{if (sub(/\\$/,"")) printf "%s", $0; else print $0}' $DS > $DS.new
mv $DS.new $DS

systemctl daemon-reload
systemctl enable docker
systemctl start docker
sleep 5 # Wait for docker to start

# Create the bootstrap docker so we can pull the etcd and flannel
# images into it, instead of the regular docker
cd /root/docker-multinode
source common.sh
kube::multinode::main
kube::bootstrap::bootstrap_daemon
sleep 5 # Wait for docker to start
docker ${BOOTSTRAP_DOCKER_PARAM} pull gcr.io/google_containers/etcd-amd64:2.2.5
docker ${BOOTSTRAP_DOCKER_PARAM} pull gcr.io/google_containers/flannel-amd64:0.5.5

# Pull the Kubernetes hyperkube container, which is how we'll deploy Kubernetes
docker pull gcr.io/google_containers/hyperkube-amd64:v1.3.5

# Pull the containers that Kubernetes needs
docker pull gcr.io/google_containers/pause-amd64:3.0

# Pull the containers to provide the Kubernetes add-ons (DNS & UI)
# We extratced the versions from a running version of Kubernetes 1.3.5
# with hyperkube (Look in /etc/kubernetes)
docker pull gcr.io/google-containers/kube-addon-manager-amd64:v4
docker pull gcr.io/google_containers/kubernetes-dashboard-amd64:v1.1.1
docker pull gcr.io/google_containers/kubedns-amd64:1.5
docker pull gcr.io/google_containers/kube-dnsmasq-amd64:1.3
docker pull gcr.io/google_containers/exechealthz-amd64:1.1 # For DNS

# Now we create the hyperkube container so we can copy its configuration
# We'll edit the etcd configuration when we bring up Kubernetes at run-time
docker run gcr.io/google_containers/hyperkube-amd64:v1.3.5 /bin/true
ID=`docker ps -a -q`
docker cp $ID:/etc/kubernetes /etc/kubernetes
docker rm -f $ID
