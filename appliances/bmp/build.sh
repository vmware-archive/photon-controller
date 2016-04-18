#!/bin/bash -xe

SSHD_ENABLE_ROOT_LOGIN=${SSHD_ENABLE_ROOT_LOGIN:-"false"}
PHOTON_VM_OVA_URL=${PHOTON_VM_OVA_URL:-"https://dl.bintray.com/vmware/photon/ova/1.0TP2/x86_64/photon-1.0TP2.ova"}
PHOTON_ISO_URL=${ISO_URL:="https://dl.bintray.com/vmware/photon/iso/1.0TP2/x86_64/photon-minimal-1.0TP2.iso"}
PHOTON_ISO_SHA1=${ISO_SHA1:="a47567368458acd8c8ef5f1e3f793c5329063dac"}
VBOX_ISO_URL=${VBOX_ISO_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/userContent/vbox/VBoxGuestAdditions_5.0.14.iso"}
VBOX_ISO_SHA256=${VBOX_ISO_SHA256:="cec0df18671adfe62a34d3810543f76f76206b212b2b61791fe026214c77507c"}

# check esxi image in folder
if [ ! -d "bmp/installer-pxe-modules" ]; then
  echo "Missing an esxi pxe image, please add your pxe boot image to [bmp/installer-pxe-modules]"
  exit -1
fi

# build the appliance ova
packer build -force \
  -var "photon_iso_url=$PHOTON_ISO_URL" \
  -var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
  -var "vbox_iso_url=$VBOX_ISO_URL" \
  -var "vbox_iso_sha256=$VBOX_ISO_SHA256" \
  -var "sshd_enable_root_login=$SSHD_ENABLE_ROOT_LOGIN" \
  -var "photon_vm_ova=$PHOTON_VM_OVA_VB" \
  bmp-vm.json


# modify the created ova to be usable by ovftools
cd build
tar -cf bmp-vm-vb.ova *.ovf *.vmdk
rm *.ovf *.vmdk
../scripts/to-vmware-ovf.sh "bmp-vm-vb" "bmp-vm"
cd ..
