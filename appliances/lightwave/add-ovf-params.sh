#!/bin/bash

packerVM=$1
shift
outputVM=$1
shift

sed -i.bak $'s@<VirtualHardwareSection@ <ProductSection ovf:required="false"> \
      <Info/> \
      <Product>ESXCloud Installer</Product> \
      <Vendor>VMware Inc.</Vendor> \
      <Version>0.0.1</Version> \
      <Property ovf:key="ip0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>IP Address</Label> \
        <Description>The IP address for the Lightwave vm. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="netmask0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Netmask</Label> \
        <Description>The netmask for the Lightwave vm network. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="gateway" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Default Gateway</Label> \
        <Description>The default gateway address for the Lightwave vm network. Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="DNS" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>DNS</Label> \
        <Description>The domain name servers for the Lightwave vm (comma separated). Leave blank if DHCP is desired.</Description> \
      </Property> \
      <Property ovf:key="ntp_servers" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>NTP Servers</Label> \
        <Description>Comma-delimited list of NTP servers</Description> \
      </Property> \
      <Property ovf:key="root_password" ovf:userConfigurable="true" ovf:type="password"> \
        <Label>Root user password</Label> \
        <Description>This is the photon user password. (default: changeme)</Description> \
      </Property> \
      <Property ovf:key="photon_password" ovf:userConfigurable="true" ovf:type="password"> \
        <Label>Photon user password</Label> \
        <Description>This is the photon user password. (default: changeme)</Description> \
      </Property> \
      <Property ovf:key="lw_password" ovf:userConfigurable="true" ovf:type="password"> \
        <Label>Lightwave administrator password</Label> \
        <Description>This is the Lightwave administrator password. (default: L1ghtwave!)</Description> \
      </Property> \
      <Property ovf:key="lw_domain" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Lightwave domain</Label> \
        <Description>Domain of the Lilghtwave server.</Description> \
      </Property> \
      <Property ovf:key="lw_hostname" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Lightwave hostname</Label> \
        <Description>Hostname of the Lightwave server.</Description> \
      </Property> \
    </ProductSection> \
    <VirtualHardwareSection ovf:transport="com.vmware.guestInfo"@' ${outputVM}.ovf
