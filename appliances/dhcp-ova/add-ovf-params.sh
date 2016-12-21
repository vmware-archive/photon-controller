#!/bin/bash
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
# specific language governing permissions and limitations under the License.
#
# This script adds custom ovf tools allowing us to provide configuration
# parameters to the vm created through ovftool.
packerVM=$1
shift
outputVM=$1
shift

sed -i.bak $'s@<VirtualHardwareSection@ <ProductSection ovf:required="false"> \
      <Info/> \
      <Product>DHCP</Product> \
      <Vendor>VMware Inc.</Vendor> \
      <Version>1.0.0</Version> \
      <Property ovf:key="ip0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Private IP Address</Label> \
        <Description>The Private IP address for the DHCP vm.</Description> \
      </Property> \
      <Property ovf:key="netmask0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Private Interface Netmask</Label> \
        <Description>The netmask for the Private VM network.</Description> \
      </Property> \
      <Property ovf:key="gateway0" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Private Interface Default Gateway</Label> \
        <Description>The default gateway address for the Private VM network.</Description> \
      </Property> \
      <Property ovf:key="ip1" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Public IP Address</Label> \
        <Description>The Public IP address for the VM.</Description> \
      </Property> \
      <Property ovf:key="netmask1" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Public Interface Netmask</Label> \
        <Description>The netmask for the Public VM interface.</Description> \
      </Property> \
      <Property ovf:key="gateway1" ovf:userConfigurable="true" ovf:type="string"> \
        <Label>Public Interface Default Gateway</Label> \
        <Description>The default gateway address for the Public Network Interface.</Description> \
      </Property> \
      <Property ovf:key="root_password" ovf:userConfigurable="true" ovf:type="password"> \
        <Label>Root user password</Label> \
        <Description>This is the Root user password. (default: change on login)</Description> \
      </Property> \
    </ProductSection> \
    <VirtualHardwareSection ovf:transport="com.vmware.guestInfo"@' ${outputVM}.ovf
