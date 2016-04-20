#!/bin/bash -xe

SSHD_ENABLE_ROOT_LOGIN=${SSHD_ENABLE_ROOT_LOGIN:-"false"}

PHOTON_ISO_URL=${ISO_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/userContent/bmp/photon-1.0-0185afd.iso"}
PHOTON_ISO_SHA1=${ISO_SHA1:="6cc1c646677ff8b8b48570b75286e496c85790f8"}
VBOX_ISO_URL=${VBOX_ISO_URL:="http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/userContent/vbox/VBoxGuestAdditions_5.0.14.iso"}
VBOX_ISO_SHA256=${VBOX_ISO_SHA256:="cec0df18671adfe62a34d3810543f76f76206b212b2b61791fe026214c77507c"}

# check esxi image in folder
if [ ! -d "bmp/installer-pxe-modules" ]; then
  echo "Missing an esxi pxe image, please add your pxe boot image to [bmp/installer-pxe-modules]"
  exit -1
fi

## build the base appliance ova
#packer build -force \
#  -var "photon_iso_url=$PHOTON_ISO_URL" \
#  -var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
#  -var "vbox_iso_url=$VBOX_ISO_URL" \
#  -var "vbox_iso_sha256=$VBOX_ISO_SHA256" \
#  -var "sshd_enable_root_login=$SSHD_ENABLE_ROOT_LOGIN" \
#  -var "photon_vm_ova=$PHOTON_VM_OVA_VB" \
#  bmp-base-vm.json
#
#
## modify the created ova to be usable by ovftools
#cd build
#tar -cf bmp-base-vm-vb.ova *.ovf *.vmdk
#rm *.ovf *.vmdk
#
#cp bmp-base-vm-vb.ova ..
#cd ..

PHOTON_VM_OVA_VB=./bmp-base-vm-vb.ova
PHOTON_CACHED_ISO=./packer_cache/3d61cc7aa2b844de59ed35aae984d70e4bb6158e379a02319cbd19ec65595e6e.iso

# build the appliance ova
packer build -force \
  -var "photon_iso_url=$PHOTON_ISO_URL" \
  -var "photon_iso_sha1=$PHOTON_ISO_SHA1" \
  -var "vbox_iso_url=$VBOX_ISO_URL" \
  -var "vbox_iso_sha256=$VBOX_ISO_SHA256" \
  -var "sshd_enable_root_login=$SSHD_ENABLE_ROOT_LOGIN" \
  -var "photon_vm_ova=$PHOTON_VM_OVA_VB" \
  -var "photon_cached_iso=$PHOTON_CACHED_ISO" \
  bmp-vm.json

cd build
../scripts/to-vmware-ovf.sh "bmp-vm-vb" "bmp-vm"
cd ..
