#!/usr/bin/env python
# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.

"""
Python program for enabling LACP
"""

from __future__ import print_function

import uuid

from pyVim.connect import SmartConnect, Disconnect
from pyVmomi import vim, vmodl, Vim

import argparse
import atexit
import getpass
import sys
import traceback

SWITCH_IP_ADDRESS = "192.168.1.254"
PHYSICAL_NICS = ["vmnic1"]
DVS_ID = str(uuid.uuid4())
DVS_NAME = "dvs1"
LACP_NAME = "lacp1"


def get_args():
    """
    Supports the command-line arguments listed below.
    """
    parser = argparse.ArgumentParser(description='Process args for powering on a Virtual Machine')
    parser.add_argument('-s', '--host', required=True, action='store', help='Remote host to connect to')
    parser.add_argument('-o', '--port', type=int, default=443, action='store', help='Port to connect on')
    parser.add_argument('-u', '--user', required=True, action='store', help='User name to use when connecting to host')
    parser.add_argument('-p', '--password', required=False, action='store',
                        help='Password to use when connecting to host')
    args = parser.parse_args()
    return args


def format_uuid(uuid):
    """Converts a uuid string to the format used by hostd.
       'hh hh hh hh hh hh hh hh-hh hh hh hh hh hh hh hh'"""
    uuid = uuid.translate(None, " -")
    if len(uuid) != 32:
        raise ValueError("unexpected format for uuid: %s" % uuid)
    pairs = [uuid[i:i+2].lower() for i in range(0, len(uuid), 2)]
    return " ".join(pairs[:8]) + "-" + " ".join(pairs[8:])


def delete_all_dvs(si):
    dvsManager = si.RetrieveInternalContent().hostDistributedVirtualSwitchManager
    for dvs in dvsManager.GetDistributedVirtualSwitch():
        print("deleting DVS " + dvs)
        configSpec = Vim.Dvs.HostDistributedVirtualSwitchManager.DVSConfigSpec(
            uuid=dvs,
            backing=Vim.Dvs.HostMember.PnicBacking(),
        )
        dvsManager.ReconfigureDistributedVirtualSwitch(configSpec)
        dvsManager.RemoveDistributedVirtualSwitch(dvs)


def create_dvs(si, pnics):
    # Create the dvs.
    prodSpec = Vim.Dvs.ProductSpec(
        forwardingClass="etherswitch",
        vendor="VMware",
        version="6.0.0")

    uplinkPorts = []
    uplinkPortKeys = []
    pnicSpecs = []
    for i in range(len(pnics)):
        uplinkPorts.append(Vim.Dvs.HostDistributedVirtualSwitchManager.PortData(
            portKey=str(i),
            name="uplink" + str(i),
            connectionCookie=0))
        uplinkPortKeys.append(str(i))
        pnicSpecs.append(Vim.Dvs.HostMember.PnicSpec(
            pnicDevice=pnics[i],
            uplinkPortKey=str(i),
            connectionCookie=0))

    lacpGroup = Vim.Dvs.VmwareDistributedVirtualSwitch.LacpGroupConfig(
        key="123",
        name=LACP_NAME,
        mode="active",
        uplinkNum=2,
        loadbalanceAlgorithm="srcMac")

    backing = Vim.Dvs.HostMember.PnicBacking(pnicSpec=pnicSpecs)
    createSpec = Vim.Dvs.HostDistributedVirtualSwitchManager.DVSCreateSpec(
        uuid=format_uuid(DVS_ID),
        name=DVS_NAME,
        backing=backing,
        productSpec=prodSpec,
        maxProxySwitchPorts=64,
        modifyVendorSpecificDvsConfig=True,
        modifyVendorSpecificHostMemberConfig=True,
        port=uplinkPorts,
        uplinkPortKey=uplinkPortKeys,
        switchIpAddress=SWITCH_IP_ADDRESS)
    dvsManager = si.RetrieveInternalContent().hostDistributedVirtualSwitchManager
    vmwsetting = Vim.Dvs.HostDistributedVirtualSwitchManager.VmwareDVSSettingSpec()
    vmwsetting.SetLacpGroupConfig([lacpGroup])
    createSpec.SetVmwareSetting(vmwsetting)
    dvsManager.CreateDistributedVirtualSwitch(createSpec)
    # dvsManager.ApplyDvs([createSpec])


def main():
    args = get_args()
    if args.password:
        password = args.password
    else:
        password = getpass.getpass(prompt='Enter password for host %s and user %s: ' % (args.host, args.user))

    try:
        si = None
        try:
            si = SmartConnect(host=args.host,
                              user=args.user,
                              pwd=password,
                              port=int(args.port))
        except IOError:
            pass
        if not si:
            print("Cannot connect to specified host using specified username and password")
            sys.exit()

        atexit.register(Disconnect, si)

        delete_all_dvs(si)
        create_dvs(si, PHYSICAL_NICS)

        print("Success, created DVS " + format_uuid(DVS_ID))
    except vmodl.MethodFault as e:
        print("Caught vmodl fault : " + e.msg)
    except Exception as e:
        print("Caught Exception : " + str(e))
        print(traceback.format_exc())


# Start program
if __name__ == "__main__":
    main()
