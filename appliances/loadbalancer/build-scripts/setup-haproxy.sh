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


# create self signed cert
if [ ! -f /etc/ssl/private/photon-platform.pem ]
then
  mkdir -p /etc/ssl/private
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/photon-haproxy.key -out /tmp/photon-haproxy.crt -subj "/C=US/ST=WA/L=Bellevue/O=Vmware/OU=Photon-Platform/CN=photon-platform.vmware.com"
  cat /tmp/photon-haproxy.crt /tmp/photon-haproxy.key > /etc/ssl/private/photon-haproxy.pem
fi

# install haproxy
tdnf install -y haproxy
groupadd haproxy
useradd -g haproxy haproxy
mkdir /var/lib/haproxy

# enabled configure guest to build final config file
systemctl enable configure-guest

# install pystache for config file editing
wget https://bootstrap.pypa.io/get-pip.py
python get-pip.py
rm -rf get-pip.py
pip install pystache
