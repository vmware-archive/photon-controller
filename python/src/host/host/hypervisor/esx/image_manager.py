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

"""Temporary hack to deploy demo image from vib to datastore"""

import errno
import glob
import gzip
import json
import logging

import time

import os.path
import shutil
import uuid

from pyVmomi import vim

from common.file_io import AcquireLockFailure
from common.file_io import FileBackedLock
from common.file_io import InvalidFile
from common.file_util import mkdir_p
from common.file_util import rm_rf
from common import services
from common.service_name import ServiceName
from common.thread import Periodic
from gen.resource.ttypes import ImageReplication
from gen.resource.ttypes import ImageType
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import TMP_IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import os_datastore_root
from host.hypervisor.esx.vm_config import datastore_to_os_path
from host.hypervisor.esx.vm_config import metadata_filename
from host.hypervisor.esx.vm_config import manifest_filename
from host.hypervisor.esx.vm_config import SUPPORT_VSAN
from host.hypervisor.esx.vm_config import os_datastore_path_pattern
from host.hypervisor.esx.vm_config import COMPOND_PATH_SEPARATOR
from host.hypervisor.esx.vm_config import vmdk_add_suffix
from host.hypervisor.esx.vm_config import image_directory_path
from host.hypervisor.esx.vm_config import os_datastore_path
from host.hypervisor.esx.vm_config import os_image_manifest_path
from host.hypervisor.esx.vm_config import os_metadata_path
from host.hypervisor.esx.vm_config import os_to_datastore_path
from host.hypervisor.esx.vm_config import os_vmdk_flat_path
from host.hypervisor.esx.vm_config import os_vmdk_path
from host.hypervisor.esx.vm_config import tmp_image_path
from host.hypervisor.esx.vm_config import vmdk_path
from host.hypervisor.image_manager import DirectoryNotFound
from host.hypervisor.image_manager import ImageManager
from host.hypervisor.image_manager import ImageInUse
from host.hypervisor.image_manager import ImageNotFoundException
from host.hypervisor.image_manager import InvalidImageState
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.disk_manager import DiskFileException
from host.hypervisor.disk_manager import DiskPathException
from host.hypervisor.image_scanner import waste_time
from host.hypervisor.placement_manager import NoSuchResourceException
from host.hypervisor.placement_manager import ResourceType

from common.log import log_duration

GC_IMAGE_FOLDER = "deleted_images"


