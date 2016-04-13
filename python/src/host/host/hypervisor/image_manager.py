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


class ImageNotFoundException(Exception):
    """ Exception thrown when image is not found on the datastore """
    pass


class ImageInUse(Exception):
    """ Exception thrown when we attempt to delete an in use image """
    pass


class InvalidImageUpdate(Exception):
    """
    Exception thrown when we attempt to transition a tombstoned image to a
    non tombstoned image
    """
    pass


class InvalidImageState(Exception):
    """
    Exception thrown if the image is being manipulated in an invalid state
    """
    pass


class DirectoryNotFound(Exception):
    """
    Exception thrown when the specified directory is not found
    """


class ImageManager(object):
    """A class that wraps hypervisor specific image management.

    Clarification of a few terms:

    Image metadata: Contains Vm information that would pass to .vmx when a Vm
                    is created. The information is saved in .ecv file located
                    together with the image in every datastore.

    """
    __metaclass__ = abc.ABCMeta

    def __init__(self):
        pass

    def datastores_with_image(self, image_id, datastores):
        if image_id is None:
            return []
        return [ds for ds in datastores if self.check_image(image_id, ds)]

    def image_metadata(self, image_id, datastores):
        for ds in datastores:
            if self.check_image(image_id, ds):
                return self.get_image_metadata(image_id, ds)

    @staticmethod
    def get_image_id_from_disks(disks):
        """Find image id in the disk collection"""
        if not disks:
            return None

        for disk in disks:
            try:
                if disk.image.id is not None:
                    return disk.image.id
            except AttributeError:
                continue
        return None

    @abc.abstractmethod
    def check_image(self, image_id, datastore_id):
        pass

    @abc.abstractmethod
    def check_and_validate_image(self, image_id, datastore_id):
        """
        :param image_id:
        :param datastore_id:
        :return: boolean, True if the image is valid and
        the timestamp file is found

        This method is intended
        as a replacement for check_image() in
        the vm creation workflow compatible
        with the new image sweeper.
        For an image to be valid both the
        directory and the image timestamp
        file must exists on the datastore.
        """
        pass

    @abc.abstractmethod
    def copy_image(self, src_datastore, src_id, dst_datastore, dst_id):
        pass

    @abc.abstractmethod
    def delete_image(self, datastore_id, image_id, ds_type, force):
        """
        Delete an image from a datastore of a given type
        datastore_id: The datastore id of the datastore
        image_id: The id of the image to delete.
        ds_type: The thrift datastore type.
        force: boolean indicating force delete.
        @throws ImageInUse if force is true and the image is inuse.
        """
        pass

    @abc.abstractmethod
    def get_image_metadata(self, image_id, datastore):
        pass

    @abc.abstractmethod
    def get_image_directory_path(self, datastore_id, image_id):
        """Get absolute path of the image directory.

        Args:
            datastore_id: datastore where the image is located.
            image_id: image id as a string.

        Returns:
            The absolute path of the image directory as a string.
        """
        pass

    @abc.abstractmethod
    def get_image_path(self, datastore_id, image_id):
        """Get absolute path of the image vmdk.

        Args:
            datastore_id: datastore where the image is located.
            image_id: image id as a string.

        Returns:
            The absolute path of the image vmdk as a string.
        """
        pass

    @abc.abstractmethod
    def get_datastore_id_from_path(self, image_path):
        """Extract the datastore ID from the absolute path of an image.

        Args:
            image_path: absolute path to the image.

        Returns:
            datastore id.
        """
        pass

    @abc.abstractmethod
    def get_image_id_from_path(self, image_path):
        """Extract the image ID from the absolute path of an image.

        Args:
            image_path: absolute path to the image.

        Returns:
            image id.
        """
        pass

    @abc.abstractmethod
    def get_timestamp_mod_time_from_dir(self, dirname, renamed=False):
        """
        :param dirname:
        :return: a boolean and the mod time of timestamp file
        """
        pass

    @abc.abstractmethod
    def get_tombstone_mod_time_from_dir(self, dirname):
        """
        :param dirname:
        :return: a boolean and the mod time of timestamp file
        """
        pass

    @abc.abstractmethod
    def get_images(self, datastore):
        """ Get all images from datastore
        :param datastore: datastore id
        :return: list of string: list of image ids
        """
        pass

    @abc.abstractmethod
    def image_size(self, image_id):
        pass

    @abc.abstractmethod
    def touch_image_timestamp(self, ds_id, image_id):
        """
        Update Image timestamp, mod file. Throws exception
        if the image doesn't exists or the image has
        been tombstoned
        :param dsid:
        :param image_id:
        :return:
        """

    @abc.abstractmethod
    def create_image_tombstone(self, ds_id, image_id):
        """
        Create tombstone file for image. Throws exception
        if the image doesn't exists
        :param dsid:
        :param image_id:
        :return:
        """

    @abc.abstractmethod
    def create_image(self, image_id, datastore_id):
        """ Create a temp image on given datastore, return its path.
        """
        pass

    @abc.abstractmethod
    def finalize_image(self, datastore_id, tmp_dir, image_id):
        """ Finalize image creation by doing an atomic move from a tmp
            folder location.
        """
        pass

    @abc.abstractmethod
    def create_image_with_vm_disk(self, datastore_id, tmp_dir, image_id,
                                  vm_disk_os_path):
        """ Fills a temp image directory with a disk from a VM,
            then installs directory in the shared image folder.
        """
        pass

    @abc.abstractmethod
    def delete_tmp_dir(self, datastore_id, tmp_dir):
        """ Delete the temp image directory.
        Does not handle the case for concurrent deltes of the same directory
        across hosts.
        """
        pass

    @abc.abstractmethod
    def transfer_image(self, source_image_id, source_datastore,
                       destination_image_id, destination_datastore,
                       destination_host, destination_port):
        """ Host to host image transfer.
        """
        pass
