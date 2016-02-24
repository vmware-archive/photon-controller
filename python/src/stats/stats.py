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

from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from .gen import StatsService
from .gen.ttypes import SetCollectionLevelResponse
from .gen.ttypes import SetCollectionLevelResultCode
from .memory_tsdb import MemoryTimeSeriesDB
from stats_collector import StatsCollector
from stats_publisher import StatsPublisher


class StatsHandler(StatsService.Iface):

    def __init__(self):
        self._db = MemoryTimeSeriesDB()
        self._logger = logging.getLogger(__name__)
        self._collector = StatsCollector(self._db)
        self._collector.configure_collectors()
        self._publisher = StatsPublisher(self._db)
        self._publisher.configure_publishers()

    def _error_response(self, code, error, response):
        self._logger.debug(error)
        response.result = code
        response.error = str(error)
        return response

    def start(self):
        self._collector.start_collection()
        self._publisher.start_publishing()

    @log_request
    @error_handler(SetCollectionLevelResponse, SetCollectionLevelResultCode)
    def set_collection_level(self, request):
        """Sets the level to collect stats at.

        :type request: SetCollectionLevelRequest
        :rtype: SetCollectionLevelResponse
        """

        # Todo(vui): add real implementation

        response = SetCollectionLevelResponse()
        if request.level < 0 or request.level > 4:
            return self._error_response(
                SetCollectionLevelResultCode.INVALID_LEVEL,
                "Invalid stats level %d" % request.level,
                response)

        response.result = SetCollectionLevelResultCode.OK
        return response