class EsxImageManager(ImageManager):
    NUM_MAKEDIRS_ATTEMPTS = 10
    DEFAULT_TMP_IMAGES_CLEANUP_INTERVAL = 600.0
    REAP_TMP_IMAGES_GRACE_PERIOD = 600.0
    IMAGE_TOMBSTONE_FILE_NAME = "image_tombstone.txt"
    IMAGE_MARKER_FILE_NAME = "unused_image_marker.txt"
    IMAGE_TIMESTAMP_FILE_NAME = "image_timestamp.txt"
    IMAGE_TIMESTAMP_FILE_RENAME_SUFFIX = ".renamed"

    def __init__(self, vim_client, ds_manager):
        super(EsxImageManager, self).__init__()
        self._logger = logging.getLogger(__name__)
        self._vim_client = vim_client
        self._ds_manager = ds_manager
        self._image_reaper = None
        self._uwsim_nas_exist = None
        agent_config = services.get(ServiceName.AGENT_CONFIG)
        self._in_uwsim = agent_config.in_uwsim

    def monitor_for_cleanup(self,
                            reap_interval=DEFAULT_TMP_IMAGES_CLEANUP_INTERVAL):
        self._image_reaper = Periodic(self.reap_tmp_images, reap_interval)
        self._image_reaper.daemon = True
        self._image_reaper.start()

    def cleanup(self):
        if self._image_reaper is not None:
            self._image_reaper.stop()

    @log_duration
    def check_image(self, image_id, datastore):
        image_dir = os_vmdk_path(datastore, image_id, IMAGE_FOLDER_NAME_PREFIX)
        try:
            return os.path.exists(image_dir)
        except:
            self._logger.exception(
                "Error looking up %s" % image_dir)
            return False

    """
    The following method is intended
    as a replacement of check_image in
    the vm creation workflow compatible
    with the new image sweeper.
    For an image to be valid both the
    directory and the image timestamp
    file must exists on the datastore.
    """
    def check_and_validate_image(self, image_id, ds_id):
        image_dir = os.path.dirname(
            os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))

        try:
            if not os.path.exists(image_dir):
                return False
        except:
            self._logger.exception(
                "Error looking up %s" % image_dir)
            return False

        # Check the existence of the timestamp file
        timestamp_pathname = \
            os.path.join(image_dir,
                         self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            if os.path.exists(timestamp_pathname):
                return True
        except Exception as ex:
            self._logger.exception(
                "Exception looking up %s, %s" % (timestamp_pathname, ex))
            return False

        return False

    """
    This method is used to update the mod time on the
    image timestamp file. It also checks for the existence
    of a tombstone file for this image. If the tombstone
    file exists it throws an exception.
    """
    def touch_image_timestamp(self, ds_id, image_id):
        """
        :param ds_id:
        :param image_id:
        :return:
        """
        image_path = os.path.dirname(
            os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))

        # Check the existence of the timestamp file
        tombstone_pathname = \
            os.path.join(image_path,
                         self.IMAGE_TOMBSTONE_FILE_NAME)
        try:
            tombstone = os.path.exists(tombstone_pathname)
        except Exception as ex:
            self._logger.exception(
                "Exception looking up %s, %s" % (tombstone_pathname, ex))

        if tombstone:
            raise InvalidImageState

        # Touch the timestamp file
        timestamp_pathname = \
            os.path.join(image_path,
                         self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            os.utime(timestamp_pathname, None)
        except Exception as ex:
            self._logger.exception(
                "Exception looking up %s, %s" % (timestamp_pathname, ex))
            raise ex

    """
    This method is used to create a tombstone marker
    in the new image management work flow. The tombstone
    marker is a file under the image directory.
    """
    def create_image_tombstone(self, ds_id, image_id):
        """
        :param ds_id:
        :param image_id:
        :return:
        """
        image_path = os.path.dirname(
            os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))

        # Create tombstone file for the image
        tombstone_pathname = \
            os.path.join(image_path,
                         self.IMAGE_TOMBSTONE_FILE_NAME)
        try:
            open(tombstone_pathname, 'w').close()
        except Exception as ex:
            self._logger.exception(
                "Exception creating %s, %s" % (tombstone_pathname, ex))
            raise ex

        self._logger.info("Image: %s tombstoned" % tombstone_pathname)

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
            try:
                image_path = os_vmdk_flat_path(image_ds, image_id,
                                               IMAGE_FOLDER_NAME_PREFIX)
                return os.path.getsize(image_path)
            except os.error:
                self._logger.info("Image %s not found in DataStore %s" %
                                  (image_id, image_ds))

        self._logger.warning("Failed to get image size:",
                             exc_info=True)
        # Failed to access shared image.
        raise NoSuchResourceException(
            ResourceType.IMAGE,
            "Image does not exist.")

    def _load_json(self, metadata_path):
        if os.path.exists(metadata_path):
            with open(metadata_path) as fh:
                try:
                    data = json.load(fh)
                    return data
                except ValueError:
                    self._logger.error(
                        "Error loading metadata file %s" % metadata_path,
                        exc_info=True)
        return {}

    def get_image_metadata(self, image_id, datastore):
        metadata_path = os_metadata_path(datastore,
                                         image_id,
                                         IMAGE_FOLDER_NAME_PREFIX)
        self._logger.info("Loading metadata %s" % metadata_path)
        return self._load_json(metadata_path)

    def get_image_manifest(self, image_id):
        # This is a shortcut for ttylinux. ttylinux doesn't have manifest file.
        if image_id == "ttylinux":
            return ImageType.CLOUD, ImageReplication.EAGER

        manifest_path = None
        for image_ds in self._ds_manager.image_datastores():
            path = os_image_manifest_path(image_ds, image_id)
            if os.path.isfile(path):
                manifest_path = path
                break

        if not manifest_path:
            self._logger.info("Manifest file %s not found" % manifest_path)
            return None, None

        self._logger.info("Loading manifest %s" % manifest_path)
        data = self._load_json(manifest_path)
        type = ImageType._NAMES_TO_VALUES[data["imageType"]]
        replication = ImageReplication._NAMES_TO_VALUES[
            data["imageReplication"]]

        return type, replication

    def _get_datastore_type(self, datastore_id):
        datastores = self._ds_manager.get_datastores()
        return [ds.type for ds in datastores if ds.id == datastore_id][0]

    def _prepare_virtual_disk_spec(self, disk_type, adapter_type):
        """
        :param disk_type [vim.VirtualDiskManager.VirtualDiskType]:
        :param adapter_type [vim.VirtualDiskManager.VirtualDiskAdapterType]:
        """
        _vd_spec = vim.VirtualDiskManager.VirtualDiskSpec()
        _vd_spec.diskType = str(disk_type)
        _vd_spec.adapterType = str(adapter_type)

        return _vd_spec

    def _create_tmp_image(self, source_datastore, source_id, dest_datastore,
                          dest_id):
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
        source = vmdk_path(source_datastore, source_id, IMAGE_FOLDER_NAME_PREFIX)
        temp_dest = tmp_image_path(dest_datastore, dest_id)
        ds_type = self._get_datastore_type(dest_datastore)
        tmp_image_dir_path = os.path.dirname(datastore_to_os_path(temp_dest))
        # Try grabbing the lock on the temp directory if it fails
        # (very unlikely) someone else is copying an image just retry
        # later.
        with FileBackedLock(tmp_image_dir_path, ds_type):
            source_meta = os_metadata_path(source_datastore, source_id,
                                           IMAGE_FOLDER_NAME_PREFIX)
            # Create the temp directory
            self._vim_client.make_directory(tmp_image_dir_path)

            # Copy the metadata file if it exists.
            if os.path.exists(source_meta):
                try:
                    dest_meta = os.path.join(tmp_image_dir_path,
                                             metadata_filename(dest_id))
                    shutil.copy(source_meta, dest_meta)
                except:
                    self._logger.exception("Failed to copy metadata file %s",
                                           source_meta)
                    raise

            # Copy the manifest file if it exists
            source_manifest = os_image_manifest_path(source_datastore,
                                                     source_id)
            if os.path.exists(source_manifest):
                try:
                    dest_manifest = os.path.join(tmp_image_dir_path,
                                                 manifest_filename(dest_id))
                    shutil.copy(source_manifest, dest_manifest)
                except:
                    # Swallow it. Not critical.
                    pass

            # Create the timestamp file
            self._create_image_timestamp_file(tmp_image_dir_path)

            _vd_spec = self._prepare_virtual_disk_spec(
                vim.VirtualDiskManager.VirtualDiskType.thin,
                vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic)

            self._manage_disk(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                              sourceName=source, destName=temp_dest,
                              destSpec=_vd_spec)
        return tmp_image_dir_path

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
        image_path = os.path.dirname(os_vmdk_path(datastore, image_id,
                                                  IMAGE_FOLDER_NAME_PREFIX))
        parent_path = os.path.dirname(image_path)
        # Create the parent image directory if it doesn't exist.
        if not os.path.exists(parent_path):
            try:
                self._vim_client.make_directory(parent_path)
            except vim.fault.FileAlreadyExists:
                pass

        try:
            with FileBackedLock(image_path, ds_type, retry=300,
                                wait_secs=0.01):  # wait lock for 3 seconds
                if self._check_image_repair(image_id, datastore):
                    raise DiskAlreadyExistException("Image already exists")

                self._vim_client.move_file(tmp_dir, image_path)
        except (AcquireLockFailure, InvalidFile):
            self._logger.info("Unable to lock %s for atomic move" % image_id)
            raise
        except DiskAlreadyExistException:
            self._logger.info("Image %s already copied" % image_id)
            rm_rf(tmp_dir)
            raise
        except:
            self._logger.exception("Move image %s to %s failed" % (image_id, image_path))
            raise

    """
    The following method should be used to check
    and validate the existence of a previously
    created image. With the new image delete path
    the "timestamp" file must exists inside the
    image directory. If the directory exists and
    the file does not, it may mean that an image
    delete operation was aborted mid-way. In this
    case the following method recreate the timestamp
    file. All operations are performed while
    holding the image directory lock (FileBackedLock),
    the caller is required to hold the lock.
    """
    def _check_image_repair(self, image_id, datastore):
        vmdk_pathname = os_vmdk_path(datastore,
                                     image_id,
                                     IMAGE_FOLDER_NAME_PREFIX)

        image_dirname = os.path.dirname(vmdk_pathname)
        try:
            # Check vmdk file
            if not os.path.exists(vmdk_pathname):
                self._logger.info("Vmdk path doesn't exists: %s" %
                                  vmdk_pathname)
                return False
        except Exception as ex:
            self._logger.exception(
                "Exception validating %s, %s" % (image_dirname, ex))
            return False

        # Check timestamp file
        timestamp_pathname = \
            os.path.join(image_dirname,
                         self.IMAGE_TIMESTAMP_FILE_NAME)
        try:
            if os.path.exists(timestamp_pathname):
                self._logger.info("Timestamp file exists: %s" %
                                  timestamp_pathname)
                return True
        except Exception as ex:
            self._logger.exception(
                "Exception validating %s, %s" % (timestamp_pathname, ex))

        # The timestamp file is not accessible,
        # try creating one, if successful try to
        # delete the renamed timestamp file if it
        # exists
        try:
            self._create_image_timestamp_file(image_dirname)
            self._delete_renamed_image_timestamp_file(image_dirname)
        except Exception as ex:
            self._logger.exception(
                "Exception creating %s, %s" % (timestamp_pathname, ex))
            return False

        self._logger.info("Image repaired: %s" %
                          image_dirname)
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
        tmp_dir = self._create_tmp_image(source_datastore, source_id,
                                         dest_datastore, dest_id)

        self._move_image(dest_id, dest_datastore, tmp_dir)

    def reap_tmp_images(self):
        """ Clean up unused directories in the temp image folder. """
        for ds in self._ds_manager.get_datastores():
            tmp_image_pattern = os_datastore_path_pattern(ds.id, TMP_IMAGE_FOLDER_NAME_PREFIX)
            for image_dir in glob.glob(tmp_image_pattern):
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
                    self._logger.info(
                        "Skip folder: %s, created: %s, now: %s" %
                        (image_dir, create_time, current_time))
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

    def delete_image(self, datastore_id, image_id, ds_type, force):
        # Check if the image currently exists
        if not self.check_image_dir(image_id, datastore_id):
            self._logger.info("Image %s on datastore %s not found" % (image_id,
                              datastore_id))
            raise ImageNotFoundException("Image %s not found" % image_id)

        # Mark image as tombstoned
        self.create_image_tombstone(datastore_id, image_id)

        if not force:
            return

        # If force try to actively garbage collect the image here
        if self._lock_data_disk(datastore_id, image_id):
            self._gc_image_dir(datastore_id, image_id)
        else:
            raise ImageInUse("Image %s is currently in use" % image_id)

        # Now attempt GCing the image directory.
        try:
            self._clean_gc_dir(datastore_id)
        except Exception:
            # Swallow the exception the next clean call will clear it all.
            self._logger.exception("Failed to delete gc dir on datastore %s" %
                                   datastore_id)

    def _lock_data_disk(self, datastore_id, image_id):
        """
        Lock the data disks associated with the VMs in the provided ref file.
        Return True if locking was successful false otherwise.
        """
        data_disk = os_vmdk_flat_path(datastore_id, image_id)
        try:
            # Its ok to delete the data disk as a subsequent power on will
            # fail if the data disk is not there.
            os.remove(data_disk)
        except OSError:
            # Remove failed so disk is locked.
            self._logger.debug("Disk %s on datastore %s is already locked"
                               % (data_disk, datastore_id))
            return False
        return True

    def get_images(self, datastore):
        """ Get image list from datastore
        :param datastore: datastore id
        :return: list of string, image id list
        """
        image_ids = []

        if SUPPORT_VSAN:
            if not os.path.exists(os_datastore_root(datastore)):
                raise DatastoreNotFoundException()

            # image_folder is /vmfs/volumes/${datastore}/images_*
            image_folder_pattern = os_datastore_path_pattern(datastore,
                                                             IMAGE_FOLDER_NAME_PREFIX)
            for dir in glob.glob(image_folder_pattern):
                image_id = dir.split(COMPOND_PATH_SEPARATOR)[1]
                if self.check_image(image_id, datastore):
                    image_ids.append(image_id)
        else:
            # image_folder is /vmfs/volumes/${datastore}/images
            image_folder = os_datastore_path(datastore, IMAGE_FOLDER_NAME_PREFIX)

            if not os.path.exists(image_folder):
                raise DatastoreNotFoundException()

            # prefix is the 2-digit prefix of image id
            for prefix in os.listdir(image_folder):
                # outer path is something like
                # /vmfs/volumes/${datastore}/images/${image_id}[0:2]
                outer_path = os.path.join(image_folder, prefix)
                if not os.path.isdir(outer_path):
                    continue

                for image_id in os.listdir(outer_path):
                    if self.check_image(image_id, datastore):
                        image_ids.append(image_id)

        return image_ids

    def mark_unused(self, image_scanner):
        images_dir_path = os_datastore_path(image_scanner.datastore_id,
                                            IMAGE_FOLDER_NAME_PREFIX)
        # Log messages with prefix: "IMAGE SCANNER" are for debugging
        # and will be removed after basic testing
        self._logger.info("IMAGE SCANNER: images_dir: %s" % images_dir_path)
        if not os.path.isdir(images_dir_path):
            self._logger.info("images_dir_path: images_dir: %s, doesn't exist"
                              % images_dir_path)
            raise DatastoreNotFoundException(
                "Image scanner, cannot find image "
                "directory for datastore: %s"
                % image_scanner.datastore_id)

        return self._mark_unused_images(image_scanner, images_dir_path)

    def delete_unused(self, image_sweeper):
        images_dir_path = os_datastore_path(image_sweeper.datastore_id,
                                            IMAGE_FOLDER_NAME_PREFIX)
        # Log messages with prefix: "IMAGE SWEEPER" are for debugging
        # and will be removed after basic testing
        self._logger.info("IMAGE SWEEPER: images_dir: %s" % images_dir_path)
        if not os.path.isdir(images_dir_path):
            self._logger.info("images_dir_path: images_dir: %s, doesn't exist"
                              % images_dir_path)
            raise DatastoreNotFoundException(
                "Image sweeper, cannot find image "
                "directory for datastore: %s"
                % image_sweeper.datastore_id)

        return self._delete_unused_images(image_sweeper, images_dir_path)

    def _unzip(self, src, dst):
        self._logger.info("unzip %s -> %s" % (src, dst))

        fsrc = gzip.open(src, "rb")
        fdst = open(dst, "wb")

        try:
            shutil.copyfileobj(fsrc, fdst)
        finally:
            fsrc.close()
            fdst.close()

    def _copy_disk(self, src, dst):
        self._manage_disk(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                          sourceName=src, destName=dst)

    def _manage_disk(self, op, **kwargs):
        if self._in_uwsim:
            self._manage_disk_uwsim(op, **kwargs)
            return

        try:
            self._logger.debug("Invoking %s(%s)" % (op.info.name, kwargs))
            task = op(self._manager, **kwargs)
            self._vim_client.wait_for_task(task)
        except vim.Fault.FileAlreadyExists, e:
            raise DiskAlreadyExistException(e.msg)
        except vim.Fault.FileFault, e:
            raise DiskFileException(e.msg)
        except vim.Fault.InvalidDatastore, e:
            raise DiskPathException(e.msg)

    def _temp(self, file):
        """ Generate a temp file name based on real file name
            [] /vmfs/volumes/datastore1/image_ttylinux/ttylinux.vmdk
            [] /vmfs/volumes/datastore1/image_ttylinux/ttylinux-tmp.vmdk
        :param file: real file name
        :return: temp file name
        """
        if file.endswith(".vmdk"):
            return file[:-5] + "-tmp.vmdk"
        else:
            return file + "-tmp"

    @property
    def _manager(self):
        """Get the virtual disk manager for the host
        rtype:vim.VirtualDiskManager
        """
        return self._vim_client.virtual_disk_manager

    def _make_image_dir(self, datastore, image_id,
                        parent_folder_name=IMAGE_FOLDER_NAME_PREFIX):
        path = os.path.dirname(
            os_vmdk_path(
                datastore,
                image_id,
                parent_folder_name))

        if os.path.isdir(path):
            return

        # On shared volumes makedirs can fail with not found in rare corner
        # cases if two directory creates collide. Just retry in that case
        for attempt in range(1, self.NUM_MAKEDIRS_ATTEMPTS+1):
            try:
                mkdir_p(path)
            except OSError:
                self._logger.debug("Retrying (%u) while creating %s" %
                                   (attempt, path))
                if attempt == self.NUM_MAKEDIRS_ATTEMPTS:
                    raise
                else:
                    continue
            # Directory got created, stop the for loop
            break

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

            /vmfs/volumes/datastore1/image_ttylinux/ttylinux.vmdk
            or with SUPPORT_VSAN:
            /vmfs/volumes/datastore1/images_ttylinux/ttylinux.vmdk

        This method returns "ttylinux" with this input.
        """
        if SUPPORT_VSAN:
            return image_path.split(os.sep)[4].split(COMPOND_PATH_SEPARATOR)[1]
        else:
            return image_path.split(os.sep)[6]

    def _gc_image_dir(self, datastore_id, image_id):
        """
        Moves the current image directory into the GC image folder and
        relies on the later GC call for cleanup.
        Exception is thrown if the move fails.
        Assumes the ref files contained in the image director are locked, so
        there can only be one move happening at any point in time.
        """
        src_path = os.path.dirname(os_vmdk_path(datastore_id, image_id,
                                                IMAGE_FOLDER_NAME_PREFIX))
        self._logger.info("Image path to be cleaned: %s" % src_path)
        # Verify locking held.
        assert(os.path.exists(src_path))
        # Generate a random suffix, this is to address the following.
        # a - Image disk say i1 was moved to the gc_dir
        # b - Cleanup of i1 failed for whatever reason.
        # c - New copy of i1 was uploaded to the datastore.
        # d - The new copy is now being deleted.
        # The location of the move in d needs to be different from the
        # location of the move from b, hence use a random uuid.
        rnd_uuid = str(uuid.uuid4())
        gc_dir = os_datastore_path(datastore_id, GC_IMAGE_FOLDER)
        dst_dir = os.path.join(gc_dir, rnd_uuid)
        os.makedirs(dst_dir)
        if os.path.islink(src_path):
            # if dir is a symlink, move the link target and remove link
            link = os.readlink(src_path)
            shutil.move(link, dst_dir)
            os.remove(src_path)
        else:
            shutil.move(src_path, dst_dir)

    def _clean_gc_dir(self, datastore_id):
        """
        Clean may fail but can be retried later
        """
        dir_path = os_datastore_path(datastore_id, GC_IMAGE_FOLDER)
        for sub_dir in os.listdir(dir_path):
            rm_rf(os.path.join(dir_path, sub_dir), ignore_errors=True)

    def create_image(self, datastore_id, tmp_dir, image_id):
        """ Installs an image using image data staged at a temp directory.
        """
        src_path = os_datastore_path(datastore_id, tmp_dir)
        if not os.path.exists(src_path):
            self._logger.info("Tmp dir %s on datastore %s not found" %
                              (tmp_dir, datastore_id))
            raise ImageNotFoundException("Image %s not found" % src_path)

        # Check if the dest image id already exists
        if self.check_image_dir(image_id, datastore_id):
            self._logger.info("Image %s on datastore %s already exists" %
                              (image_id, datastore_id))
            raise DiskAlreadyExistException()

        self._move_image(image_id, datastore_id, src_path)
        self._create_image_timestamp_file_from_ids(datastore_id, image_id)

    def create_image_with_vm_disk(self, datastore_id, tmp_dir, image_id,
                                  vm_disk_os_path):
        """ Fills a temp image directory with a disk from a VM,
            then installs directory in the shared image folder.
        """
        # Create parent directory as required by CopyVirtualDisk_Task
        dst_parent_dir = os_datastore_path(datastore_id, tmp_dir)
        if os.path.exists(dst_parent_dir):
            self._logger.debug("Parent directory %s exists" % dst_parent_dir)
        else:
            mkdir_p(dst_parent_dir)

        dst_vmdk_path = os.path.join(dst_parent_dir, "%s.vmdk" % image_id)
        if os.path.exists(dst_vmdk_path):
            self._logger.warning(
                "Unexpected disk %s present, overwriting" % dst_vmdk_path)
        dst_vmdk_ds_path = os_to_datastore_path(dst_vmdk_path)

        _vd_spec = self._prepare_virtual_disk_spec(
            vim.VirtualDiskManager.VirtualDiskType.thin,
            vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic)

        self._manage_disk(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                          sourceName=os_to_datastore_path(vm_disk_os_path),
                          destName=dst_vmdk_ds_path,
                          destSpec=_vd_spec)

        try:
            self.create_image(datastore_id, tmp_dir, image_id)
        except:
            self._logger.warning("Delete copied disk %s" % dst_vmdk_ds_path)
            self._manage_disk(vim.VirtualDiskManager.DeleteVirtualDisk_Task,
                              name=dst_vmdk_ds_path)
            raise

    def receive_image(self, image_id, datastore_id, imported_vm_name, metadata,
                      manifest):
        """ Creates an image using the data from the imported vm.

        This is run at the destination host end of the host-to-host
        image transfer.
        """

        self._vim_client.wait_for_vm_create(imported_vm_name)
        vm = self._vim_client.get_vm_obj_in_cache(imported_vm_name)
        self._logger.warning("receive_image found vm %s, %s" %
                             (imported_vm_name, vm))
        vmx_os_path = datastore_to_os_path(vm.config.files.vmPathName)
        vm_dir = os.path.dirname(vmx_os_path)

        vm.Unregister()

        try:
            if self.check_image_dir(image_id, datastore_id):
                self._logger.info("Image %s on datastore %s already exists" %
                                  (image_id, datastore_id))
                raise DiskAlreadyExistException()
            self._move_image(image_id, datastore_id, vm_dir)

            # Save raw manifest
            manifest_path = os_image_manifest_path(datastore_id, image_id)
            with open(manifest_path, 'w') as f:
                f.write(manifest)

            # Save raw metadata
            if metadata:
                metadata_path = os_metadata_path(datastore_id, image_id,
                                                 IMAGE_FOLDER_NAME_PREFIX)
                with open(metadata_path, 'w') as f:
                    f.write(metadata)

            self._create_image_timestamp_file_from_ids(datastore_id, image_id)
        finally:
            parent_dir = os.path.dirname(vm_dir)
            if os.path.basename(parent_dir).startswith(TMP_IMAGE_FOLDER_NAME_PREFIX):
                rm_rf(parent_dir)

    def delete_tmp_dir(self, datastore_id, tmp_dir):
        """ Deletes a temp image directory by moving it to a GC directory """
        file_path = os_datastore_path(datastore_id, tmp_dir)
        if not os.path.exists(file_path):
            self._logger.info("Tmp dir %s not" % file_path)
            raise DirectoryNotFound("Directory %s not found" % file_path)
        rm_rf(file_path)

    def _manage_disk_uwsim(self, op, **kwargs):
        def _vmdk_pairs(ds_path):
            vmdk_path = datastore_to_os_path(ds_path)
            pos = vmdk_path.rfind(".vmdk")
            vmdk_flat_path = vmdk_path[:pos] + "-flat" + vmdk_path[pos:]
            return (vmdk_path, vmdk_flat_path)

        if (op is vim.VirtualDiskManager.DeleteVirtualDisk_Task):
            (vmdk, flatvmdk) = _vmdk_pairs(kwargs["name"])
            os.unlink(vmdk)
            os.unlink(flatvmdk)
        elif (op is vim.VirtualDiskManager.CopyVirtualDisk_Task):
            (src_vmdk, src_flatvmdk) = _vmdk_pairs(kwargs["sourceName"])
            (dst_vmdk, dst_flatvmdk) = _vmdk_pairs(kwargs["destName"])
            shutil.copyfile(src_vmdk, dst_vmdk)
            shutil.copyfile(src_flatvmdk, dst_flatvmdk)
        elif (op is vim.VirtualDiskManager.MoveVirtualDisk_Task):
            (src_vmdk, src_flatvmdk) = _vmdk_pairs(kwargs["sourceName"])
            (dst_vmdk, dst_flatvmdk) = _vmdk_pairs(kwargs["destName"])
            shutil.move(src_vmdk, dst_vmdk)
            shutil.move(src_flatvmdk, dst_flatvmdk)

    """
    This method scans the image tree for the current
    datastore starting from the directory "root"
    (e.g.: /vmfs/volumes/<ds-id>/images). It looks for
    unused images and creates a marker file in the
    directory containing the image.
    """
    def _mark_unused_images(self, image_scanner, root):
        self._logger.info("IMAGE SCANNER: Mark unused started on %s" % root)
        active_images = image_scanner.get_active_images()
        unused_images = dict()
        # Compute scan rest interval
        rest_interval_sec = image_scanner.get_image_mark_rest_interval()
        apply_rate_limiter = False
        for curdir, dirs, files in os.walk(root):

            # If this contains only other directories skip it
            if len(files) == 0 and len(dirs) >= 0:
                continue

            if apply_rate_limiter:
                waste_time(rest_interval_sec)
            apply_rate_limiter = True

            # On a directory change check if it still needs to run
            if image_scanner.is_stopped():
                return unused_images

            image_id = self._get_and_validate_image_id(curdir, files)

            if not image_id:
                continue

            if image_id in active_images:
                self._logger.info(
                    "IMAGE SCANNER: skipping active image %s" % image_id)
                continue

            # If there is already a marker file skip it
            # but record this image in the unused dictionary
            marker_pathname = os.path.join(curdir,
                                           self.IMAGE_MARKER_FILE_NAME)
            if os.path.isfile(marker_pathname):
                self._logger.info("IMAGE_SCANNER: Adding dir: %s"
                                  % curdir)
                unused_images[image_id] = curdir
                continue

            # Write the content of _start_time to the
            # marker file, any change occurred to
            # the image after _start_time,
            # invalidates the image as a candidate
            # for removal
            try:
                self._write_marker_file(marker_pathname,
                                        image_scanner.start_time_str)
            except Exception as ex:
                self._logger.warning("Failed to write maker file: %s, %s"
                                     % (marker_pathname, ex))
                continue

            self._logger.info("IMAGE_SCANNER: Adding dir: %s"
                              % curdir)
            unused_images[image_id] = curdir

        return unused_images

    @staticmethod
    def _validate_image_id(image_id):
        image_uuid = uuid.UUID(image_id)
        return str(image_uuid) == image_id

    @staticmethod
    def _write_marker_file(filename, content):
        with open(filename, "wx") as marker_file:
            marker_file.write(content)

    @staticmethod
    def _read_marker_file(filename):
        with open(filename, "r") as marker_file:
            start_time_str = marker_file.read()
        return float(start_time_str)

    def _get_and_validate_image_id(self, imagedir, files):
        try:
            _, image_id = os.path.split(imagedir)
            # Validate directory name, if
            # not valid skip it
            if not self._validate_image_id(image_id):
                self._logger.info("Invalid image id for directory: "
                                  "%s", imagedir)
                return None
            vmdk_filename = vmdk_add_suffix(image_id)
            # If a file of the format: <image-id>.vmdk
            # does not exists, we assume this is
            # the result of a partial image copy,
            # it should be deleted, log a message
            # and continue
            if vmdk_filename not in files:
                self._logger.info("No vmdk file found in "
                                  "image directory: %s", imagedir)
            return image_id
        except Exception as ex:
            self._logger.info("Failed to get image vmdk: %s, %s" %
                              (imagedir, ex))
            return None

    """
    This method scans the image tree for the current
    datastore starting from the directory "root"
    (e.g.: /vmfs/volumes/<ds-id>/images). It looks for
    unused images in a directory containing the marker
    file, moves the directory to a GC location and
    deletes it.
    """
    def _delete_unused_images(self, image_sweeper, root):
        self._logger.info("IMAGE SCANNER: Sweeper started on %s" % root)

        deleted_images = list()
        target_images = image_sweeper.get_target_images()

        # Compute sweep rest interval
        rest_interval_sec = image_sweeper.get_image_sweep_rest_interval()

        apply_rate_limiter = False
        for curdir, dirs, files in os.walk(root):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            if apply_rate_limiter:
                waste_time(rest_interval_sec)
            apply_rate_limiter = True

            # On a directory change check if it still needs to run
            if image_sweeper.is_stopped():
                return

            image_id = self._get_and_validate_image_id(curdir, files)

            if not image_id:
                continue

            # If there is not a marker file skip it
            marker_pathname = os.path.join(curdir,
                                           self.IMAGE_MARKER_FILE_NAME)
            if not os.path.isfile(marker_pathname):
                continue

            # If this is not a part of target images skip it
            if image_id not in target_images:
                continue

            # Write the content of _start_time as an ISO date
            # inside the the marker file, any change occurred to
            # the image after _start_time invalidates the image
            # as a candidate for removal
            try:
                if self._delete_single_image(image_sweeper,
                                             curdir, image_id):
                    deleted_images.append(image_id)
            except Exception as ex:
                self._logger.warning("Failed to remove image: %s, %s"
                                     % (curdir, ex))
                continue

        # Now attempt GCing the image directory.
        datastore_id = image_sweeper.datastore_id
        try:
            self._clean_gc_dir(datastore_id)
        except Exception:
            # Swallow the exception the next clean call will clear it all.
            self._logger.exception("Failed to delete gc dir on datastore %s" %
                                   datastore_id)

        return deleted_images

    """
    Delete a single image following the delete image steps. This
    method is supposed to be safe when run concurrently with:
    a) itself,
    b) image creation/copy,
    c) vm creation

    The steps are outlined here:
    1) Read content of the unused_image_marker file.
       If error move on to next image,
    2) Acquire image-lock,
    3) Read the mod time on the t-stamp file,
       if t-stamp file doesn't exist go to 6
    4) If the mod time of the t-stamp file is
       newer than the content of the marker
       file move on to next image
    5) Move the t-stamp file to another name,
    6) Check the mod time on the new name of
       the t-stamp file. if the mod time has
       changed, move on to next image
    7) move image directory to a trash location

    This method returns True if the image was removed,
    False if the image could not be removed.
    """

    def _delete_single_image(self, image_sweeper, curdir, image_id, modify=True):
        self._logger.info("IMAGE SCANNER: Starting to "
                          "delete image: %s, %s"
                          % (curdir, image_id))
        # Read content of marker file
        try:
            marker_pathname = os.path.join(curdir,
                                           self.IMAGE_MARKER_FILE_NAME)
            marker_time = self._read_marker_file(marker_pathname)
        except Exception as ex:
            self._logger.warning("Cannot read marker file: %s, %s"
                                 % (curdir, ex))
            return False

        self._logger.info("IMAGE SCANNER: Marker time: %s"
                          % marker_time)

        # Subtract grace time to avoid
        # errors due to small difference in clock
        # values on different hosts. Pretend the scan
        # started 60 seconds earlier.
        marker_time -= image_sweeper.get_grace_period()

        self._logger.info(
            "IMAGE SCANNER: Marker time after grace: %s"
            % marker_time)

        timestamp_pathname = \
            os.path.join(curdir,
                         self.IMAGE_TIMESTAMP_FILE_NAME)
        renamed_timestamp_pathname = \
            timestamp_pathname + \
            self.IMAGE_TIMESTAMP_FILE_RENAME_SUFFIX

        # Lock image
        datastore_id = image_sweeper.datastore_id
        ds_type = self._get_datastore_type(datastore_id)

        with FileBackedLock(curdir, ds_type):
            # Get mod time of the timestamp file,
            # the method returns None if the file doesn't
            # exists, throws exception if there are
            # other errors
            timestamp_exists, mod_time = \
                self._get_mod_time(
                    timestamp_pathname)

            if timestamp_exists:
                # Marker time is out of date
                # skip this image
                self._logger.info(
                    "IMAGE SCANNER: mod time: %s"
                    % mod_time)
                if mod_time >= marker_time:
                    # Remove marker file
                    self._logger.info(
                        "IMAGE SCANNER: mod time too recent")
                    self._image_sweeper_unlink(marker_pathname)
                    return False

                # Move timestamp file to a new name
                if modify:
                    self._image_sweeper_rename(
                        timestamp_pathname,
                        renamed_timestamp_pathname)

            else:
                # If we could not find the timestamp file
                # it may mean that this was a partially
                # removed image, log message and continue
                self._logger.info("Cannot find timestamp file: %s"
                                  "continuing with image removal"
                                  % timestamp_pathname)

            if modify:
                # Get mod time of the renamed timestamp file
                renamed_timestamp_exists, renamed_mod_time = \
                    self._get_mod_time(
                        renamed_timestamp_pathname)
            else:
                renamed_timestamp_exists = True
                renamed_mod_time = mod_time

            self._logger.info(
                "IMAGE SCANNER: rename timestamp exists: %s, "
                "renamed mod time: %s" %
                (renamed_timestamp_exists, renamed_mod_time))

            # If there was timestamp file but there
            # is no renamed-timestamp file something
            # bad might have happened, skip this image
            if timestamp_exists and \
                    not renamed_timestamp_exists:
                self._logger.warning("Error, missing renamed "
                                     "timestamp file: %s"
                                     % renamed_timestamp_pathname)
                return False

            # Normal case both timestamp and renamed
            # timestamp exist
            if timestamp_exists and renamed_timestamp_exists:
                # Normal case: both timestamp and renamed
                # timestamp files exist. If the mod time on the
                # renamed-timestamp has changed skip this image.
                if renamed_mod_time != mod_time:
                    self._logger.info("mod time changed on renamed "
                                      "timestamp file, %s: %d -> %d" %
                                      (renamed_timestamp_pathname,
                                       mod_time, renamed_mod_time))
                    self._image_sweeper_unlink(marker_pathname)
                    return False
            elif renamed_timestamp_exists:
                # Only the renamed timestamp file exists
                # Check the mod time of the renamed-timestamp
                # file against the marker time
                if renamed_mod_time >= marker_time:
                    self._image_sweeper_unlink(marker_pathname)
                    return False

            # Move directory
            self._logger.info("IMAGE SCANNER: removing image: %s"
                              % curdir)
            if modify:
                trash_dir = os.path.join(
                    os_datastore_path(datastore_id, GC_IMAGE_FOLDER),
                    image_id)
                self._image_sweeper_rename(curdir, trash_dir)
            # Unlock

        # Delete image
        if modify:
            self._image_sweeper_rm_rf(trash_dir)
        return True

    def get_timestamp_mod_time_from_dir(self, dirname, renamed=False):
        filename = \
            os.path.join(dirname,
                         self.IMAGE_TIMESTAMP_FILE_NAME)

        if renamed:
            filename += self.IMAGE_TIMESTAMP_FILE_RENAME_SUFFIX

        return self.\
            _get_mod_time(filename)

    def get_tombstone_mod_time_from_dir(self, dirname):
        filename = \
            os.path.join(dirname,
                         self.IMAGE_TOMBSTONE_FILE_NAME)

        return self.\
            _get_mod_time(filename)

    # Read the mod time on a file,
    # returns two values, a boolean
    # which is set to true if the
    # file exists, otherwise set to false
    # and the mod time of the existing
    # file
    def _get_mod_time(self, pathname):
        try:
            mod_time = os.path.getmtime(pathname)
        except OSError as ex:
            self._logger.warning(
                "Cannot read mod time for file: %s, %s"
                % (pathname, ex))
            if ex.errno == errno.ENOENT:
                return False, 0
            else:
                raise ex
        return True, mod_time

    def _create_image_timestamp_file(self, dirname):
        try:
            timestamp_pathname = \
                os.path.join(dirname,
                             self.IMAGE_TIMESTAMP_FILE_NAME)
            open(timestamp_pathname, 'w').close()
        except Exception as ex:
            self._logger.exception(
                "Exception creating %s, %s" %
                (dirname, ex))
            raise ex

    def _create_image_timestamp_file_from_ids(self, ds_id, image_id):
        image_path = os.path.dirname(
            os_vmdk_path(ds_id, image_id, IMAGE_FOLDER_NAME_PREFIX))
        self._create_image_timestamp_file(image_path)

    def _delete_renamed_image_timestamp_file(self, dirname):
        try:
            timestamp_pathname = \
                os.path.join(dirname,
                             self.IMAGE_TIMESTAMP_FILE_NAME)
            timestamp_pathname += \
                self.IMAGE_TIMESTAMP_FILE_RENAME_SUFFIX
            os.unlink(timestamp_pathname)
        except Exception as ex:
            self._logger.exception(
                "Exception deleting %s, %s" %
                (dirname, ex))

    def _image_sweeper_rename(self, src, dest):
        try:
            shutil.move(src, dest)
        except Exception as ex:
            self._logger.warning(
                "Cannot rename file/dir: %s => %s, %s"
                % (src, dest, ex))
            raise ex

    def _image_sweeper_unlink(self, filename):
        try:
            os.unlink(filename)
        except Exception as ex:
            self._logger.warning(
                "Cannot unlink file: %s, %s"
                % (filename, ex))

    def _image_sweeper_rm_rf(self, directory):
        try:
            rm_rf(directory)
        except Exception as ex:
            self._logger.warning(
                "Cannot rm_rf dir: %s, %s"
                % (directory, ex))
