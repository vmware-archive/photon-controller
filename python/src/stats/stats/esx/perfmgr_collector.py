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

from datetime import datetime
from datetime import timedelta
import logging

import common
from common.service_name import ServiceName
from stats.collector import Collector


class PerfManagerCollector(Collector):
    def __init__(self):
        self._logger = logging.getLogger(__name__)
        if self._logger.getEffectiveLevel() < logging.INFO:
            self._logger.setLevel(logging.INFO)

        self._collection_level = 1

        # Samples are 20 seconds apart
        self._stats_interval_id = 20

    @staticmethod
    def get_client():
        return common.services.get(ServiceName.HOST_CLIENT)

    def get_perf_manager_stats(self, start_time, end_time=None):
        """ Returns the host statistics by querying the perf manager on the
            host for the configured performance counters.

        :param start_time [int]: seconds since epoch
        :param end_time [int]: seconds since epoch
        """
        results = self.get_client().query_stats(start_time, end_time)
        return results

    def collect(self, since=None):
        # We are omitting end_time and letting internal API
        # to get the numbers for last 20 seconds starting from now.
        since = datetime.now() - timedelta(seconds=self._stats_interval_id)
        results = self.get_perf_manager_stats(start_time=since, end_time=None)
        return results
