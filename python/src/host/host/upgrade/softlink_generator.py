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

from host.hypervisor.esx.vm_config import IMAGE_FILE_EXT
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import VM_FILE_EXT
from host.hypervisor.esx.vm_config import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import VMFS_VOLUMES


class SoftLinkGenerator():

    def __init__(self, datastore_id):
        self.datastore_id = datastore_id
        self._logger = logging.getLogger(__name__)

    def _create_symlinks_to_new_image_path(self, root, datastore):

        images_root = os.path.join(root, datastore, "images")

        for curdir, dirs, files in os.walk(images_root):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            image_id = self._get_and_validate_image_id(curdir, files)

            if not image_id:
                continue

            # Creating symlink
            try:
                new_image_dir = self._get_new_folder_name(IMAGE_FOLDER_NAME_PREFIX, image_id)
                new_image_dir_path = os.path.join(VMFS_VOLUMES, datastore, new_image_dir)
                if os.path.islink(new_image_dir_path):
                    self._logger.info("Symlink %s exists", new_image_dir_path)
                    continue
                if os.path.exists(new_image_dir_path):
                    self._logger.warn("Path %s exists and it's not a symlink", new_image_dir_path)
                    continue

                os.symlink(curdir, new_image_dir_path)
                self._logger.info("Symlink %s to %s created.", new_image_dir_path, curdir)

            except Exception as ex:
                self._logger.exception("Failed to create symlink %s with: %s" % (new_image_dir_path, ex))

    def _create_symlinks_to_new_vm_path(self, root, datastore):

        vms_root = os.path.join(root, datastore, "vms")

        for curdir, dirs, files in os.walk(vms_root):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            vm_id = self._get_and_validate_vm_id(curdir, files)

            if not vm_id:
                continue

            # Creating symlink
            try:
                new_vm_dir = self._get_new_folder_name(VM_FOLDER_NAME_PREFIX, vm_id)
                new_vm_dir_path = os.path.join(VMFS_VOLUMES, datastore, new_vm_dir)
                if os.path.islink(new_vm_dir_path):
                    self._logger.info("Symlink %s exists", new_vm_dir_path)
                    continue
                if os.path.exists(new_vm_dir_path):
                    self._logger.warn("Path %s exists and it's not a symlink", new_vm_dir_path)
                    continue

                os.symlink(curdir, new_vm_dir_path)
                self._logger.info("Symlink %s to %s created.", new_vm_dir_path, curdir)

            except Exception as ex:
                self._logger.exception("Failed to create symlink %s with: %s" % (new_vm_dir_path, ex))

    def _get_and_validate_image_id(self, imagedir, files):
        try:
            _, image_id = os.path.split(imagedir)

            # Validate directory name, if not valid skip it
            if not self._validate_uuid(image_id):
                self._logger.info("Invalid image id for directory: %s", imagedir)
                return None
            vmdk_filename = self._file_add_suffix(image_id, IMAGE_FILE_EXT)

            # If a file of the format: <image-id>.vmdk does not exists, log a message and continue
            if vmdk_filename not in files:
                self._logger.info("No vmdk file found in image directory: %s", imagedir)
                return None
            return image_id

        except Exception as ex:
            self._logger.exception("Failed to get image vmdk: %s, %s" % (imagedir, ex))
            return None

    def _get_and_validate_vm_id(self, vmdir, files):
        try:
            _, vm_id = os.path.split(vmdir)

            # Validate directory name, if not valid skip it
            if not self._validate_uuid(vm_id):
                self._logger.info("Invalid vm id for directory: %s", vmdir)
                return None
            vmx_filename = self._file_add_suffix(vm_id, VM_FILE_EXT)

            # If a file of the format: <vm-id>.vmx does not exists, log a message and continue
            if vmx_filename not in files:
                self._logger.info("No vmx file found in vm directory: %s", vmdir)
                return None
            return vm_id

        except Exception as ex:
            self._logger.exception("Failed to get vm vmx: %s, %s" % (vmdir, ex))
            return None

    def _file_add_suffix(self, filename, file_suffix):
        return "%s.%s" % (filename, file_suffix)

    def _get_new_folder_name(self, folder_prefix, folder_suffix):
        return "%s_%s" % (folder_prefix, folder_suffix)

    @staticmethod
    def _validate_uuid(id):
        entity_uuid = uuid.UUID(id)
        return str(entity_uuid) == id
