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

systemctl daemon-reload
systemctl enable docker
systemctl start docker
sleep 5 # Wait for docker to start

# Install docker-compose. It is needed to build the container images and run the Harbor containers.
wget https://github.com/docker/compose/releases/download/1.8.0/docker-compose-Linux-x86_64
chmod +x docker-compose-Linux-x86_64
mv docker-compose-Linux-x86_64 /usr/bin/docker-compose

# Install harbor
wget https://github.com/vmware/harbor/releases/download/0.3.0/harbor-0.3.0.tgz
tar -xzvf harbor-0.3.0.tgz
cd harbor
./prepare
docker-compose pull
docker-compose build
