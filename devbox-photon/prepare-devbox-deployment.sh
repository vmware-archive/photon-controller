#!/bin/bash -xe

MANAGEMENT_VM_DIR="./management-vm"
EXPORTED_OVA_FILE="exported-photon-management-vm.ova"
OUTPUT_OVA_FILE="photon-management-vm.ova"
OUTPUT_MF_FILE="photon-management-vm.mf"
OUTPUT_OVF_FILE="photon-management-vm.ovf"
OUTPUT_VMDK_FILE="photon-management-vm-disk1.vmdk"

function remove_if_exists() {
  if [ -f "$*" ]
  then
    rm -f "$*"
  fi
}

if [ ! -d $MANAGEMENT_VM_DIR ]
then
  mkdir $MANAGEMENT_VM_DIR
else
  remove_if_exists "$MANAGEMENT_VM_DIR/$EXPORTED_OVA_FILE"
  remove_if_exists "$MANAGEMENT_VM_DIR/$OUTPUT_OVF_FILE"
  remove_if_exists "$MANAGEMENT_VM_DIR/$OUTPUT_VMDK_FILE"
  remove_if_exists "$MANAGEMENT_VM_DIR/$OUTPUT_MF_FILE"
  remove_if_exists "$MANAGEMENT_VM_DIR/$OUTPUT_OVA_FILE"
fi

# Use ovftool to convert to vmware friendly format
# This will generate vmdk, mf and ovf
# Find `ovftool` in PATH if needed
if [ -z "$RUN_ON_MAC" ]
then
  image_vsphere_ovf_ovftool_path=$(which ovftool)
else
  image_vsphere_ovf_ovftool_path="$OVFTOOL_FULL_PATH"
fi

if [ -z "$image_vsphere_ovf_ovftool_path" ]
then
  echo "ERROR: ovftool not found"
  exit -1
fi

export NO_RESTART_ALWAYS=1

vagrant destroy -f

vagrant up

vagrant ssh -c "docker tag devbox/zookeeper esxcloud/zookeeper"
vagrant ssh -c "docker tag devbox/haproxy esxcloud/haproxy"
vagrant ssh -c "docker tag devbox/deployer esxcloud/deployer"
vagrant ssh -c "docker tag devbox/cloud_store esxcloud/cloud-store"
vagrant ssh -c "docker tag devbox/management_api esxcloud/management-api"
vagrant ssh -c "docker tag devbox/root_scheduler esxcloud/root-scheduler"
vagrant ssh -c "docker tag devbox/chairman esxcloud/chairman"
vagrant ssh -c "docker tag devbox/housekeeper esxcloud/housekeeper"

#
# Copy the config files and scripts
#
vagrant ssh -c "sudo docker cp devbox_deployer_container:/etc/esxcloud-deployer/ /etc/"
vagrant ssh -c "sudo mkdir -p /usr/lib/esxcloud/deployer/"
vagrant ssh -c "sudo docker cp devbox_deployer_container:/usr/lib/esxcloud/deployer/scripts/ /usr/lib/esxcloud/deployer/"
vagrant ssh -c "sudo chmod +x /usr/lib/esxcloud/deployer/scripts/"

#
# Sleep for the docker operations to complete
#
echo "sleeping for 2 minutes to let docker operations complete..."
sleep 120

vagrant suspend

photon_vm=$(VBoxManage list vms | grep devbox-photon_photon | sed 's/"\(.*\)".*/\1/')

VBoxManage export ${photon_vm} -o ../$EXPORTED_OVA_FILE

mv ../$EXPORTED_OVA_FILE $MANAGEMENT_VM_DIR/

# Use ovftool to convert to vmware friendly format
# This will generate vmdk, mf and ovf
# Find `ovftool` in PATH if needed
if [ -z "$RUN_ON_MAC" ]
then
  image_vsphere_ovf_ovftool_path=$(which ovftool)
else
  image_vsphere_ovf_ovftool_path="$OVFTOOL_FULL_PATH"
fi

# Abort when $image_vsphere_ovf_ovftool_path is empty
if [ -z "${image_vsphere_ovf_ovftool_path:-}" ]
then
  echo "image_vsphere_ovf_ovftool_path is empty"
  exit -1
fi

cd $MANAGEMENT_VM_DIR

"$image_vsphere_ovf_ovftool_path" --lax $EXPORTED_OVA_FILE $OUTPUT_OVF_FILE
if [ -z "$RUN_ON_MAC" ]
then
  # get the old sha1 of the ovf file
  OLD_OVF_SHA=$(sha1sum $OUTPUT_OVF_FILE | cut -d ' ' -f 1)
else
  # get the old sha1 of the ovf file
  OLD_OVF_SHA=$(openssl sha1 $OUTPUT_OVF_FILE | cut -d ' ' -f 2)
fi

# Overwrite VirtualBox related stuff with vmware related values
sed -i.bak 's/virtualbox-2.2/vmx-04 vmx-06 vmx-07 vmx-09/' $OUTPUT_OVF_FILE
sed -i.bak 's/<rasd:Caption>sataController0/<rasd:Caption>SCSIController/' $OUTPUT_OVF_FILE
sed -i.bak 's/<rasd:Description>SATA Controller/<rasd:Description>SCSI Controller/' $OUTPUT_OVF_FILE
sed -i.bak 's/<rasd:ElementName>sataController0/<rasd:ElementName>SCSIController/' $OUTPUT_OVF_FILE
sed -i.bak 's/<rasd:ResourceSubType>AHCI/<rasd:ResourceSubType>lsilogic/' $OUTPUT_OVF_FILE
sed -i.bak 's/<rasd:ResourceType>20</<rasd:ResourceType>6</' $OUTPUT_OVF_FILE

if [ -z "$RUN_ON_MAC" ]
then
  # update the sha1 to match the new ovf file
  NEW_OVF_SHA=$(sha1sum $OUTPUT_OVF_FILE | cut -d ' ' -f 1)
else
  # update the sha1 to match the new ovf file
  NEW_OVF_SHA=$(openssl sha1 $OUTPUT_OVF_FILE | cut -d ' ' -f 2)
fi
sed -i.bak "s/$OLD_OVF_SHA/$NEW_OVF_SHA/" $OUTPUT_MF_FILE

tar cvf $OUTPUT_OVA_FILE $OUTPUT_OVF_FILE $OUTPUT_VMDK_FILE $OUTPUT_MF_FILE
rm *.ova
rm *.ovf
rm *.mf
rm *.bak
cd ..

vagrant resume
vagrant ssh -c "sudo mkdir -p /var/photon/images"
vagrant ssh -c "sudo chown vagrant /var/photon/images"
vagrant ssh -c "sudo cp /vagrant/$MANAGEMENT_VM_DIR/photon-management-vm-disk1.vmdk /var/photon/images/"

echo "Dev Box is ready for deployment"
