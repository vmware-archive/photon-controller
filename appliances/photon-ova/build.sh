#!/bin/bash -xe

PHOTON_ISO_URL=${ISO_URL:="https://bintray.com/artifact/download/vmware/photon/photon-1.0-37d64ad.iso"}
PHOTON_ISO_SHA1=${ISO_SHA1:="aff58f10855db6f9f5fae62641816044dd459ffa"}

packer build -force \
	-var "photon_iso_url=$PHOTON_ISO_URL" \
	-var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
	photon-ova.json
