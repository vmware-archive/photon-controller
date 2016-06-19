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
from common.log import log_duration


class DatastoreInfo(object):
    def __init__(self, total, used):
        self.total = total
        self.used = used


class System(object):

    def __init__(self, vim_client):
        self._logger = logging.getLogger(__name__)
        self._vim_client = vim_client
        self._num_physical_cpus = None
        self._total_vmusable_memory_mb = None
        self._host_version = None

    def total_vmusable_memory_mb(self):
        if self._total_vmusable_memory_mb is None:
            self._total_vmusable_memory_mb = self._vim_client.total_vmusable_memory_mb
        return self._total_vmusable_memory_mb

    def num_physical_cpus(self):
        if self._num_physical_cpus is None:
            self._num_physical_cpus = self._vim_client.num_physical_cpus
        return self._num_physical_cpus

    @log_duration
    def datastore_info(self, datastore_id):
        ds = self._vim_client.get_datastore_in_cache(datastore_id)
        total = float(ds.capacity) / (1024 ** 3)
        free = float(ds.free) / (1024 ** 3)
        return DatastoreInfo(total, total - free)

    def host_consumed_memory_mb(self):
        return self._vim_client.memory_usage_mb

    def host_version(self):
        if self._host_version is None:
            self._host_version = self._vim_client.host_version

        return self._host_version
