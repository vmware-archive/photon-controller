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

from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import VMFS_VOLUMES


class SoftLinkGenerator():

    IMAGE_FILE_EXT = "vmdk"
    VM_FILE_EXT = "vmx"
    OLD_IMAGE_ROOT_FOLDER_NAME = "images"
    OLD_VM_ROOT_FOLDER_NAME = "vms"

    def __init__(self, datastore_id):
        self.datastore_id = datastore_id
        self._logger = logging.getLogger(__name__)

    def _create_symlinks_to_new_image_path(self, datastore):

        self._logger.info("Processing datastore %s to create symlinks for image" % (datastore))

        self._traverse_dir_to_create_symlinks(datastore, self.OLD_IMAGE_ROOT_FOLDER_NAME, IMAGE_FOLDER_NAME_PREFIX,
                                              self.IMAGE_FILE_EXT)

    def _create_symlinks_to_new_vm_path(self, datastore):

        self._logger.info("Processing datastore %s to create symlinks for vm" % (datastore))

        self._traverse_dir_to_create_symlinks(datastore, self.OLD_VM_ROOT_FOLDER_NAME, VM_FOLDER_NAME_PREFIX,
                                              self.VM_FILE_EXT)

    def _traverse_dir_to_create_symlinks(self, datastore, old_folder_name, folder_prefix, file_ext):

        for curdir, dirs, files in os.walk(os.path.join(VMFS_VOLUMES, datastore, old_folder_name)):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            entity_id = self._get_and_validate_id(curdir, files, file_ext)

            if not entity_id:
                continue

            # Creating symlink
            try:
                new_dir = self._get_new_folder_name(folder_prefix, entity_id)
                new_dir_path = os.path.join(VMFS_VOLUMES, datastore, new_dir)
                if os.path.islink(new_dir_path):
                    self._logger.info("Symlink %s exists", new_dir_path)
                    continue
                if os.path.exists(new_dir_path):
                    self._logger.warn("Path %s exists and it's not a symlink", new_dir_path)
                    continue

                os.symlink(curdir, new_dir_path)
                self._logger.info("Symlink %s to %s created.", new_dir_path, curdir)

            except Exception as ex:
                self._logger.exception("Failed to create symlink %s with: %s" % (new_dir_path, ex))

    def _get_and_validate_id(self, dir_name, files, file_ext):
        try:
            _, id = os.path.split(dir_name)

            # Validate directory name, if not valid skip it
            if not self._validate_uuid(id):
                self._logger.info("Invalid id for directory: %s", dir_name)
                return None
            filename = self._file_add_suffix(id, file_ext)

            # If a file of the relevant format does not exists, log a message and continue
            if filename not in files:
                self._logger.info("No relevant file found in directory: %s", dir_name)
                return None
            return id

        except Exception as ex:
            self._logger.exception("Failed to get relevant file : %s, %s" % (dir_name, ex))
            return None

    def _file_add_suffix(self, filename, file_suffix):
        return "%s.%s" % (filename, file_suffix)

    def _get_new_folder_name(self, folder_prefix, folder_suffix):
        return "%s_%s" % (folder_prefix, folder_suffix)

    @staticmethod
    def _validate_uuid(id):
        entity_uuid = uuid.UUID(id)
        return str(entity_uuid) == id
