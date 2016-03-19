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
from host.hypervisor.esx.vm_config import VMFS_VOLUMES
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX

IMAGES_FOLDER_NAME = "images"


class SoftLinkGenerator():

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def create_symlinks_to_new_image_path(self, datastore):
        self._logger.info("processing datastore %s", datastore)

        root = os.path.join(VMFS_VOLUMES, datastore, IMAGES_FOLDER_NAME)

        for curdir, dirs, files in os.walk(root):

            # If this contains only other directories skip it
            if len(files) == 0:
                continue

            image_id = self._get_and_validate_image_id(curdir, files)

            if not image_id:
                continue

            # Creating symlink
            try:
                new_image_dir = self._get_new_image_dir_name(image_id)
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

    def _get_and_validate_image_id(self, imagedir, files):
        try:
            _, image_id = os.path.split(imagedir)

            # Validate directory name, if not valid skip it
            if not self._validate_image_id(image_id):
                self._logger.info("Invalid image id for directory: %s", imagedir)
                return None
            vmdk_filename = self._vmdk_add_suffix(image_id)

            # If a file of the format: <image-id>.vmdk does not exists, log a message and continue
            if vmdk_filename not in files:
                self._logger.info("No vmdk file found in image directory: %s", imagedir)
                return None
            return image_id

        except Exception as ex:
            self._logger.exception("Failed to get image vmdk: %s, %s" % (imagedir, ex))
            return None

    def _vmdk_add_suffix(self, pathname):
        return "%s.%s" % (pathname, IMAGE_FILE_EXT)

    def _get_new_image_dir_name(self, image_id):
        return "%s_%s" % (IMAGE_FOLDER_NAME_PREFIX, image_id)

    @staticmethod
    def _validate_image_id(image_id):
        image_uuid = uuid.UUID(image_id)
        return str(image_uuid) == image_id
