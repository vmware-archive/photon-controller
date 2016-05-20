#!/bin/bash -xe

MANAGEMENT_VM_DIR="./management-vm"
EXPORTED_OVA_FILE="exported-photon-management-vm.ova"
OUTPUT_OVF_FILE="photon-management-vm.ovf"

rm -rf "$MANAGEMENT_VM_DIR"
mkdir -p "$MANAGEMENT_VM_DIR"

ovftool=${OVFTOOL_FULL_PATH:="$(which ovftool)"}
if [ ! -f "$ovftool" ]; then
  echo "ERROR: ovftool not found"
  exit -1
fi

export NO_RESTART_ALWAYS=1

./gradlew :devbox:renewPhoton

vagrant ssh -c "docker tag photon/zookeeper esxcloud/zookeeper"
vagrant ssh -c "docker tag photon/haproxy esxcloud/haproxy"
vagrant ssh -c "docker tag photon/deployer esxcloud/deployer"
vagrant ssh -c "docker tag photon/photon-controller-core esxcloud/photon-controller-core"
vagrant ssh -c "docker tag photon/management-api esxcloud/management-api"
vagrant ssh -c "docker tag photon/housekeeper esxcloud/housekeeper"


mgmt_ui_container_url="https://ci.ec.eng.vmware.com/view/UI/job/ec-ui-mgmt-publish-docker-image-develop/lastSuccessfulBuild/artifact/ci/docker-image/esxcloud-management-ui.tar"
container_tar=$(basename "$mgmt_ui_container_url")
vagrant ssh -c "wget -N -nv --no-proxy --no-check-certificate \"$mgmt_ui_container_url\""
vagrant ssh -c "docker load -i \"$container_tar\""
vagrant ssh -c "rm -f \"$container_tar\""

#
# Copy the config files and scripts
#
vagrant ssh -c "sudo docker cp deployer:/etc/esxcloud-deployer/ /etc/"
vagrant ssh -c "sudo mkdir -p /usr/lib/esxcloud/deployer/"
vagrant ssh -c "sudo docker cp deployer:/usr/lib/esxcloud/deployer/scripts/ /usr/lib/esxcloud/deployer/"

photon_vm=$(VBoxManage list runningvms | grep devbox-photon_photon | sed 's/"\(.*\)".*/\1/')
#
# Sleep for the docker operations to complete
#
echo "sleeping for 2 minutes to let docker operations complete..."
sleep 120

# removing all docker containers in the vagrant box
#
#                              \         .  ./
#                            \      .:";'.:.."   /
#                                (M^^.^~~:.'").
#                          -   (/  .    . . \ \)  -
#   O                         ((| :. ~ ^  :. .|))
#  |\\                     -   (\- |  \ /  |  /)  -
#  |  T                         -\  \     /  /-
# / \[_]..........................\  \   /  /
vagrant ssh -c 'docker stop $(docker ps -a -q)'
vagrant ssh -c 'docker rm $(docker ps -a -q)'

vagrant suspend

VBoxManage export "$photon_vm" -o "../$EXPORTED_OVA_FILE"

mv "../$EXPORTED_OVA_FILE" "$MANAGEMENT_VM_DIR/"


cd $MANAGEMENT_VM_DIR

# Extract vmdk
"$ovftool" --lax $EXPORTED_OVA_FILE $OUTPUT_OVF_FILE
# Remove unused files
rm -f -- *.ova
rm -f -- *.ovf
rm -f -- *.mf
cd ..

./gradlew :devbox:renewPhoton
vagrant ssh -c "sudo mkdir -p /var/photon/images"
vagrant ssh -c "sudo chown vagrant /var/photon/images"
vagrant ssh -c "sudo cp /vagrant/$MANAGEMENT_VM_DIR/photon-management-vm-disk1.vmdk /var/photon/images/"

echo "Devbox is ready for deployment"
