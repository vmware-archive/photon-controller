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

"""Temporary hack to fake deploy demo image to datastore"""

import logging
import os

from common.file_util import mkdir_p
from host.hypervisor.fake.disk_manager import FakeDiskManager
from host.hypervisor.image_manager import ImageManager


class FakeImageManager(ImageManager):
    IMAGE_PATH = '/tmp/images'

    def __init__(self, hypervisor, image_datastore, image_path=None):
        super(FakeImageManager, self).__init__()
        self._hypervisor = hypervisor
        self._logger = logging.getLogger(__name__)
        self.image_path = image_path or self.IMAGE_PATH
        self._disk_manager = FakeDiskManager(hypervisor, self.image_path)
        self._image_datastore = image_datastore
        self._setup_datastores()

    def copy_to_datastores(self, image_id, datastores):
        """Demoware method to deploy the ttylinux image."""
        for datastore in datastores:
            self.deploy_image(image_id, image_id, datastore)

    def deploy_image(self, src_id, dst_id, datastore_id):
        self._logger.info("creating fake image for %s in datastore %s" %
                          (src_id, datastore_id))
        self._disk_manager.create_disk(datastore_id, dst_id, 0, force=True)

    def check_image(self, image_id, datastore_id):
        return self._disk_manager._check_disk(image_id, datastore_id)

    def check_and_validate_image(self, image_id, datastore_id):
        return self.check_image(image_id, datastore_id)

    def copy_image(self, src_datastore, src_id, dst_datastore, dst_id):
        self._logger.info("copying fake image from %s in datastore %s to %s in"
                          " datastore %s" % (src_id, src_datastore,
                                             dst_id, dst_datastore))
        self._disk_manager.copy_disk(src_datastore, src_id,
                                     dst_datastore, dst_id)

    def get_image_metadata(self, image_id, datastore):
        return None

    def image_file(self, datastore_id, image_id):
        return self.get_image_path(datastore_id, image_id)

    def get_image_directory_path(self, datastore_id, image_id):
        # for the fake host, the image id is not a part of the image directory
        # path.
        return os.path.join(self.image_path, datastore_id)

    def get_image_path(self, datastore_id, image_id):
        image_dir = self.get_image_directory_path(datastore_id, image_id)
        return os.path.join(image_dir, "%s.vmdk" % image_id)

    def get_images(self, datastore):
        return list(self._disk_manager.get_resource_ids(datastore))

    def get_inactive_images(self, datastore_id):
        return list()

    def _setup_datastores(self):
        datastores = self._hypervisor.datastore_manager.get_datastore_ids()
        self._logger.info("setup datastore (%s)" %
                          ",".join(datastores))
        for i, datastore_id in enumerate(datastores):
            self._logger.info("create dir for %s" % datastore_id)
            datastore_id_path = os.path.join(self.image_path, datastore_id)
            mkdir_p(datastore_id_path)

            datastore_name = \
                self._hypervisor.datastore_manager.datastore_name(datastore_id)
            self._logger.info("create symlink for %s" % datastore_name)
            datastore_name_path = os.path.join(self.image_path, datastore_name)
            if not os.path.exists(datastore_name_path):
                os.symlink(datastore_id_path, datastore_name_path)

    def get_datastore_id_from_path(self, image_path):
        # The image path looks like: /tmp/tmprWGCzm/datastore1/ttylinux.vmdk
        directory = os.path.dirname(image_path)
        return os.path.basename(directory)

    def get_image_id_from_path(self, image_path):
        # The image path looks like: /tmp/tmprWGCzm/datastore1/ttylinux.vmdk
        basename = os.path.basename(image_path)
        return os.path.splitext(basename)[0]

    def image_size(self, image_id):
        return self._disk_manager.disk_size(self._image_datastore, image_id)

    def touch_image_timestamp(self, dsid, image_id):
        return

    def get_timestamp_mod_time_from_dir(self, dirname):
        return True, 0

    def create_image(self, image_id, datastore_id):
        return

    def finalize_image(self, datastore_id, tmp_dir, image_id):
        return

    def delete_tmp_dir(self, datastore_id, tmp_dir):
        return

    def create_image_with_vm_disk(self, datastore_id, tmp_dir, image_id, vm_disk_os_path):
        return

    def delete_image(self, datastore_id, image_id):
        return True
