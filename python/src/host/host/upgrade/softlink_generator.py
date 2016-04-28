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


import logging

import os.path
import uuid

from host.hypervisor.esx.path_util import DISK_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import VMFS_VOLUMES


class SoftLinkGenerator:

    IMAGE_FILE_EXT = "vmdk"
    VM_FILE_EXT = "vmx"
    OLD_IMAGE_ROOT_FOLDER_NAME = "images"
    OLD_VM_ROOT_FOLDER_NAME = "vms"
    OLD_DISKS_ROOT_FOLDER_NAME = "disks"

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def process(self, datastores):
        for ds in datastores:
            self._process_images(ds)
            self._process_vms(ds)
            self._process_disks(ds)

    def _process_images(self, datastore):
        self._create_symlinks(datastore, self.OLD_IMAGE_ROOT_FOLDER_NAME, IMAGE_FOLDER_NAME_PREFIX,
                              self.IMAGE_FILE_EXT)

    def _process_vms(self, datastore):
        self._create_symlinks(datastore, self.OLD_VM_ROOT_FOLDER_NAME, VM_FOLDER_NAME_PREFIX, self.VM_FILE_EXT)

    def _process_disks(self, datastore):
        self._create_symlinks(datastore, self.OLD_DISKS_ROOT_FOLDER_NAME, DISK_FOLDER_NAME_PREFIX, self.IMAGE_FILE_EXT)

    def _create_symlinks(self, datastore, old_root_folder, new_folder_prefix, required_file_ext):
        """ Helper to enumerate old directory structure and create symlinks that matches new structure
            /vmfs/volumns/[datastore]/[new_folder_prefix]_[id]/
            ==> /vmfs/volumns/[datastore]/[old_root_folder]/[id:2]/[id]/

            To validate the folder is not empty or corrupted, we check whether file with name
            [id].[required_file_ext] exists.
        """
        self._logger.info("Processing %s on datastore %s" % (old_root_folder, datastore))
        root_path = os.path.join(VMFS_VOLUMES, datastore, old_root_folder)
        for curdir, dirs, files in os.walk(root_path):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            entity_id = self._validate_dir_and_parse_id(curdir, files, required_file_ext)
            if not entity_id:
                continue

            link_name = "%s_%s" % (new_folder_prefix, entity_id)
            link_path = os.path.join(VMFS_VOLUMES, datastore, link_name)
            self._create_symlink(curdir, link_path)

    def _validate_dir_and_parse_id(self, dir_name, files, required_file_ext):
        try:
            _, id = os.path.split(dir_name)

            # Validate directory name, if not valid skip it
            if not self._validate_uuid(id):
                self._logger.info("Invalid id for directory: %s", dir_name)
                return None
            required_filename = "%s.%s" % (id, required_file_ext)

            # If a file of the relevant format does not exists, log a message and continue
            if required_filename not in files:
                self._logger.info("Required file %s cannot be found in directory: %s", (required_filename, dir_name))
                return None
            return id

        except Exception as ex:
            self._logger.exception("Failed to get relevant file : %s, %s" % (dir_name, ex))
            return None

    def _create_symlink(self, target_path, link_path):
        try:
            if os.path.islink(link_path):
                self._logger.info("Symlink %s exists", link_path)
                return
            if os.path.exists(link_path):
                self._logger.warn("Path %s exists and it's not a symlink", link_path)
                return

            os.symlink(target_path, link_path)
            self._logger.info("Symlink %s to %s created.", link_path, target_path)

        except Exception:
            self._logger.exception("Failed to create symlink %s" % link_path)

    @staticmethod
    def _validate_uuid(id):
        entity_uuid = uuid.UUID(id)
        return str(entity_uuid) == id
