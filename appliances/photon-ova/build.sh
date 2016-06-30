#!/bin/bash -xe

source ../scripts/http.sh

# we are defaulting to PhotonOS version 1.0 full iso
PHOTON_ISO_URL=${ISO_URL:="https://bintray.com/artifact/download/vmware/photon/photon-1.0-13c08b6.iso"}
PHOTON_IMAGE_NAME=`basename $PHOTON_ISO_URL`

rm -f $PHOTON_IMAGE_NAME
download_file $PHOTON_ISO_URL

PHOTON_ISO_SHA1=`sha1sum $PHOTON_IMAGE_NAME | cut -d' ' -f1`

packer build -force \
	-var "photon_iso_url=file://localhost/`pwd`/$PHOTON_IMAGE_NAME" \
	-var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
	photon-ova.json
