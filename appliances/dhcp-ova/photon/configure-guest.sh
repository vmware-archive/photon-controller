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

function mask2cidr()
{
    local netmask=$1
    bits=0
    IFS=.
    for dig in $netmask ; do
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
            *) echo "Error: $dig is not correct"; exit 1
        esac
    done
    unset IFS
    echo "$bits"
}

function set_network_properties()
{
  rm -rf /etc/systemd/network/*.network

  echo "Setting Network properties"

  en_names=$(ip addr show label "e*" | grep mtu | awk -F ':' '{ print $2; }')

  en_idx=0

  for en_name in $en_names
  do
     case $en_idx in
        0)
         configure_nic $en_name $ip0 $netmask0 $gateway0
         ;;
        1)
         configure_nic $en_name $ip1 $netmask1 $gateway1 
         ;;
        *)
         echo "Error: Unexpected number of network interfaces"
         exit 1
         ;;
     esac
     (( en_idx=en_idx + 1 ))
  done

  systemctl restart systemd-networkd
}

function configure_nic()
{
  local interface=$1
  local ip=$2
  local netmask=$3
  local gateway=$4

  cat > "/etc/systemd/network/10-static-$interface.network" <<-EOF
	[Match]
	Name=$interface
	
	[Address]
	Address=$ip/$(mask2cidr $netmask)
	
	[Route]
	Gateway=$gateway
	EOF
}

function set_root_password()
{
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

function set_dhcp_config()
{
  cat > "/etc/dnsmasq.conf" <<-EOF
	dhcp-range=192.168.0.0,static
	EOF
}

function parse_ovf_env() {
  # vm config
  ip0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ip0']/../@*[local-name()='value'])")
  netmask0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='netmask0']/../@*[local-name()='value'])")
  gateway0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='gateway0']/../@*[local-name()='value'])")
  ip1=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ip1']/../@*[local-name()='value'])")
  netmask1=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='netmask1']/../@*[local-name()='value'])")
  gateway1=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='gateway1']/../@*[local-name()='value'])")

  # users
  root_password=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='root_password']/../@*[local-name()='value'])")

  missing_values=0
  if [ -z "$ip0" ]
  then
     echo "Missing ip0"
     missing_values=1
  fi
  if [ -z "$netmask0" ]
  then
     echo "Missing netmask0"
     missing_values=1
  fi
  if [ -z "$gateway0" ]
  then
     echo "Missing gateway0"
     missing_values=1
  fi
  if [ -z "$ip1" ]
  then
     echo "Missing ip1"
     missing_values=1
  fi
  if [ -z "$netmask1" ]
  then
     echo "Missing netmask1"
     missing_values=1
  fi
  if [ -z "$gateway1" ]
  then
     echo "Missing gateway1"
     missing_values=1
  fi

  if [ $missing_values -ne 0 ]
  then
     echo "Some configuration parameters are missing"
     exit 1
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

  set_network_properties
  set_root_password
  set_dhcp_config

  # the XML file contains passwords
  rm -rf $XML_FILE
fi
set -e



#remove itself from startup
systemctl disable configure-guest
