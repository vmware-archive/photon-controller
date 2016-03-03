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

import common
from common.service_name import ServiceName
from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from gen.stats.plugin import StatsService
from gen.stats.plugin.ttypes import SetCollectionLevelResponse
from gen.stats.plugin.ttypes import SetCollectionLevelResultCode
from .memory_tsdb import MemoryTimeSeriesDB
from stats_collector import StatsCollector
from stats_publisher import StatsPublisher


class StatsHandler(StatsService.Iface):

    def __init__(self):
        self._db = None
        self._collector = None
        self._publisher = None

        self._logger = logging.getLogger(__name__)
        self._agent_config = common.services.get(ServiceName.AGENT_CONFIG)

        if self._agent_config.stats_enabled is None or self._agent_config.stats_enabled is False:
            self._logger.info("Stats not configured, Stats plugin will be in silent mode")
            return

        if self._agent_config.stats_store_endpoint is None or self._agent_config.stats_store_port is None:
            self._logger.error("Stats endpoint/port not specified, though stats are enabled")
            return

        self._db = MemoryTimeSeriesDB()
        self._collector = StatsCollector(self._db)
        self._collector.configure_collectors()
        self._publisher = StatsPublisher(self._db)
        self._publisher.configure_publishers()

    def get_db(self):
        return self._db

    def get_collector(self):
        return self._collector

    def get_publisher(self):
        return self._publisher

    def _error_response(self, code, error, response):
        self._logger.debug(error)
        response.result = code
        response.error = str(error)
        return response

    def start(self):
        if self._agent_config.stats_enabled is None or \
           self._agent_config.stats_enabled is False or \
           self._agent_config.stats_store_endpoint is None or \
           self._agent_config.stats_store_port is None:
            return

        if self._collector is None:
            self._logger.error("Stats collector is not initialized at init time. Stats plugin will be silent")
            return

        if self._publisher is None:
            self._logger.error("Stats publisher is not initialized at init time. Stats plugin will be silent")
            return

        if self._db is None:
            self._logger.error("Stats internal DB is not initialized at init time. Stats plugin will be silent")
            return

        self._collector.start_collection()
        self._publisher.start_publishing()

    @log_request
    @error_handler(SetCollectionLevelResponse, SetCollectionLevelResultCode)
    def set_collection_level(self, request):
        """Sets the level to collect stats at.

        :type request: SetCollectionLevelRequest
        :rtype: SetCollectionLevelResponse
        """
        response = SetCollectionLevelResponse()
        if request.level < 0 or request.level > 4:
            return self._error_response(
                SetCollectionLevelResultCode.INVALID_LEVEL,
                "Invalid stats level %d" % request.level,
                response)

        response.result = SetCollectionLevelResultCode.OK
        return response
