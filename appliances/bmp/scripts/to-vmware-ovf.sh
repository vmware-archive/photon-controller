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
sed -i.bak $'s@<VirtualHardwareSection@ <ProductSection ovf:required="false"> \
      <Info/> \
      <Product>ESXCloud Installer</Product> \
      <Vendor>VMware Inc.</Vendor> \
      <Version>0.0.1</Version> \
      <Property ovf:key="ip0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>IP Address</Label> \
        <Description>The IP address for the ESXCloud Installer. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="use_photon_dhcp" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Use DHCP Server provided by Photon Controller</Label> \
        <Description>Use DCHP from Photon Controller to issue IP addresses and do Bare Metal Provisioning. Default is false.</Description> \
      </Property> \
      <Property ovf:key="netmask0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Netmask</Label> \
        <Description>The netmask for the ESXCloud Installer network. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="gateway" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Default Gateway</Label> \
        <Description>The default gateway address for the ESXCloud Installer network. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="DNS" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>DNS</Label> \
        <Description>The domain name servers for the ESXCloud Installer (comma separated). Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="ntp_servers" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>NTP Servers</Label> \
        <Description>Comma-delimited list of NTP servers</Description> \
      </Property> \
      <Property ovf:key="admin_password" ovf:userConfigurable="true" ovf:type="password"> \
        <Label>Admin Password</Label> \
        <Description>This password is used to SSH into the ESXCloud Installer. The username is esxcloud.</Description> \
      </Property> \
      <Property ovf:key="enable_syslog" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Enable Syslog</Label> \
        <Description>Enable syslog or not. Default is false.</Description> \
      </Property> \
      <Property ovf:key="syslog_endpoint" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Syslog Endpoint</Label> \
        <Description>IP address for syslog endpoint.</Description> \
      </Property> \
    </ProductSection> \
    <VirtualHardwareSection@' ${outputVM}.ovf

newOvfSha=$(sha1sum ${outputVM}.ovf | awk '{ print $1 }')
sed -i.bak "s/$oldOvfSha/$newOvfSha/" ${outputVM}.mf

tar cvf ${outputVM}.ova ${outputVM}.ovf ${outputVM}*.vmdk ${outputVM}.mf
