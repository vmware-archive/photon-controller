#!/bin/bash -xe

XML_FILE=configovf.xml

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

function set_admin_password(){
  if [ -z "$admin_password" ]
  then
     echo "No admin_password."
     return
  fi

  echo "root:${admin_password}" | chpasswd
  exit_code=$?
  if [ 0 -ne $exit_code ]
  then
    echo "password setting failed: $* with $exit_code"
    exit $exit_code
  fi
}

function set_dhcp_conf(){
  if [ -z "$dhcp_range" ]
  then
     echo "No dhcp range, not configuring DHCP."
     return
  fi

  dhcp_conf=$dhcp_range
  if [ ! -z "$dhcp_lease_expiry" ]
  then
    dhcp_conf="${dhcp_conf},${dhcp_lease_expiry}"
  fi

  sed -i "s/# dhcp-range=192.168.0.50,192.168.0.150,24h/dhcp-range=${dhcp_conf}/g" /etc/bmp/dnsmasq.conf

  #override the gateway, otherwise it will be the DHCP vm itself
  if [ ! -z "$gateway" ]
  then
    sed -i "s/#dhcp-option=3,1.2.3.4/dhcp-option=3,${gateway}/g" /etc/bmp/dnsmasq.conf
  fi
  if [ ! -z "$pxe_image_files" ]
  then
    sed -i "s@#esxli network vswitch standard portgroup set -p \"Management Network\" --vlan-id 100@esxli network vswitch standard portgroup set -p \"Management Network\" --vlan-id ${vlan_on_hosts}@g" /etc/bmp/ks.cfg
  fi

  if [ ! -z "$vlan_on_hosts" ]
  then
    sed -i "s@prefix=http.*@prefix=${pxe_image_files}@g" /etc/bmp/boot.cfg
  fi
}

function set_esxboot_file_path(){
  # adding vms ip address to the configuration files
  HOST_IP=`ifconfig | grep -a1 eth0 | grep inet | awk '{print $2}' | awk -F\: '{print $2}'`
  ## modify dhcp config
  ## modify apache config
  ## modify boot.cfg
  sed -i "s/HOST_IP/${HOST_IP}/g" /etc/bmp/boot.cfg
  ## modify ipxe.tmpl
  sed -i "s/HOST_IP/${HOST_IP}/g" /etc/bmp/ipxe.tmpl
}

function parse_ovf_env(){
  ip0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='ip0']/../@*[local-name()='value'])")
  netmask0=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='netmask0']/../@*[local-name()='value'])")
  gateway=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='gateway']/../@*[local-name()='value'])")
  dns=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='DNS']/../@*[local-name()='value'])")
  admin_password=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='admin_password']/../@*[local-name()='value'])")
  enable_syslog=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='enable_syslog']/../@*[local-name()='value'])")
  syslog_endpoint=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='syslog_endpoint']/../@*[local-name()='value'])")
  dhcp_range=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='dhcp_range']/../@*[local-name()='value'])")
  dhcp_lease_expiry=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='dhcp_lease_expiry']/../@*[local-name()='value'])")
  pxe_image_files=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='pxe_image_files']/../@*[local-name()='value'])")
  vlan_on_hosts=$(xmllint $XML_FILE --xpath "string(//*/@*[local-name()='key' and .='vlan_on_hosts']/../@*[local-name()='value'])")

  echo "ip0" "$ip0"
  echo "gateway" "$gateway"
  echo "netmask0" "$netmask0"
  echo "dns" "$dns"
  echo "ntpservers" "$ntp_servers"
  echo "admin_password" "$admin_password"
  echo "enable_syslog" "$enable_syslog"
  echo "syslog_endpoint" "$syslog_endpoint"
  echo "dhcp_range" "$dhcp_range"
  echo "dhcp_lease_expiry" "$dhcp_lease_expiry"
  echo "pxe_image_files" "$pxe_image_files"
  echo "vlan_on_hosts" "$vlan_on_hosts"
}

set +e
# Get env variables set in this OVF thru properties
ovf_env=$(vmtoolsd --cmd 'info-get guestinfo.ovfEnv')
echo "$ovf_env"
if [ -z "$ovf_env" ]
then
  echo "No ovf_env variables, nothing to do"
  # Give default value so that YML file is still valid
  syslog_endpoint="127.0.0.1"
else
  echo "$ovf_env" > $XML_FILE
  parse_ovf_env
fi
set -e

set_network_properties
set_admin_password

set_dhcp_conf
set_esxboot_file_path

systemctl restart dnsmasq

#remove itself from startup
systemctl disable configure-guest
