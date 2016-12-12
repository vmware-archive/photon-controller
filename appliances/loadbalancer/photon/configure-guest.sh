#!/bin/bash
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
#
# This script runs on first boot up of the ova to confgigure the system.
XML_FILE=configovf.xml

function set_ntp_servers() {

  if [ -z "$ntp_servers" ]
  then
     echo "No ntp_servers."
     return
  fi

  # make an array using , as the separator
  IFS=','
  read -a ntp_servers_arr <<< "$ntp_servers"

  # trim the spaces
  IFS=' '
  ntp_servers_arr=(${ntp_servers_arr[@]})

  unset IFS

  cat > "/etc/systemd/timesyncd.conf" << EOF
NTP=${ntp_servers_arr[@]}

EOF

  systemctl daemon-reload
  systemctl restart systemd-timesyncd
}

function mask2cidr() {
    bits=0
    IFS=.
    for dig in $netmask0 ; do
        case $dig in
            255) let bits+=8;;
            254) let bits+=7 ; break ;;
            252) let bits+=6 ; break ;;
            248) let bits+=5 ; break ;;
            240) let bits+=4 ; break ;;
            224) let bits+=3 ; break ;;
            192) let bits+=2 ; break ;;
            128) let bits+=1 ; break ;;
            0);;
            *) echo "Error: $dig is not correct"; exit -1
        esac
    done
    unset IFS
    echo "$bits"
}

function set_network_properties(){
  if [ -z "$dns" ]
  then
    multiline_dns=""
  else

    # convert to array using , as seperator
    IFS=','
    read -a dns_arr <<< "$dns"

    #add DNS= to the beginning
    len=${#dns_arr[@]}
    for ((i=0;i<len;i++)); do
      dns_entry=$(echo "${dns_arr[i]}" | sed 's/^[[:blank:]]*//')
      dns_arr[i]="DNS=${dns_entry}"
    done

    #make it multiline
    multiline_dns=$(IFS=$'\n'; echo "${dns_arr[*]}")

    unset IFS
  fi

  rm -rf /etc/systemd/network/10-dhcp-en.network

  if [ -z "$ip0" ] || [ -z "$netmask0" ] || [ -z "$gateway" ]
  then
    echo "Using DHCP"
    nwConfig="DHCP=yes"
  else
    nwConfig=$(cat <<EOF
[Address]
Address=${ip0}/$(mask2cidr)
EOF
)
  fi

  echo "Setting Network properties"

  en_name=$(ip addr show label "e*" | head -n 1 | sed 's/^[0-9]*: \(e.*\): .*/\1/')

  cat > "/etc/systemd/network/10-dhcp-${en_name}.network" << EOF
[Match]
Name=$en_name

[Network]
$multiline_dns

$nwConfig

[Route]
Gateway=${gateway}
EOF

  systemctl restart systemd-networkd
}

function set_root_password(){
  if [ -z "$root_password" ]
  then
     echo "No root_password."
     return
  fi

  echo "root:${root_password}" | chpasswd
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "password setting failed: $* with $exit_code"
    exit $exit_code
  fi
}

function set_photon_password(){
  if [ -z "$photon_password" ]
  then
     echo "No photon_password."
     return
  fi

  echo "photon:${photon_password}" | chpasswd
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "password setting failed: $* with $exit_code"
    exit $exit_code
  fi
}

function configure_loadbalancer() {
  # convert to array using , as seperator
  IFS=','
  read -a node_arr <<< "$lb_hosts"
  len=${#node_arr[@]}
  ui_nodes=""
  api_nodes=""
  for ((i=0;i<len;i++))
  do
    if [ ! -z "${ui_nodes}" ]; then
      ui_nodes=${ui_nodes}","
      api_nodes=${api_nodes}","
    fi
    ui_nodes=${ui_nodes}" { \"serverName\": \"ui-${node_arr[i]}\", \"serverAddress\" : \"${node_arr[i]}\" } "
    api_nodes=${api_nodes}" { \"serverName\": \"api-${node_arr[i]}\", \"serverAddress\" : \"${node_arr[i]}\" } "
  done
  ui_nodes="[ ${ui_nodes} ]"
  api_nodes="[ ${api_nodes} ]"

  context="{\
    \"UI_SERVERS\" : ${ui_nodes}, \
    \"API_SERVERS\" : ${api_nodes} \
  }"
  content=`cat /etc/photon/haproxy.cfg.template`
  pystache "$content" "$context" > /etc/haproxy/haproxy.cfg

  # start loadbalancer to pick up new config
  systemctl start haproxy.service
}

function parse_ovf_env() {
  # vm config
  ip0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ip0']/../@*[local-name()='value'])")
  netmask0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='netmask0']/../@*[local-name()='value'])")
  gateway=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='gateway']/../@*[local-name()='value'])")
  dns=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='DNS']/../@*[local-name()='value'])")
  ntp_servers=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ntp_servers']/../@*[local-name()='value'])")

  # users
  root_password=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='root_password']/../@*[local-name()='value'])")
  photon_password=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='photon_password']/../@*[local-name()='value'])")

  # loadbalancer config
  lb_hosts=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='lb_hosts']/../@*[local-name()='value'])") # 1.1.1.1,1.1.1.2

  if [ -z "$lb_hosts" ]; then
    missing_values = ${missing_values}", lb_hosts"
  fi
  if [ ! -z "$missing_values" ]; then
    echo $missing_values
    exit -1
  fi
}

set +e
# Get env variables set in this OVF thru properties
ovf_env=$(vmtoolsd --cmd 'info-get guestinfo.ovfEnv')
if [ ! -z "${ovf_env}" ]; then
  # remove passwords from guestinfo.ovfEnv
  vmtoolsd --cmd "info-set guestinfo.ovfEnv `vmtoolsd --cmd 'info-get guestinfo.ovfEnv' | grep -v password`"
  # this file needs to be deleted since it contains passwords
  echo "$ovf_env" > $XML_FILE
  parse_ovf_env

  set_ntp_servers
  set_network_properties
  set_root_password
  set_photon_password
  configure_loadbalancer

  # the XML file contains passwords
  rm -rf $XML_FILE
fi
set -e

#remove itself from startup
systemctl disable configure-guest
