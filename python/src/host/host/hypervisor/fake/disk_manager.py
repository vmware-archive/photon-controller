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

"""Fake hypervisor virtual disk operations."""

import logging
import os
import sys

from common.file_util import mkdir_p, mkdtemp
from common.kind import Flavor
from host.hypervisor.disk_manager import DatastoreOutOfSpaceException
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.disk_manager import DiskManager
from host.hypervisor.resources import Disk
from host.hypervisor.vm_manager import DiskNotFoundException


class FakeDiskManager(DiskManager):
    """A class that wraps hypervisor specific disk management code.
    The 'name' of a disk can be either a datastore path or a URL.

    Attributes:
        disk_manager: The hypervisor specific disk_manager instance.

    """

    def __init__(self, hypervisor, disk_path=None, capacity_map=None):
        self.hypervisor = hypervisor
        self.capacity_map = capacity_map
        self.disk_path = disk_path or mkdtemp(delete=True)
        self._logger = logging.getLogger(__name__)
        self._logger.debug("Fake disk manager uses tempdir %s" %
                           self.disk_path)
        mkdir_p(self.disk_path)

    def create_disk(self, datastore, name, size, force=False):
        if not force:
            self._assert_not_exists(name, datastore)
        self._check_free_space(datastore, size)
        disk_file = self._disk_path(datastore, name)
        mkdir_p(os.path.dirname(disk_file))
        with open(disk_file, "a") as f:
            f.truncate(size)

    def delete_disk(self, datastore, name):
        self._assert_exists(name, datastore)
        os.unlink(self._disk_path(datastore, name))

    def move_disk(self, source_datastore, source, dest_datastore, dest):
        self._assert_exists(source, source_datastore)
        self._assert_not_exists(dest, dest_datastore)
        dest_file = self._disk_path(dest_datastore, dest)
        source_file = self._disk_path(source_datastore, source)
        self._check_free_space(dest_datastore, os.path.getsize(source_file))
        mkdir_p(os.path.dirname(dest_file))
        os.rename(self._disk_path(source_datastore, source), dest_file)

    def copy_disk(self, source_datastore, source, dest_datastore, dest,
                  force=False):
        if not self.hypervisor.image_manager.check_image(source,
                                                         source_datastore):
            raise DiskNotFoundException("ENOENT")
        dest_file = self._disk_path(dest_datastore, dest)
        image_file = self.hypervisor.image_manager.image_file(source_datastore,
                                                              source)
        self._check_free_space(dest_datastore, os.path.getsize(image_file))
        if not force:
            # force allows overwriting existing destination
            self._assert_not_exists(dest, dest_datastore)
        mkdir_p(os.path.dirname(dest_file))
        with open(dest_file, "a") as f:
            image_file = self.hypervisor.image_manager.image_file(
                source_datastore, source)
            f.truncate(os.path.getsize(image_file))

    def get_datastore(self, name):
        for datastore in os.listdir(self.disk_path):
            if os.path.isdir(self._datastore_path(datastore)):
                if os.path.exists(self._disk_path(datastore, name)):
                    return datastore
        return None

    def get_resource(self, disk_id):
        datastore = self.get_datastore(disk_id)
        if datastore is None:
            raise DiskNotFoundException(disk_id)

        size = os.path.getsize(self._disk_path(datastore, disk_id))

        resource = Disk(disk_id)
        resource.flavor = Flavor("default")
        resource.persistent = False
        resource.new_disk = False
        # Every one byte in fake world equals to one G byte in real world
        resource.capacity_gb = size
        resource.image = None
        resource.datastore = datastore
        return resource

    def used_storage(self, datastore):
        sum = 0
        if os.path.isdir(self._datastore_path(datastore)):
            for disk in os.listdir(self._datastore_path(datastore)):
                sum += os.path.getsize(self._disk_path(datastore, disk[:-5]))
        return sum

    def _assert_exists(self, name, datastore):
        if not os.path.exists(self._disk_path(datastore, name)):
            self._logger.warning("disk in datastore (%s,%s) doesn't exist" %
                                 (name, datastore))
            raise DiskNotFoundException("ENOENT")

    def _assert_not_exists(self, name, datastore):
        if os.path.exists(self._disk_path(datastore, name)):
            self._logger.warning("disk in datastore (%s:%s) already exists" %
                                 (name, datastore))
            raise DiskAlreadyExistException("EEXISTS")

    def _disk_path(self, datastore, name):
        return os.path.join(self.disk_path, datastore, name + ".vmdk")

    def _datastore_path(self, datastore):
        return os.path.join(self.disk_path, datastore)

    def _datastore_capacity(self, datastore):
        if self.capacity_map and datastore in self.capacity_map:
            return self.capacity_map[datastore]
        else:
            return sys.maxint

    def _check_free_space(self, datastore, size):
        if self.used_storage(datastore) + size > self._datastore_capacity(
                datastore):
            raise DatastoreOutOfSpaceException()

    def _check_disk(self, name, datastore):
        return os.path.exists(self._disk_path(datastore, name))

    def disk_size(self, datastore, name):
        return os.path.getsize(self._disk_path(datastore, name))
