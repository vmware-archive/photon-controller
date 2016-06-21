# Copyright 2015 VMware, Inc. All Rights Reserved.
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
Utility functions for hosts.
"""

from pyVmomi import vim
from pyVmomi.VmomiSupport import ResolveLinks


# @param si [in] Retrieve the root folder
def GetRootFolder(si):
    """
    Retrieve the root folder.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a Folder
    @return        : Reference to the top of the inventory managed by this
                     service.
    """

    content = si.RetrieveContent()
    rootFolder = content.rootFolder
    return rootFolder


# @param si [in] Retrieve the host folder
def GetHostFolder(si):
    """
    Retrieve the host folder.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a Folder
    @return        : A reference to the folder hierarchy that contains the
                     compute resources, including hosts and clusters.
    """

    content = si.RetrieveContent()
    dataCenter = content.rootFolder.childEntity[0]
    hostFolder = dataCenter.hostFolder
    return hostFolder


# @param si [in] Retrieve the compute resource for the host
def GetComputeResource(si):
    """
    Retrieve the compute resource for the host.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a ComputeResource
    @return        :
    """

    hostFolder = GetHostFolder(si)
    computeResource = hostFolder.childEntity[0]
    return computeResource


# @param si [in] Retrieve the host system
def GetHostSystem(si):
    """
    Retrieve the host system.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a HostSystem
    @return        :
    """

    computeResource = GetComputeResource(si)
    hostSystem = computeResource.host[0]
    return hostSystem


# @param si [in] Retrieve the host config manager
def GetHostConfigManager(si):
    """
    Retrieve the host config manager.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a HostConfigManager.
    @return        :
    """

    hostSystem = GetHostSystem(si)
    configManager = hostSystem.configManager
    return configManager


# @param si [in] Retrieve the host's virtual nic manager
def GetHostVirtualNicManager(si):
    """
    Retrieve the host VirtualNicManager

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a VirtualNicManager
    @return        :
    """

    configMgr = GetHostConfigManager(si)
    vnicMgr = configMgr.virtualNicManager
    return vnicMgr


# @param si [in] Retrieve the UUID of the host
def GetHostUuid(si):
    """
    Retrieve the UUID of the host.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : str
    @return        : Hardware BIOS identification.
    """

    hostSystem = GetHostSystem(si)
    hwInfo = hostSystem.hardware
    if hwInfo is None:
        raise Exception("Hardware info of host is NULL.")
    return hwInfo.systemInfo.uuid


# @param si [in] Retrieve the root resource pool of a host
def GetRootResourcePool(si):
    """
    Retrieve the root resource pool of a host.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a ResourcePool
    @return        : Reference to root resource pool.
    """

    computeRes = GetComputeResource(si)
    resPool = computeRes.resourcePool
    return resPool


def GetNicIp(si, nicType):
    """
    Retrieve the IP associated with a specified Nic type on the host.

    @type    si         : ServiceInstance ManagedObject
    @param   si         :
    @type    nicType    : str
    @param   nicType    : Type of Nic
    @rtype              : str
    @return             : The IP address currently used by
                          the specified nic type, if selected
    """

    vnicMgr = GetHostVirtualNicManager(si)
    netConfig = vnicMgr.QueryNetConfig(nicType)
    if netConfig is None:
        raise Exception("NetConfig is NULL.")
    vnicArr = ResolveLinks(netConfig.selectedVnic, netConfig)
    if len(vnicArr) < 1:
        raise Exception("No Nic configured for type " + nicType)
    ipConfig = vnicArr[0].spec.ip
    return ipConfig.ipAddress


# @param si [in] Retrieve the VMotion IP of the host
def GetVMotionIP(si):
    """
    Retrieve the VMotion IP of the host.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : str
    @return        : The IP address currently used by the VMotion NIC.
                     All IP addresses are specified using
                     IPv4 dot notation. For example,"192.168.0.1".
                     Subnet addresses and netmasks are specified using
                     the same notation.
    """

    return GetNicIp(si, vim.host.VirtualNicManager.NicType.vmotion)


# @param si [in] Retrieve the VMotionManager instance for a host
def GetVmotionManager(si):
    """
    Retrieve the VMotionManager instance for a host.

    @type    si    : ServiceInstance ManagedObject
    @param   si    :
    @rtype         : ManagedObjectReference to a VmotionManager.
    @return        :
    """

    vmotionMgr = vim.host.VMotionManager("ha-vmotionmgr", si._stub)
    return vmotionMgr
