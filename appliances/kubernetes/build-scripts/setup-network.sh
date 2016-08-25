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

# It may seem unwise to disable iptables, but as best we know it's
# required. Containers can run on random ports on the nodes, and
# services can be exposed on random ports on the master.
systemctl stop iptables; systemctl disable iptables

hostnamectl set-hostname kubernetes

# If we don't reset the machine-id, systemd will bring up the system
# with the same IP address it goes via DHCP when we made the VM. We
# can't reset the machine-id because each VM needs a unique IP
# address. If we make it an empty file, then systemd will regenerate
# the machine-id and refresh the DHCP IP address when the VM is
# restarted.
rm /etc/machine-id
touch /etc/machine-id
