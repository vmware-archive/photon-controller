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

import errno
import json
import logging

import time

import os.path
import shutil
import uuid

from gen.resource.ttypes import DatastoreType

from common.file_lock import AcquireLockFailure
from common.file_lock import FileBackedLock
from common.file_lock import InvalidFile
from common.file_util import rm_rf
from common.thread import Periodic
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import TMP_IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import os_datastore_root
from host.hypervisor.esx.path_util import datastore_to_os_path
from host.hypervisor.esx.path_util import metadata_filename
from host.hypervisor.esx.path_util import list_top_level_directory
from host.hypervisor.esx.path_util import COMPOND_PATH_SEPARATOR
from host.hypervisor.esx.path_util import image_directory_path
from host.hypervisor.esx.path_util import os_datastore_path
from host.hypervisor.esx.path_util import os_metadata_path
from host.hypervisor.esx.path_util import os_to_datastore_path
from host.hypervisor.esx.path_util import os_vmdk_flat_path
from host.hypervisor.esx.path_util import os_vmdk_path
from host.hypervisor.esx.path_util import vmdk_path
from host.hypervisor.exceptions import DiskAlreadyExistException
from host.placement.placement_manager import NoSuchResourceException
from host.placement.placement_manager import ResourceType

from common.log import log_duration

GC_IMAGE_FOLDER = "deleted_images"


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


class DirectoryNotFound(Exception):
    """
    Exception thrown when the specified directory is not found
    """


