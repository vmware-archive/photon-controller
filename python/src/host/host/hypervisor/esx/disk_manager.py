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

""" Contains the implementation code for ESX VM Disk operations."""

import logging
import os

from pyVmomi import vim

from common.kind import Flavor
from host.hypervisor.disk_manager import DiskManager
from host.hypervisor.disk_manager import DiskFileException
from host.hypervisor.disk_manager import DiskPathException
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import DEFAULT_DISK_ADAPTER_TYPE
from host.hypervisor.esx.vm_config import os_vmdk_path
from host.hypervisor.esx.vm_config import uuid_to_vmdk_uuid
from host.hypervisor.esx.vm_config import vmdk_path
from host.hypervisor.vm_manager import DiskNotFoundException
from host.hypervisor.resources import Disk


class EsxDiskManager(DiskManager):
    """ESX VM Manager specific implementation.

    This will be used by host/disk_manager.py if the agent has selected to use
    the ESX hypervisor on boot. This class contains all methods for VM disk
    operations.
    """

    def __init__(self, vim_client, ds_manager):
        """Create ESX Disk Manager.

        :type vim_client: VimClient
        :type ds_manager: DatastoreManager
        """
        self._logger = logging.getLogger(__name__)
        self._vim_client = vim_client
        self._ds_manager = ds_manager

    @property
    def _manager(self):
        """Get the virtual disk manager for the host
        rtype:vim.VirtualDiskManager
        """
        return self._vim_client.virtual_disk_manager

    def create_disk(self, datastore, disk_id, size):
        spec = self._create_spec(size)
        name = vmdk_path(datastore, disk_id)
        self._vmdk_mkdir(datastore, disk_id)
        self._manage_disk(vim.VirtualDiskManager.CreateVirtualDisk_Task, name=name, spec=spec)
        self._manage_disk(vim.VirtualDiskManager.SetVirtualDiskUuid, name=name, uuid=uuid_to_vmdk_uuid(disk_id))

    def delete_disk(self, datastore, disk_id):
        name = vmdk_path(datastore, disk_id)
        self._manage_disk(vim.VirtualDiskManager.DeleteVirtualDisk_Task, name=name)
        self._vmdk_rmdir(datastore, disk_id)

    def move_disk(self, source_datastore, source_id, dest_datastore, dest_id):
        source = vmdk_path(source_datastore, source_id)
        dest = vmdk_path(dest_datastore, dest_id)
        self._vmdk_mkdir(dest_datastore, dest_id)
        self._manage_disk(vim.VirtualDiskManager.MoveVirtualDisk_Task, sourceName=source, destName=dest)
        self._vmdk_rmdir(source_datastore, source_id)

    def copy_disk(self, source_datastore, source_id, dest_datastore, dest_id):
        """Copy a virtual disk.

        This method is used to create a "full clone" of a vmdk.
        Underneath, this call boils down to doing a DiskLib_Clone()

        Command line equivalent:
          $ vmkfstools -i source dest

        """
        source = vmdk_path(source_datastore, source_id, IMAGE_FOLDER_NAME_PREFIX)
        dest = vmdk_path(dest_datastore, dest_id)
        self._vmdk_mkdir(dest_datastore, dest_id)
        self._manage_disk(vim.VirtualDiskManager.CopyVirtualDisk_Task, sourceName=source, destName=dest)
        self._manage_disk(vim.VirtualDiskManager.SetVirtualDiskUuid, name=dest, uuid=uuid_to_vmdk_uuid(dest_id))

    def get_datastore(self, disk_id):
        for datastore in self._ds_manager.get_datastore_ids():
            disk = os_vmdk_path(datastore, disk_id)
            if os.path.isfile(disk):
                return datastore
        return None

    def get_resource(self, disk_id):
        datastore = self.get_datastore(disk_id)
        if datastore is None:
            raise DiskNotFoundException(disk_id)
        resource = Disk(disk_id)
        resource.flavor = Flavor("default")  # TODO
        resource.persistent = False  # TODO
        resource.new_disk = False
        resource.capacity_gb = -1  # TODO
        resource.image = None
        resource.datastore = datastore
        return resource

    def _manage_disk(self, op, **kwargs):
        try:
            self._logger.debug("Invoking %s(%s)" % (op.info.name, kwargs))
            task = op(self._manager, **kwargs)
            if task:
                self._vim_client.wait_for_task(task)
        except vim.fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    def _create_spec(self, size):
        spec = vim.VirtualDiskManager.FileBackedVirtualDiskSpec()
        spec.capacityKb = size * (1024 ** 2)
        spec.diskType = vim.VirtualDiskManager.VirtualDiskType.thin
        spec.adapterType = DEFAULT_DISK_ADAPTER_TYPE
        return spec

    def _query_uuid(self, datastore, disk_id):
        name = vmdk_path(datastore, disk_id)
        return self._manager.QueryVirtualDiskUuid(name=name)

    def _vmdk_mkdir(self, datastore, disk_id):
        path = os.path.dirname(os_vmdk_path(datastore, disk_id))
        self._vim_client.make_directory(path)

    def _vmdk_rmdir(self, datastore, disk_id):
        path = os.path.dirname(os_vmdk_path(datastore, disk_id))
        self._vim_client.delete_file(path)
