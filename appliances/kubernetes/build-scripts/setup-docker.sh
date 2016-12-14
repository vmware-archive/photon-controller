#!/bin/bash -xe
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.

# The argument is always set through build-kubernetes-stem-cell. We expect it
# to be the version number. We modify kubernetes_version as Kubernetes
# expects the format to be v{version number}.
kubernetes_version="v$1"

# The docker.service file in Photon has an ExecStart line with a continuation.
# This breaks docker-multinode, which will edit this file on the when we start
# up flannel. We fix this by joining lines that end with backslash.
DS=/usr/lib/systemd/system/docker.service
awk '{if (sub(/\\$/,"")) printf "%s", $0; else print $0}' $DS > $DS.new
mv $DS.new $DS
# When a node restarts, we want the bootstrap docker to come back up first for
# flannel to start before we bring up Kubernetes services in the normal docker
# service. We resolve this by requiring the bootstrap docker service to start
# before the docker service.
sed -i s/Requires.*/"Requires=docker-containerd.service docker-bootstrap.service"/ $DS

systemctl daemon-reload
systemctl enable docker-bootstrap
systemctl start docker-bootstrap
sleep 5 # Wait for docker-bootstrap to start
# We assume docker-bootstrap starts successfully.

systemctl enable docker
systemctl start docker
sleep 5 # Wait for docker to start

# Pull the etcd and flannel images into bootstrap docker, instead of the regular docker
cd /root/docker-multinode
source common.sh
kube::multinode::main
docker ${BOOTSTRAP_DOCKER_PARAM} pull gcr.io/google_containers/etcd-amd64:3.0.4
docker ${BOOTSTRAP_DOCKER_PARAM} pull quay.io/coreos/flannel:v0.6.1-amd64

# Pull the Kubernetes hyperkube container, which is how we'll deploy Kubernetes
docker pull gcr.io/google_containers/hyperkube-amd64:$kubernetes_version

# Pull the containers that Kubernetes needs
docker pull gcr.io/google_containers/pause-amd64:3.0

# Pull the containers to provide the Kubernetes add-ons (DNS & UI)
# We extracted the versions from a running version of Kubernetes 1.5.1
# with hyperkube (Look in /etc/kubernetes)
docker pull gcr.io/google_containers/kube-addon-manager-amd64:v6.1
docker pull gcr.io/google_containers/kubernetes-dashboard-amd64:v1.5.0
docker pull gcr.io/google_containers/kubedns-amd64:1.9
docker pull gcr.io/google_containers/kube-dnsmasq-amd64:1.4
docker pull gcr.io/google_containers/dnsmasq-metrics-amd64:1.0
docker pull gcr.io/google_containers/exechealthz-amd64:1.2 # For DNS

# Now we create the hyperkube container so we can copy its configuration
# We'll edit the etcd configuration when we bring up Kubernetes at run-time
docker run gcr.io/google_containers/hyperkube-amd64:$kubernetes_version /bin/true
ID=`docker ps -a -q`
docker cp $ID:/etc/kubernetes /etc/kubernetes
docker rm -f $ID

# Write environment variables to a file, allowing it to be sourced later
echo "export K8S_VERSION=${kubernetes_version}" >> /root/env_variables.txt
