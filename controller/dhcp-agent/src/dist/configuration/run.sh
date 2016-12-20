#!/bin/bash
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

en_name=$(ip addr show label "en*" | head -n 1 | sed 's/^[0-9]*: \(en.*\): .*/\1/')
container_ip=$(ifconfig $en_name | grep 'inet addr:' | cut -d: -f2 | awk '{ print $1 }')

CONFIG_PATH="/etc/esxcloud"
DHCP_AGENT_BIN="{{{DHCP-AGENT_INSTALL_DIRECTORY}}}/bin"
DHCP_AGENT_CONFIG="$CONFIG_PATH/dhcp-agent.yml"

#
# Add hosts entry to allow InetAddress.getLocalHost() to
# succeed when running container with --net=host
#
myhostname="$(hostname)"
if [ -z "$(grep $myhostname /etc/hosts)" ]
then
  echo "$container_ip     $myhostname" >> /etc/hosts
fi

# jvm heap size will be set to by default is 128m
jvm_mem=128

{{#memoryMb}}
jvm_mem=$(({{{memoryMb}}}/2))
{{/memoryMb}}

export JAVA_OPTS="-Xmx${jvm_mem}m -Xms${jvm_mem}m -XX:+UseConcMarkSweepGC {{{JAVA_DEBUG}}}"

#
# Start service
#
$DHCP_AGENT_BIN/dhcp-agent $DHCP_AGENT_CONFIG
