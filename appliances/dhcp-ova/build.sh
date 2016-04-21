#!/bin/bash -xe

PHOTON_OVA_URL=${PHOTON_OVA_URL:="../photon-ova/build/photon-ova-virtualbox.ova"}
DNSMASQ_URL=${DNSMASQ_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/dnsmasq-v2-75/dnsmasq"}
DNSMASQ_DHCP_RELEASE_URL=${DNSMASQ_DHCP_RELEASE_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/dnsmasq-v2-75/dhcp_release"}

packer build -force \
	-var "photon_ova_url=$PHOTON_OVA_URL" \
	-var "dnsmasq_url=$DNSMASQ_URL" \
	-var "dnsmasq_dhcp_release_url=$DNSMASQ_DHCP_RELEASE_URL" \
	dhcp-ova.json

cd build
../to-vmware-ovf.sh "dhcp-ova-virtualbox" "dhcp-ova"
cd ..
