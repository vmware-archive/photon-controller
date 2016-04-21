#!/bin/bash -xe

PHOTON_ISO_URL=${ISO_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/userContent/bmp/photon-1.0-0185afd.iso"}
PHOTON_ISO_SHA1=${ISO_SHA1:="6cc1c646677ff8b8b48570b75286e496c85790f8"}

packer build -force \
	-var "photon_iso_url=$PHOTON_ISO_URL" \
	-var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
	photon-ova.json
