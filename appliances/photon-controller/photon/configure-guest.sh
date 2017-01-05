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
 
#
# This script is run during first start up to configure the vm.
#

CONFIG_XML_FILE=configovf.xml

function cleanup()
{
    rm -rf $CONFIG_XML_FILE

    #remove itself from startup
    systemctl disable configure-guest
}

function set_ntp_servers()
{
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

  cat > "/etc/systemd/timesyncd.conf" <<-EOF
	NTP=${ntp_servers_arr[@]}
	EOF

  systemctl daemon-reload
  systemctl restart systemd-timesyncd
}

function mask2cidr()
{
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

function set_network_properties()
{
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
    nwConfig=$(cat <<-EOF
	[Address]
	Address=${ip0}/$(mask2cidr)
	EOF
    )
  fi

  echo "Setting Network properties"

  en_name=$(ip addr show label "e*" | \
            head -n 1 | \
            sed 's/^[0-9]*: \(e.*\): .*/\1/')

  cat > "/etc/systemd/network/10-dhcp-${en_name}.network" <<-EOF
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

function set_root_password()
{
  if [ -z "$root_password" ]
  then
     echo "No root_password."
     return
  fi

  echo -e "${root_password}\n${root_password}" | passwd
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "password setting failed: $* with $exit_code"
    exit $exit_code
  fi
}

function set_photon_password()
{
  if [ -z "$photon_password" ]
  then
     echo "No photon_password."
     return
  fi

  echo -e "${photon_password}\n${photon_password}" | passwd photon
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "password setting failed: $* with $exit_code"
    exit $exit_code
  fi
}

function configure_lightwave()
{
  echo "Starting required services"

  systemctl start lwsmd

  echo "Joining system to Lightwave domain $lw_domain"

  exec 3<<<"$lw_password"

  /opt/vmware/bin/ic-join --domain $lw_domain \
                          --ssl-subject-alt-name $ip0 \
                          <&3
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "Failed to join Lightwave domain $lw_domain with $exit_code"
    exit $exit_code
  fi

  lw_host=`/opt/vmware/bin/vmafd-cli get-dc-name --server-name localhost`
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "Failed to get Lightwave Domain Controller. Exit code; $exit_code"
    exit $exit_code
  fi
}

function configure_photon()
{
  pc_auth_enabled="true"
  pc_enabled_syslog="true"

  if [ -z "$pc_syslog_endpoint" ]
  then
    pc_enabled_syslog="false"
  fi

  pc_peer_nodes="{\"${ip0}\" : \"19000\"}"

  # convert to array using , as seperator
  IFS=','
  read -a node_arr <<< "$pc_peer_nodes_comma_seperated"
  len=${#node_arr[@]}
  pc_peer_nodes="{ \"peerAddress\" : \"${ip0}\", \"peerPort\" : 19000 }"
  for ((i=0;i<len;i++))
  do
    pc_peer_nodes=${pc_peer_nodes}", { \"peerAddress\" : \"${node_arr[i]}\", \"peerPort\" : 19000 } "
    dns_entry=$(echo "${dns_arr[i]}" | sed 's/^[[:blank:]]*//')
    dns_arr[i]="DNS=${dns_entry}"
  done
  pc_peer_nodes="[ ${pc_peer_nodes} ]"

  # compute memory available on the system
  memory_mb=`free -m | grep Mem | tr -s " " | cut -d " " -f 2`

  case "${create_default_deployment}" in
    1 | [Tt][rR][uU][eE] | [Yy][eE][sS])
        create_default_deployment="true"
        ;;
    *)
        create_default_deployment="false"
        ;;
  esac

  custom_context="{\
    \"CREATE_DEFAULT_DEPLOYMENT\" : \"${create_default_deployment}\", \
    \"REGISTRATION_ADDRESS\" : \"${ip0}\", \
    \"PHOTON_CONTROLLER_PEER_NODES\" : ${pc_peer_nodes}, \
    \"APIFE_IP\" : \"${ip0}\", \
    \"APIFE_PORT\" : 9000, \
    \"LIGHTWAVE_USERNAME\" : \"${lw_username}\", \
    \"LIGHTWAVE_PASSWORD\" : \"${lw_password}\", \
    \"LIGHTWAVE_HOSTNAME\" : \"${lw_host}\", \
    \"LIGHTWAVE_DOMAIN\" : \"${lw_domain}\", \
    \"LIGHTWAVE_SECURITY_GROUPS\" : \"${lw_domain}\\\\Administrators\", \
    \"USE_VIRTUAL_NETWORK\" : false, \
    \"ENABLE_SYSLOG\" : ${pc_enabled_syslog}, \
    \"SYSLOG_ENDPOINT\" : \"${pc_syslog_endpoint}\", \
    \"DATASTORE\" : \"datastore1\", \
    \"ENABLE_AUTH\" : ${pc_auth_enabled}, \
    \"SHARED_SECRET\" : \"${pc_secret_password}\", \
    \"AUTH_SERVER_ADDRESS\" : \"${lw_host}\", \
    \"AUTH_SERVER_PORT\" : \"${lw_port}\", \
    \"AUTH_SERVER_TENANT\" : \"${lw_domain}\", \
    \"peerPort\" : 19000, \
    \"memoryMb\" : ${memory_mb} \
  }"

  # need grab the dynamix parameters section and remove the syslog entry
  predefined_context=`jq .dynamicParameters /etc/photon/config-templates/photon-controller-core_release.json | jq 'del(.ENABLE_SYSLOG)'`

  context=`echo "${custom_context}" "${predefined_context}" | jq -s add`

  mkdir -p /etc/esxcloud
  content=`cat /etc/photon/config-templates/photon-controller-core.yml`
  pystache "$content" "$context" > /etc/esxcloud/photon-controller-core.yml

  content=`cat /etc/photon/config-templates/run.sh`
  pystache "$content" "$context" > /etc/esxcloud/run.sh

  content=`cat /etc/photon/config-templates/swagger-config.js`
  pystache "$content" "$context" > /etc/esxcloud/swagger-config.js

  # enable/start the photon-controller service
  systemctl enable photon-controller
  systemctl start photon-controller
}

function parse_ovf_env()
{
  # vm config
  ip0=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ip0']/../@*[local-name()='value'])")
  netmask0=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='netmask0']/../@*[local-name()='value'])")
  gateway=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='gateway']/../@*[local-name()='value'])")
  dns=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='DNS']/../@*[local-name()='value'])")
  ntp_servers=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ntp_servers']/../@*[local-name()='value'])")

  # users
  root_password=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='root_password']/../@*[local-name()='value'])")
  photon_password=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='photon_password']/../@*[local-name()='value'])")

  # photon Controller
  pc_syslog_endpoint=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='pc_syslog_endpoint']/../@*[local-name()='value'])")
  # host,host
  pc_peer_nodes_comma_seperated=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='pc_peer_nodes']/../@*[local-name()='value'])")
  pc_secret_password=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='pc_secret_password']/../@*[local-name()='value'])")
  create_default_deployment=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='create_default_deployment']/../@*[local-name()='value'])")

  if [ -z "$create_default_deployment" ]; then
        create_default_deployment="false"
  fi

  # lightwave config
  lw_domain=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='lw_domain']/../@*[local-name()='value'])") # some.domain.com
  lw_username=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='lw_username']/../@*[local-name()='value'])") # administrator
  lw_password=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='lw_password']/../@*[local-name()='value'])")
  lw_port=$(xmllint $CONFIG_XML_FILE --xpath "string(//*/@*[local-name()='key' and .='lw_port']/../@*[local-name()='value'])")
  pc_secret_password=$(date +%s | base64 | head -c 8)
  pc_keystore_password=$(date +%s | base64 | head -c 8)

  if [ -z "$lw_port" ]; then
    lw_port="443"
  fi

  if [ -z "$lw_username" ]; then
    lw_username="administrator"
  fi

  missing_values=0
  if [ -z "$lw_password" ]; then
    missing_values=1
    echo "Missing value [lw_password]"
  fi
  if [ -z "$lw_domain" ]; then
    missing_values=1
    echo "Missing value [lw_domain]"
  fi
  if [ $missing_values -ne 0 ]; then
    exit -1
  fi
}

#
# Main
#

trap cleanup EXIT

set +e

# Get env variables set in this OVF thru properties
ovf_env=$(vmtoolsd --cmd 'info-get guestinfo.ovfEnv')

# remove passwords from guestinfo.ovfEnv
vmtoolsd --cmd "info-set guestinfo.ovfEnv `vmtoolsd --cmd 'info-get guestinfo.ovfEnv' | grep -v password`"

# this file needs to be deleted since it contains passwords
if [ ! -z "$ovf_env" ]
then
  echo "$ovf_env" > $CONFIG_XML_FILE
  parse_ovf_env

  set_ntp_servers
  set_network_properties
  set_root_password
  set_photon_password
  configure_lightwave
  configure_photon

fi

set -e


