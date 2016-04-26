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

"""Fake host system."""

import logging
from host.hypervisor.system import DatastoreInfo
from host.hypervisor.system import System


class FakeSystem(System):

    DEFAULT_TOTAL_MEMORY_MB = 1024 * 1024
    BASE_DS_SIZE_GB = 1024 * 1024
    TOTAL_CPUS = 4 * 4 * 2

    def __init__(self, hypervisor):
        self._logger = logging.getLogger(__name__)
        self._vm_manager = hypervisor.vm_manager
        self._disk_manager = hypervisor.disk_manager
        self.datastores = {}
        self.vm_usable_memory = self.DEFAULT_TOTAL_MEMORY_MB
        self.total_cpus = self.TOTAL_CPUS

    def add_datastore_gb(self, datastore_id, size_in_gb=BASE_DS_SIZE_GB):
        self.datastores.update({datastore_id: size_in_gb})

    def total_vmusable_memory_mb(self):
        return self.vm_usable_memory

    def num_physical_cpus(self):
        return self.total_cpus

    def datastore_info(self, datastore_id):

        if datastore_id not in self.datastores.keys():
            self._logger.warning("Datastore (%s) not connected" %
                                 datastore_id)
            raise Exception

        capacity = self.datastores[datastore_id]
        used_storage = self._disk_manager.used_storage(datastore_id)
        self._logger.debug("used_storage: %d" % used_storage)
        return DatastoreInfo(capacity, used_storage)

    def host_consumed_memory_mb(self):
        return None

    def host_version(self):
        return "X.X.X"
