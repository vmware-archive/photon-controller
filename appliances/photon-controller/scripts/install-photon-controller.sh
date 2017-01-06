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

echo "installing photon-controller"

# install photon-controller
# the JRE/JDK version needs to match the version used by the lightwave client
# otherwise we will be installing the JRE twice.
# we require the JDK for keystoreutlis.
tdnf install -y openjdk-1.8.0.102
tdnf install -y openjre-1.8.0.102
tdnf install -y sshpass
mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default

rpm -i /tmp/photon-controller*.rpm

# install lightwave client
echo "installing lightwave client"
mkdir -p /var/run/sshd; chmod -rx /var/run/sshd
rm -rf /etc/ssh/ssh_host_rsa_key
ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key

# configure journald
sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf

# install lightwave
tdnf install -y procps-ng
tdnf install -y likewise-open
tdnf install -y boost
tdnf install -y vmware-lightwave-clients

# open iptables ports
# open lightwave client ports
echo "iptables -I INPUT -p tcp --dport 22 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p udp --dport 53 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 53 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p udp --dport 88 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 88 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 389 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 443 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 636 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2012 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2014 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 2020 -j ACCEPT" >> /etc/systemd/scripts/iptables
# open photon-controller ports
echo "iptables -I INPUT -p tcp --dport 9000 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 19000 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 20001 -j ACCEPT" >> /etc/systemd/scripts/iptables