class ImageManager():
    NUM_MAKEDIRS_ATTEMPTS = 10
    DEFAULT_TMP_IMAGES_CLEANUP_INTERVAL = 600.0
    REAP_TMP_IMAGES_GRACE_PERIOD = 2 * 60.0 * 60.0  # 2 hrs
    DELETE_IMAGE_GRACE_PERIOD = 60
    UNUSED_IMAGE_MARKER_FILE_NAME = "unused_image_marker.txt"
    IMAGE_TIMESTAMP_FILE_NAME = "image_timestamp.txt"

    def __init__(self, host_client, ds_manager):
        self._logger = logging.getLogger(__name__)
        self._host_client = host_client
        self._ds_manager = ds_manager
        self._image_reaper = None

    def monitor_for_cleanup(self, reap_interval=DEFAULT_TMP_IMAGES_CLEANUP_INTERVAL):
        self._image_reaper = Periodic(self.reap_tmp_images, reap_interval)
        self._image_reaper.daemon = True
        self._image_reaper.start()

    def cleanup(self):
        if self._image_reaper is not None:
            self._image_reaper.stop()

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

    @log_duration
    def check_image(self, image_id, datastore):
        image_dir = os_vmdk_path(datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)
        try:
            return os.path.exists(image_dir)
        except:
            self._logger.exception("Error looking up %s" % image_dir)
            return False

    """
    The following method is intended as a replacement of check_image in
    the vm creation workflow compatible with the new image sweeper.
    For an image to be valid both the directory and the image timestamp
    file must exists on the datastore.
    """
    def check_and_validate_image(self, image_id, ds_id):
        image_dir = os.path.dirname(os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))

        try:
            if not os.path.exists(image_dir):
                return False
        except:
            self._logger.exception("Error looking up %s" % image_dir)
            return False

        # Check the existence of the timestamp file
        timestamp_pathname = os.path.join(image_dir, self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            if os.path.exists(timestamp_pathname):
                return True
        except Exception as ex:
            self._logger.exception("Exception looking up %s, %s" % (timestamp_pathname, ex))
            return False

        return False

    """
    This method is used to update the mod time on the image timestamp file.
    """
    def touch_image_timestamp(self, ds_id, image_id):

        image_path = os.path.dirname(os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))

        # Touch the timestamp file
        timestamp_pathname = os.path.join(image_path, self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            os.utime(timestamp_pathname, None)
        except Exception as ex:
            self._logger.exception("Exception looking up %s, %s" % (timestamp_pathname, ex))
            raise ex

    @log_duration
    def check_image_dir(self, image_id, datastore):
        image_path = os_vmdk_path(datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)
        try:
            return os.path.exists(os.path.dirname(image_path))
        except:
            self._logger.error(
                "Error looking up %s" % image_path, exc_info=True)
            return False

    def get_image_directory_path(self, datastore_id, image_id):
        return image_directory_path(datastore_id, image_id)

    def get_image_path(self, datastore_id, image_id):
        return os_vmdk_path(datastore_id, image_id, IMAGE_FOLDER_NAME_PREFIX)

    def image_size(self, image_id):
        for image_ds in self._ds_manager.image_datastores():
            if self._ds_manager.datastore_type(image_ds) is DatastoreType.VSAN:
                if os.path.exists(os_vmdk_path(image_ds, image_id, IMAGE_FOLDER_NAME_PREFIX)):
                    # VSAN does not have flat.vmdk so we cannot get file size. Default to 1GB.
                    return 1024 ** 3
            else:
                try:
                    image_path = os_vmdk_flat_path(image_ds, image_id, IMAGE_FOLDER_NAME_PREFIX)
                    return os.path.getsize(image_path)
                except os.error:
                    pass
            self._logger.info("Image %s not found in DataStore %s" % (image_id, image_ds))

        self._logger.warning("Failed to get image size:", exc_info=True)
        # Failed to access shared image.
        raise NoSuchResourceException(ResourceType.IMAGE, "Image does not exist.")

    def get_image_metadata(self, image_id, datastore):
        metadata_path = os_metadata_path(datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)
        self._logger.info("Loading metadata %s" % metadata_path)
        if os.path.exists(metadata_path):
            with open(metadata_path) as fh:
                try:
                    return json.load(fh)
                except ValueError:
                    self._logger.error("Error loading metadata file %s" % metadata_path, exc_info=True)
        return {}

    def _get_datastore_type(self, datastore_id):
        datastores = self._ds_manager.get_datastores()
        return [ds.type for ds in datastores if ds.id == datastore_id][0]

    def _copy_to_tmp_image(self, source_datastore, source_id, dest_datastore, dest_id):
        """ Copy an image into a temp location.
            1. Lock a tmp image destination file with an exclusive lock. This
            is to prevent the GC thread from garbage collecting directories
            that are actively being used.
            The temp directory name contains a random UUID to prevent
            collisions with concurrent copies
            2. Create the temp directory.
            3. Copy the metadata file over.
            4. Copy the vmdk over.

            @return the tmp image directory on success.
        """
        ds_type = self._get_datastore_type(dest_datastore)
        if ds_type == DatastoreType.VSAN:
            tmp_image_dir = os_datastore_path(dest_datastore,
                                              compond_path_join(IMAGE_FOLDER_NAME_PREFIX, dest_id),
                                              compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, str(uuid.uuid4())))
        else:
            tmp_image_dir = os_datastore_path(dest_datastore,
                                              compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, str(uuid.uuid4())))

        # Create the temp directory
        self._host_client.make_directory(tmp_image_dir)

        # Copy the metadata file if it exists.
        source_meta = os_metadata_path(source_datastore, source_id, IMAGE_FOLDER_NAME_PREFIX)
        if os.path.exists(source_meta):
            try:
                dest_meta = os.path.join(tmp_image_dir, metadata_filename(dest_id))
                shutil.copy(source_meta, dest_meta)
            except:
                self._logger.exception("Failed to copy metadata file %s", source_meta)
                raise

        # Create the timestamp file
        self._create_image_timestamp_file(tmp_image_dir)

        self._host_client.copy_disk(vmdk_path(source_datastore, source_id, IMAGE_FOLDER_NAME_PREFIX),
                                    os.path.join(tmp_image_dir, "%s.vmdk" % dest_id))
        return tmp_image_dir

    def _move_image(self, image_id, datastore, tmp_dir):
        """
        Atomic move of a tmp folder into the image datastore. Handles
        concurrent moves by locking a well know derivative of the image_id
        while doing the atomic move.
        The exclusive file lock ensures that only one move is successful.
        Has the following side effects:
            a - If the destination image already exists, it is assumed that
            someone else successfully copied the image over and the temp
            directory is deleted.
            b - If we fail to acquire the file lock after retrying 3 times,
            or the atomic move fails, the tmp image directory will be left
            behind and needs to be garbage collected later.

        image_id: String.The image id of the image being moved.
        datastore: String. The datastore id of the datastore.
        tmp_dir: String. The absolute path of the temp image directory.

        raises: OsError if the move fails
                AcquireLockFailure, InvalidFile if we fail to lock the
                destination image.
        """
        ds_type = self._get_datastore_type(datastore)
        image_path = os_datastore_path(datastore, compond_path_join(IMAGE_FOLDER_NAME_PREFIX, image_id))
        self._logger.info("_move_image: %s => %s, ds_type: %s" % (tmp_dir, image_path, ds_type))

        if not os.path.exists(tmp_dir):
            raise ImageNotFoundException("Temp image %s not found" % tmp_dir)

        try:
            with FileBackedLock(image_path, ds_type, retry=300, wait_secs=0.1):  # wait lock for 30 seconds
                if self._check_image_repair(image_id, datastore):
                    raise DiskAlreadyExistException("Image already exists")

                if ds_type == DatastoreType.VSAN:
                    # on VSAN, move all files under [datastore]/image_[image_id]/tmp_image_[uuid]/* to
                    # [datastore]/image_[image_id]/*.
                    # Also we do not delete tmp_image folder in success case, because VSAN accesses it
                    # when creating linked VM, even the folder is now empty.
                    for entry in os.listdir(tmp_dir):
                        shutil.move(os.path.join(tmp_dir, entry), os.path.join(image_path, entry))
                else:
                    # on VMFS/NFS/etc, rename [datastore]/tmp_image_[uuid] to [datastore]/tmp_image_[image_id]
                    self._host_client.move_file(tmp_dir, image_path)
        except:
            self._logger.exception("Move image %s to %s failed" % (image_id, image_path))
            self._host_client.delete_file(tmp_dir)
            raise

    """
    The following method should be used to check and validate the existence of a previously
    created image. With the new image delete path the "timestamp" file must exists inside the
    image directory. If the directory exists and the file does not, it may mean that an image
    delete operation was aborted mid-way. In this case the following method recreate the timestamp
    file. All operations are performed while holding the image directory lock (FileBackedLock),
    the caller is required to hold the lock.
    """
    def _check_image_repair(self, image_id, datastore):
        vmdk_pathname = os_vmdk_path(datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)

        image_dirname = os.path.dirname(vmdk_pathname)
        try:
            # Check vmdk file
            if not os.path.exists(vmdk_pathname):
                self._logger.info("Vmdk path doesn't exists: %s" % vmdk_pathname)
                return False
        except Exception as ex:
            self._logger.exception("Exception validating %s, %s" % (image_dirname, ex))
            return False

        # Check timestamp file
        timestamp_pathname = os.path.join(image_dirname, self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            if os.path.exists(timestamp_pathname):
                self._logger.info("Timestamp file exists: %s" % timestamp_pathname)
                return True
        except Exception as ex:
            self._logger.exception("Exception validating %s, %s" % (timestamp_pathname, ex))

        # The timestamp file is not accessible, try creating one
        try:
            self._create_image_timestamp_file(image_dirname)
        except Exception as ex:
            self._logger.exception("Exception creating %s, %s" % (timestamp_pathname, ex))
            return False

        self._logger.info("Image repaired: %s" % image_dirname)
        return True

    def copy_image(self, source_datastore, source_id, dest_datastore, dest_id):
        """Copy an image between datastores.

        This method is used to create a "full clone" of a vmdk.
        It does so by copying a disk to a unique directory in a well known
        temporary directory then moving the disk to the destination image
        location. Data in the temporary directory not properly cleaned up
        will be periodically garbage collected by the reaper thread.

        This minimizes the window during which the vmdk path exists with
        incomplete content. It also works around a hostd issue where
        cp -f does not work.

        The current behavior for when the destination disk exists is
        to overwrite said disk.

        source_datastore: id of the source datastore
        source_id: id of the image to copy from
        dest_datastore: id of the destination datastore
        dest_id: id of the new image in the destination datastore

        throws: AcquireLockFailure if timed out waiting to acquire lock on tmp
                image directory
        throws: InvalidFile if unable to lock tmp image directory or some other
                reasons
        """
        if self.check_and_validate_image(dest_id, dest_datastore):
            # The image is copied, presumably via some other concurrent
            # copy, so we move on.
            self._logger.info("Image %s already copied" % dest_id)
            raise DiskAlreadyExistException("Image already exists")

        # Copy image to the tmp directory.
        tmp_dir = self._copy_to_tmp_image(source_datastore, source_id, dest_datastore, dest_id)

        self._move_image(dest_id, dest_datastore, tmp_dir)

    def reap_tmp_images(self):
        """ Clean up unused directories in the temp image folder. """
        for ds in self._ds_manager.get_datastores():
            for image_dir in list_top_level_directory(ds.id, TMP_IMAGE_FOLDER_NAME_PREFIX):
                if not os.path.isdir(image_dir):
                    continue

                create_time = os.stat(image_dir).st_ctime
                current_time = time.time()
                if current_time - self.REAP_TMP_IMAGES_GRACE_PERIOD < create_time:
                    # Skip folders that are newly created in past x minutes
                    # For example, during host-to-host transfer, hostd on
                    # receiving end stores the uploaded file in temp images
                    # folder but does not lock it with FileBackedLock, so we
                    # need to allow a grace period before reaping it.
                    self._logger.info("Skip folder: %s, created: %s, now: %s" % (image_dir, create_time, current_time))
                    continue

                try:
                    with FileBackedLock(image_dir, ds.type):
                        if os.path.exists(image_dir):
                            self._logger.info("Delete folder %s" % image_dir)
                            shutil.rmtree(image_dir, ignore_errors=True)
                except (AcquireLockFailure, InvalidFile):
                    self._logger.info("Already locked: %s, skipping" % image_dir)
                except:
                    self._logger.info("Unable to remove %s" % image_dir, exc_info=True)

    def get_images(self, datastore):
        """ Get image list from datastore
        :param datastore: datastore id
        :return: list of string, image id list
        """
        image_ids = []

        if not os.path.exists(os_datastore_root(datastore)):
            raise DatastoreNotFoundException()

        # image_folder is /vmfs/volumes/${datastore}/images_*
        for dir in list_top_level_directory(datastore, IMAGE_FOLDER_NAME_PREFIX):
            image_id = dir.split(COMPOND_PATH_SEPARATOR)[1]
            if self.check_image(image_id, datastore):
                image_ids.append(image_id)

        return image_ids

    def get_datastore_id_from_path(self, image_path):
        """Extract datastore id from the absolute path of an image.

        The image path looks something like this:

            /vmfs/volumes/datastore1/image_ttylinux/ttylinux.vmdk

        This method returns "datastore1" with this input.
        """
        return image_path.split(os.sep)[3]

    def get_image_id_from_path(self, image_path):
        """Extract image id from the absolute path of an image.

        The image path looks something like this:

            /vmfs/volumes/datastore1/images_ttylinux/ttylinux.vmdk

        This method returns "ttylinux" with this input.
        """
        return image_path.split(os.sep)[4].split(COMPOND_PATH_SEPARATOR)[1]

    def create_image(self, image_id, datastore_id):
        """ Create a temp image on given datastore, return its path.
        """
        datastore_type = self._get_datastore_type(datastore_id)
        if datastore_type == DatastoreType.VSAN:
            # on VSAN, tmp_dir is [datastore]/image_[image_id]/tmp_image_[uuid]
            # Because VSAN does not allow moving top-level directories, we place tmp_image
            # under image's dir.
            relative_path = os.path.join(compond_path_join(IMAGE_FOLDER_NAME_PREFIX, image_id),
                                         compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, str(uuid.uuid4())))
            tmp_dir = os_datastore_path(datastore_id, relative_path)
        else:
            # on VMFS/NFS/etc, tmp_dir is [datastore]/tmp_image_[uuid]
            tmp_dir = os_datastore_path(datastore_id,
                                        compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, str(uuid.uuid4())))

        self._host_client.make_directory(tmp_dir)
        # return datastore path, so that it can be passed to nfc client
        return os_to_datastore_path(tmp_dir)

    def finalize_image(self, datastore_id, tmp_dir, image_id):
        """ Installs an image using image data staged at a temp directory.
        """
        self._move_image(image_id, datastore_id, datastore_to_os_path(tmp_dir))
        self._create_image_timestamp_file(self._image_directory(datastore_id, image_id))

    def create_image_with_vm_disk(self, datastore_id, tmp_dir, image_id,
                                  vm_disk_os_path):
        """ Fills a temp image directory with a disk from a VM,
            then installs directory in the shared image folder.
        """
        # Create parent directory as required by CopyVirtualDisk_Task
        dst_vmdk_path = os.path.join(datastore_to_os_path(tmp_dir), "%s.vmdk" % image_id)
        if os.path.exists(dst_vmdk_path):
            self._logger.warning("Unexpected disk %s present, overwriting" % dst_vmdk_path)

        self._host_client.copy_disk(vm_disk_os_path, dst_vmdk_path)

        try:
            self.finalize_image(datastore_id, tmp_dir, image_id)
        except:
            self._logger.warning("Delete copied disk %s" % dst_vmdk_path)
            self._host_client.delete_disk(dst_vmdk_path)
            raise

    def delete_tmp_dir(self, datastore_id, tmp_dir):
        """ Deletes a temp image directory by moving it to a GC directory """
        file_path = os_datastore_path(datastore_id, tmp_dir)
        if not os.path.exists(file_path):
            self._logger.info("Tmp dir %s not" % file_path)
            raise DirectoryNotFound("Directory %s not found" % file_path)
        rm_rf(file_path)

    @staticmethod
    def _read_marker_file(filename):
        with open(filename, "r") as marker_file:
            start_time_str = marker_file.read()
        return float(start_time_str)

    """
    Delete a single image following the delete image steps. This
    method is supposed to be safe when run concurrently with:
    a) itself,
    b) image creation/copy,
    c) vm creation

    The steps are outlined here:
    1) Read content of the unused_image_marker file. If error, move on to next image.
    2) Acquire image-lock.
    3) Read the mod time on the t-stamp file. If t-stamp file doesn't exist go to 6.
    4) If the mod time of the t-stamp file is newer than the content of the marker
       file move on to next image.
    5) Move the t-stamp file to another name.
    6) Check the mod time on the new name of the t-stamp file. if the mod time has
       changed, move on to next image.
    7) move image directory to a trash location

    This method returns True if the image was removed, False if the image could not be removed.
    """

    def delete_image(self, datastore_id, image_id, grace_period):
        self._logger.info("delete_image: Starting to delete image: %s, %s" % (datastore_id, image_id))

        image_dir = self._image_directory(datastore_id, image_id)
        ds_type = self._get_datastore_type(datastore_id)
        marker_pathname = os.path.join(image_dir, self.UNUSED_IMAGE_MARKER_FILE_NAME)
        timestamp_pathname = os.path.join(image_dir, self.IMAGE_TIMESTAMP_FILE_NAME)

        try:
            with FileBackedLock(image_dir, ds_type):
                # Read marker file to determine when image scanner marked this image as unused
                marker_time = self._read_marker_file(marker_pathname)
                self._logger.info("delete_image: image was marked as unused at: %s" % marker_time)
                # Subtract grace time to avoid errors due to small difference in clock
                # values on different hosts. Pretend the scan started 60 seconds earlier.
                marker_time -= grace_period

                # Read timestamp mod_time to determine the latest vm creation using this image
                timestamp_exists, mod_time = self._get_mod_time(timestamp_pathname)
                self._logger.info("delete_image: image was last touched at: %s, %s" % (timestamp_exists, mod_time))

                # Image was touched (due to VM creation) after scanner marked it as unused.
                # Remove unused image marker file
                if timestamp_exists and mod_time >= marker_time:
                    self._logger.info("delete_image: image is in-use, do not delete")
                    os.unlink(marker_pathname)
                    return False

                # Delete image directory
                self._logger.info("delete_image: removing image directory: %s" % image_dir)
                self._host_client.delete_file(image_dir)

            return True
        except Exception:
            self._logger.exception("delete_image: failed to delete image")
            return False

    # Read the mod time on a file, returns two values, a boolean which is set to true if the
    # file exists, otherwise set to false and the mod time of the existing file
    def _get_mod_time(self, pathname):
        try:
            mod_time = os.path.getmtime(pathname)
        except OSError as ex:
            self._logger.warning("Cannot read mod time for file: %s, %s" % (pathname, ex))
            if ex.errno == errno.ENOENT:
                return False, 0
            else:
                raise ex
        return True, mod_time

    def get_timestamp_mod_time_from_dir(self, dirname):
        filename = os.path.join(dirname, self.IMAGE_TIMESTAMP_FILE_NAME)
        return self._get_mod_time(filename)

    def _create_image_timestamp_file(self, dirname):
        try:
            timestamp_pathname = os.path.join(dirname, self.IMAGE_TIMESTAMP_FILE_NAME)
            open(timestamp_pathname, 'w').close()
        except Exception as ex:
            self._logger.exception("Exception creating %s, %s" % (dirname, ex))
            raise ex

    def _image_directory(self, datastore_id, image_id):
        return os.path.dirname(os_vmdk_path(datastore_id, image_id, IMAGE_FOLDER_NAME_PREFIX))
