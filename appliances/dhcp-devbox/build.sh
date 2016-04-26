#!/bin/bash -xe

DHCP_OVA_URL=${DHCP_OVA_URL:="../dhcp-ova/build/dhcp-ova-virtualbox.ova"}

function getHostOnlyInterfaceName() {
	# Attempts to get name of first hostonly network adapter
	# Take note of stderr redirect to /dev/null, which prevents warnings in stderr from
	# breaking the parsing of the output.
	hostonlyif=$(VBoxManage list hostonlyifs 2> /dev/null | head -n 1 | awk '{print $2}')
}

getHostOnlyInterfaceName
if [[ "$hostonlyif" == "" ]]; then
	VBoxManage hostonlyif create
	getHostOnlyInterfaceName
	if [[ "$hostonlyif" == "" ]]; then
		echo "Unable to find or create hostonly network adapter in VirtualBox"
		exit 1
	fi
fi

packer build -force \
	-var "dhcp_ova_url=$DHCP_OVA_URL" \
	-var "hostonly_adapter_name=$hostonlyif" \
        dhcp-devbox.json
