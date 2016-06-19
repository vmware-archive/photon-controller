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

from host.hypervisor.exceptions import DatastoreNotFoundException

from host.image.image_scanner import DatastoreImageScanner
from host.image.image_sweeper import DatastoreImageSweeper


"""
    This class collects contains the list of all
    the datastore image scanner and sweepers.
"""


class ImageMonitor:

    def __init__(self, datastore_manager, image_manager, vm_manager):
        self.logger = logging.getLogger(__name__)
        self.datastore_manager = datastore_manager
        self.datastore_image_scanners = dict()
        self.datastore_image_sweepers = dict()
        for datastore_id in self.datastore_manager.get_datastore_ids():
            self.logger.info("IMAGE SCANNER: adding datastore: %s"
                             % datastore_id)
            self.datastore_image_scanners[datastore_id] = \
                DatastoreImageScanner(image_manager,
                                      vm_manager,
                                      datastore_id)
            self.datastore_image_sweepers[datastore_id] = \
                DatastoreImageSweeper(image_manager,
                                      datastore_id)

    def get_image_scanner(self, datastore_id):
        self.logger.info("IMAGE SCANNER: dict: %s" %
                         self.datastore_image_scanners)
        if datastore_id in self.datastore_image_scanners:
            return self.datastore_image_scanners[datastore_id]
        raise DatastoreNotFoundException

    def get_image_sweeper(self, datastore_id):
        self.logger.info("IMAGE SCANNER: dict: %s" %
                         self.datastore_image_sweepers)
        if datastore_id in self.datastore_image_sweepers:
            return self.datastore_image_sweepers[datastore_id]
        raise DatastoreNotFoundException
