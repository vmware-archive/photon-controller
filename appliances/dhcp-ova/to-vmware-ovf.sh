#!/bin/bash -xe
inputVM=$1
outputVM=$2

ovftool --lax -o ${inputVM}.ova ${outputVM}.ovf
oldOvfSha=$(sha1sum ${outputVM}.ovf | awk '{ print $1 }')

# Overwrite VirtualBox related stuff with vmware related values
# Overwrite VirtualBox related stuff with vmware related values
sed -i.bak 's/virtualbox-2.2/vmx-04 vmx-06 vmx-07 vmx-09/' ${outputVM}.ovf
sed -i.bak 's/<rasd:Caption>sataController0/<rasd:Caption>SCSIController/' ${outputVM}.ovf
sed -i.bak 's/<rasd:Description>SATA Controller/<rasd:Description>SCSI Controller/' ${outputVM}.ovf
sed -i.bak 's/<rasd:ElementName>sataController0/<rasd:ElementName>SCSIController/' ${outputVM}.ovf
sed -i.bak 's/<rasd:ResourceSubType>AHCI/<rasd:ResourceSubType>lsilogic/' ${outputVM}.ovf
sed -i.bak 's/<rasd:ResourceType>20</<rasd:ResourceType>6</' ${outputVM}.ovf

newOvfSha=$(sha1sum ${outputVM}.ovf | awk '{ print $1 }')
sed -i.bak "s/$oldOvfSha/$newOvfSha/" ${outputVM}.mf

tar cvf ${outputVM}.ova ${outputVM}.ovf ${outputVM}*.vmdk ${outputVM}.mf
rm *.ovf
rm *.mf
rm *.vmdk
rm *.bak
