#!/bin/bash

if [ -f seed-dhcp-vm.iso ] ; then rm seed-dhcp-vm.iso ; fi
if [ -f seed-dhcp-vagrant-box.iso ] ; then rm seed-dhcp-vagrant-box.iso ; fi


if [ -z "$RUN_ON_UBUNTU" ] ; then
  hdiutil makehybrid -o seed-dhcp-vm.iso -hfs -iso -default-volume-name cidata -joliet config-dhcp-vm
  hdiutil makehybrid -o seed-dhcp-vagrant-box.iso -hfs -iso -default-volume-name cidata -joliet config-dhcp-vagrant-box
else
  dpkg-query -l genisoimage
  if [ 0 -ne $? ]
  then
    echo "genisoimage is not installed. Installing."
    sudo apt-get install -y genisoimage
  fi
  cd config-dhcp-vm
  genisoimage -output seed-dhcp-vm.iso -volid cidata -joliet -rock user-data meta-data
  mv seed-dhcp-vm.iso ..
  cd ../config-dhcp-vagrant-box
  genisoimage -output seed-dhcp-vagrant-box.iso -volid cidata -joliet -rock user-data meta-data
  mv seed-dhcp-vagrant-box.iso ..
  cd ..
fi
