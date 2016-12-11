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

# Open Haproxy ports
echo "iptables -I INPUT -p tcp --dport 443 -j ACCEPT" >> /etc/systemd/scripts/iptables
echo "iptables -I INPUT -p tcp --dport 4343 -j ACCEPT" >> /etc/systemd/scripts/iptables

# Set hostname
hostnamectl set-hostname haproxy
