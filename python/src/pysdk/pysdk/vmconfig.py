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
Virtual Machine Configuration Operations
"""

from pyVmomi import vim
from invt import *  # noqa


def GetFreeKey(cspec):
    """ Get a free key for a new device in the spec """
    minkey = -1
    deviceChange = cspec.deviceChange
    if deviceChange is None:
        return minkey
    for devSpec in deviceChange:
        if minkey >= devSpec.device.key:
            minkey = devSpec.device.key - 1
    return minkey


def GetCnxInfo(conInfo):
    """ Function guarantees a connection info that is sufficiently populated
    """
    if conInfo is None:
        conInfo = vim.vm.device.VirtualDevice.ConnectInfo()
        conInfo.allowGuestControl = True
        conInfo.connected = False
        conInfo.startConnected = False
    return conInfo


def GetCfgOption(cfgOption):
    if cfgOption is None:
        envBrowser = GetEnv()
        cfgOption = envBrowser.QueryConfigOption(None, None)
    return cfgOption


def GetCfgTarget(cfgTarget):
    if cfgTarget is None:
        envBrowser = GetEnv()
        cfgTarget = envBrowser.QueryConfigTarget(None)
    return cfgTarget


def GetDeviceOptions(devType, cfgOption=None):
    foundOptions = []
    if cfgOption is None:
        cfgOption = GetCfgOption()
    for opt in cfgOption.GetHardwareOptions().virtualDeviceOption:
        if isinstance(opt, devType):
            foundOptions.append(opt)
    return opt


def GetControllers(cfgOption, controllerType, cfgInfo=None, cspec=None):
    """ Get all controllers of specified type """
    ctlrs = []
    for device in cfgOption.defaultDevice:
        if isinstance(device, controllerType):
            ctlrs.append(device)
    if cfgInfo is not None:
        for device in cfgInfo.hardware.device:
            if isinstance(device, controllerType):
                ctlrs.append(device)
    if cspec is not None:
        for device in GetDeviceListFromSpec(cspec):
            if isinstance(device, controllerType):
                ctlrs.append(device)
    return ctlrs


def GetNic(type):
    if type == "pcnet":
        return vim.vm.device.VirtualPCNet32()
    elif type == "e1000":
        return vim.vm.device.VirtualE1000()
    elif type == "vmxnet":
        return vim.vm.device.VirtualVmxnet()
    elif type == "vmxnet3":
        return vim.vm.device.VirtualVmxnet3()
    elif type == "e1000e":
        return vim.vm.device.VirtualE1000e()
    else:
        raise Exception("Invalid nic type " + type + " specified!")


def GetDeviceListFromSpec(cspec):
    """ Get an array of devices that is currently present in the config spec
    """
    devices = []
    deviceChange = cspec.deviceChange
    if deviceChange is None:
        return devices
    for devSpec in deviceChange:
        if devSpec.operation != \
                vim.vm.device.VirtualDeviceSpec.Operation.remove:
            devices.append(devSpec.device)
    return devices


def GetFreeBusNumber(cfgOption, ctlrType, cfgInfo, cspec):
    ctlrs = GetControllers(cfgOption, ctlrType, cfgInfo, cspec)
    usedBusNumbers = []
    for dev in ctlrs:
        usedBusNumbers.append(dev.busNumber)
    slot = 0
    for i in sorted(usedBusNumbers):
        if slot < i:
            break
        elif slot == i:
            slot = slot + 1
    return slot


def CreateProfileSpec(policy):
    profileSpec = []

    #
    # Even though the correct signature is to expect a profile, to
    # accommodate earlier workarounds that bypassed SPBM, accept
    # strings as though they are raw profile data.
    #
    if isinstance(policy, vim.vm.ProfileSpec):
        profileSpec.append(policy)
    else:  # isinstance(policy, string):
        pspec = vim.vm.DefinedProfileSpec()
        rawData = vim.vm.ProfileRawData()
        rawData.extensionKey = "com.vmware.vim.sps"
        rawData.objectData = policy
        pspec.profileData = rawData
        profileSpec.append(pspec)

    return profileSpec


def CreateDefaultSpec(name="Dummy VM", memory=128, guest="otherGuest",
                      annotation="Quick Dummy", cpus=1,
                      datastoreName=None, policy=None):
    cspec = vim.vm.ConfigSpec()
    cspec.annotation = annotation
    cspec.memoryMB = memory
    cspec.guestId = guest
    cspec.name = name
    cspec.numCPUs = cpus
    files = vim.vm.FileInfo()
    if (datastoreName is None):
        raise Exception("Invalid datastore")
    files.vmPathName = "[" + datastoreName + "]"
    cspec.files = files

    if policy is not None:
        profileSpec = CreateProfileSpec(policy)
        cspec.vmProfile = profileSpec

    return cspec


def AddDeviceToSpec(cspec, device, op=None, fileop=None, policy=None):
    """ Add a device to the given spec """
    devSpec = vim.vm.device.VirtualDeviceSpec()
    if op is not None:
        devSpec.operation = op
    if fileop is not None:
        devSpec.fileOperation = fileop
    devSpec.device = device

    if policy is not None:
        profileSpec = CreateProfileSpec(policy)
        devSpec.profile = profileSpec

    if cspec.deviceChange is None:
        cspec.deviceChange = []
    deviceChange = cspec.deviceChange
    deviceChange.append(devSpec)
    cspec.deviceChange = deviceChange
    return cspec


def AddNic(cspec, cfgOption=None, cfgTarget=None, devName=None,
           addressType=None, nicType="pcnet", mac=None,
           wakeOnLan=False, conInfo=None, cfgInfo=None, unitNumber=-1):
    """ Add a nic to the config spec based on the options specified """
    # Get config options and targets
    cfgOption = GetCfgOption(cfgOption)
    cfgTarget = GetCfgTarget(cfgTarget)

    # Nic backing (should be configurable)
    nicBacking = vim.vm.device.VirtualEthernetCard.NetworkBackingInfo()
    if len(cfgTarget.network) > 0:
        nicBacking.deviceName = cfgTarget.network[0].name

    nic = GetNic(nicType)

    # address type
    if addressType is not None:
        nic.SetAddressType(addressType)

    # mac
    if mac is not None:
        nic.macAddress = mac

    nic.wakeOnLanEnabled = wakeOnLan
    nic.backing = nicBacking
    nic.connectable = GetCnxInfo(conInfo)
    nic.key = GetFreeKey(cspec)
    ctlrs = GetControllers(cfgOption, vim.vm.device.VirtualPCIController,
                           cfgInfo, cspec)
    nic.controllerKey = ctlrs[0].key
    nic.unitNumber = unitNumber

    return AddDeviceToSpec(cspec, nic,
                           vim.vm.device.VirtualDeviceSpec.Operation.add,
                           None)


def AddIsoCdrom(cspec, fileName, cfgOption=None,
                conInfo=None, cfgInfo=None, unitNumber=-1):
    """ Add a cdrom to the specified spec """

    # Get config options and targets
    cfgOption = GetCfgOption(cfgOption)

    devBacking = vim.vm.device.VirtualCdrom.IsoBackingInfo()
    devBacking.fileName = fileName

    cdrom = vim.vm.device.VirtualCdrom()
    cdrom.key = GetFreeKey(cspec)
    ctlrs = GetControllers(cfgOption, vim.vm.device.VirtualIDEController,
                           cfgInfo, cspec)
    cdrom.controllerKey = ctlrs[0].key
    cdrom.unitNumber = unitNumber
    cdrom.connectable = GetCnxInfo(conInfo)
    cdrom.backing = devBacking

    return AddDeviceToSpec(cspec, cdrom,
                           vim.vm.device.VirtualDeviceSpec.Operation.add,
                           None)


def AddURIBackedSerial(cspec, serviceURI, direction="connect",
                       proxyURI=None, yieldPoll=True):
    backing = vim.vm.device.VirtualSerialPort.URIBackingInfo()
    backing.serviceURI = serviceURI
    backing.direction = direction
    backing.proxyURI = proxyURI
    return AddSerial(cspec, backing, yieldPoll)


def AddSerial(cspec, backing, yieldPoll=True):
    """ Add a serial port to the config spec. """
    serial = vim.vm.device.VirtualSerialPort()
    serial.yieldOnPoll = yieldPoll
    serial.key = GetFreeKey(cspec)
    serial.backing = backing
    return AddDeviceToSpec(cspec, serial,
                           vim.vm.device.VirtualDeviceSpec.Operation.add)
