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

"""ESX host system."""

import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor
import common

from common.log import log_duration
from host.hypervisor.system import DatastoreInfo
from host.hypervisor.system import System

DATASTORE_INFO_CACHE_TTL = 60
FETCH_TIMEOUT = 5


class EsxSystem(System):

    def __init__(self, vim_client):
        self._logger = logging.getLogger(__name__)
        self._lock = threading.RLock()
        self._threadpool = common.services.get(ThreadPoolExecutor)
        self._vim_client = vim_client
        self._datastore_info_cache = {}
        self._pending_datastore_updates = {}
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
        """Returns the datastore info.

        The backend call is expensive and sometimes takes in excess of 30
        seconds to execute. This method uses a cache that is updated in the
        background to reduce the latency as much as possible.
        """
        with self._lock:
            if datastore_id in self._datastore_info_cache:
                entry = self._datastore_info_cache[datastore_id]
                if entry.is_stale(DATASTORE_INFO_CACHE_TTL):
                    # update the datastore info in the background and return
                    # the stale entry
                    self._update_datastore_info(datastore_id)
                self._logger.debug(
                    "Using cached datastore info: %s" % datastore_id)
                return entry.value
            else:
                future_result = self._update_datastore_info(datastore_id)

        return future_result.result(FETCH_TIMEOUT)

    def _update_datastore_info(self, datastore_id):
        with self._lock:
            if datastore_id in self._pending_datastore_updates:
                return self._pending_datastore_updates[datastore_id]
            else:
                future_result = self._threadpool.submit(
                    self._fetch_datastore_info, datastore_id)
                self._pending_datastore_updates[datastore_id] = future_result
                return future_result

    @log_duration
    def _fetch_datastore_info(self, datastore_id):
        # XXX: datastore_id is a misnomer, the parameter is expected
        # to be a datastore name
        self._logger.debug("Fetching fresh datastore info: %s" % datastore_id)
        ds = self._vim_client.get_datastore(datastore_id)
        total = float(ds.capacity) / (1024 ** 3)
        free = float(ds.free) / (1024 ** 3)
        result = DatastoreInfo(total, total - free)
        with self._lock:
            self._datastore_info_cache[datastore_id] = CacheEntry(result)
            del self._pending_datastore_updates[datastore_id]
        return result

    def host_consumed_memory_mb(self):
        return self._vim_client.memory_usage_mb

    def host_version(self):
        if self._host_version is None:
            self._host_version = self._vim_client.host_version

        return self._host_version


class CacheEntry(object):

    def __init__(self, value):
        self.value = value
        self.fetched = time.time()

    def is_stale(self, ttl):
        return time.time() - self.fetched > ttl
