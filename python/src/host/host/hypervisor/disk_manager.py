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

import abc


class DiskFileException(Exception):
    pass


class DiskPathException(Exception):
    pass


class DiskAlreadyExistException(DiskFileException):
    pass


class DatastoreOutOfSpaceException(Exception):
    pass


class DiskManager(object):
    """A class that wraps hypervisor specific disk management code."""
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def create_disk(self, datastore, disk_id, size):
        """Create a new virtual disk.

        :param datastore: The datastore name
        :type datastore: str
        :param disk_id: The disk id
        :param disk_id: str
        :param size: Capacity of the disk in kilobytes.
        :type size: int
        """
        pass

    @abc.abstractmethod
    def delete_disk(self, datastore, disk_id):
        """Delete an existing virtual disk.

        :param datastore: The datastore name
        :type datastore: str
        :param disk_id: The disk id
        :type disk_id: str
        """
        pass

    @abc.abstractmethod
    def move_disk(self, source_datastore, source_id,
                  dest_datastore, dest_id):
        """Move a virtual disk.

        :param source_datastore: The source disk datastore
        :type source_datastore: str
        :param source_id: The id of the source disk
        :type source_id: str
        :param dest_datastore: The destination disk datastore
        :type dest_datastore: str
        :param dest_id: The id of the destination disk
        :type dest_id: str
        """
        pass

    @abc.abstractmethod
    def copy_disk(self, source_datastore, source_id,
                  dest_datastore, dest_id):
        """Copy a virtual disk.

        :param source_datastore: The source disk datastore
        :type source_datastore: str
        :param source_id: The id of the source disk
        :type source_id: str
        :param dest_datastore: The destination disk datastore
        :type dest_datastore: str
        :param dest_id: The id of the destination disk
        :type dest_id: str
        """
        pass

    @abc.abstractmethod
    def get_datastore(self, disk_id):
        pass

    @abc.abstractmethod
    def get_resource(self, disk_id):
        """Get a Disk resource

        :type disk_id: str
        :rtype: resources.Disk
        """
        pass
