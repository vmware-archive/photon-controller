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
Inventory functions
"""

from pyVmomi import vim
import connect


def GetEnv(dataCenter=None, si=None):
    """Get the default environment for this host"""
    cr = GetHostFolder(dataCenter, si).childEntity[0]
    return cr.environmentBrowser


def GetDatacenter(name=None, si=None):
    """
    Gets the datacenter with the given name. If the name is None, returns the
    first (and in the case of hostd, only) datacenter.
    """
    if not si:
        si = connect.GetSi()
    content = si.RetrieveContent()

    if name is None:
        if len(content.rootFolder.childEntity) == 0:
            return None
        else:
            for child in content.rootFolder.childEntity:
                # Not all children of the root folder are Datacenters, so we
                # need to scan the list looking for one.
                if isinstance(child, vim.Datacenter):
                    return child

            return None
    else:
        return FindChild(content.rootFolder, name)


def GetHostFolder(dataCenter=None, si=None):
    """Retrieve the host folder for the given datacenter."""
    return GetDatacenter(dataCenter, si).hostFolder


def GetVmFolder(dataCenter=None, si=None):
    """Retrieve the folder where virtual machines are stored on this host."""
    return GetDatacenter(dataCenter, si).vmFolder


def FindChild(parent, name):
    """
    Find the managed entity with the given name in the list of children for
    this entity. If the input name is None, returns the first child. Returns
    None if the child with the given name is not found
    """

    if name is None:
        return parent.childEntity[0]

    for child in parent.childEntity:
        if child.name == name:
            return child

    return None
