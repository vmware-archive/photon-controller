#!/bin/bash -xe

source ../scripts/http.sh

# Download ovftool if it's not available on the system and if it doesn't exist in the current dir
if ! hash ovftool 2> /dev/null && [ ! -d ovftool ]; then
  printf "Downloading ovftool"
  ovftoolURL="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/VMware-ovftool-4.0.0-1944234-lin.x86_64.zip"
  if [ "$(uname)" == "Darwin" ]; then
    ovftoolURL="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/VMware-ovftool-4.0.0-1944234-mac.x64.zip"
  fi
  download_file $ovftoolURL
  unzip -q -o VMware-ovftool*
fi
# If local ovftool exists, put it on the path
if [ -d ovftool ]; then
  export PATH=$(pwd)/ovftool:$PATH
fi

SSHD_ENABLE_ROOT_LOGIN=${SSHD_ENABLE_ROOT_LOGIN:-"false"}
PHOTON_OVA_URL=${PHOTON_OVA_URL:="../photon-ova/build/photon-ova-virtualbox.ova"}
DNSMASQ_URL=${DNSMASQ_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/dnsmasq-v2-75/dnsmasq"}
DNSMASQ_DHCP_RELEASE_URL=${DNSMASQ_DHCP_RELEASE_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/dnsmasq-v2-75/dhcp_release"}

packer build -force \
	-var "photon_ova_url=$PHOTON_OVA_URL" \
	-var "dnsmasq_url=$DNSMASQ_URL" \
	-var "dnsmasq_dhcp_release_url=$DNSMASQ_DHCP_RELEASE_URL" \
	-var "sshd_enable_root_login=$SSHD_ENABLE_ROOT_LOGIN" \
        dhcp-ova.json

cd build
../to-vmware-ovf.sh "dhcp-ova-virtualbox" "dhcp-ova"
cd ..
