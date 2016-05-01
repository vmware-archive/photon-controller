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

from common.kind import Flavor
from host.hypervisor.disk_manager import DiskManager
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import DISK_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import os_datastore_root
from host.hypervisor.esx.path_util import os_datastore_path
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import os_vmdk_path
from host.hypervisor.esx.path_util import vmdk_path
from host.hypervisor.vm_manager import DiskNotFoundException
from host.hypervisor.resources import Disk


class EsxDiskManager(DiskManager):
    """ESX VM Manager specific implementation.

    This will be used by host/disk_manager.py if the agent has selected to use
    the ESX hypervisor on boot. This class contains all methods for VM disk
    operations.
    """

    def __init__(self, host_client, ds_manager):
        """Create ESX Disk Manager.

        :type host_client: VimClient
        :type ds_manager: DatastoreManager
        """
        self._logger = logging.getLogger(__name__)
        self._host_client = host_client
        self._ds_manager = ds_manager

    def create_disk(self, datastore, disk_id, size):
        name = vmdk_path(datastore, disk_id)
        self._vmdk_mkdir(datastore, disk_id)
        self._host_client.create_disk(name, size)
        self._host_client.set_disk_uuid(name, disk_id)

    def delete_disk(self, datastore, disk_id):
        name = vmdk_path(datastore, disk_id)
        self._host_client.delete_disk(name)
        self._vmdk_rmdir(datastore, disk_id)

    def move_disk(self, source_datastore, source_id, dest_datastore, dest_id):
        source = vmdk_path(source_datastore, source_id)
        dest = vmdk_path(dest_datastore, dest_id)
        self._vmdk_mkdir(dest_datastore, dest_id)
        self._host_client.move_disk(source, dest)
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
        self._host_client.copy_disk(source, dest)
        self._host_client.set_disk_uuid(dest, dest_id)

    def get_datastore(self, disk_id):
        for datastore in self._ds_manager.get_datastore_ids():
            disk = os_vmdk_path(datastore, disk_id)
            if os.path.isfile(disk):
                return datastore

        # Extra logging to help debug failures where host2 cannot find disk created by host1 on a shared datastore
        self._logger.error("get_disk_datastore failed: disk=%s, datastores=%s" %
                           (disk_id, self._ds_manager.get_datastore_ids()))
        for datastore in self._ds_manager.get_datastore_ids():
            p1 = os_datastore_root(datastore)
            p2 = os_datastore_path(datastore, compond_path_join(DISK_FOLDER_NAME_PREFIX, disk_id))
            p3 = os_vmdk_path(datastore, disk_id)
            self._logger.error("get_disk_datastore check_path: %s:%s, %s:%s, %s:%s" %
                               (p1, os.path.isdir(p1), p2, os.path.isdir(p2), p3, os.path.isfile(p3)))

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

    def _query_uuid(self, datastore, disk_id):
        name = vmdk_path(datastore, disk_id)
        return self._host_client.query_disk_uuid(name)

    def _vmdk_mkdir(self, datastore, disk_id):
        path = os.path.dirname(os_vmdk_path(datastore, disk_id))
        self._host_client.make_directory(path)

    def _vmdk_rmdir(self, datastore, disk_id):
        path = os.path.dirname(os_vmdk_path(datastore, disk_id))
        self._host_client.delete_file(path)
