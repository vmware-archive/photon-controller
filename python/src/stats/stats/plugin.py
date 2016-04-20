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

import common.plugin

from gen.stats.plugin import StatsService
from pysdk import connect
from .stats import StatsHandler
from .free_esx import SoapStubAdapterWrapper


class StatsPlugin(common.plugin.Plugin):
    def __init__(self):
        super(StatsPlugin, self).__init__("Stats", is_core=False)
        connect.SoapStubAdapter = SoapStubAdapterWrapper

    def init(self):
        self._handler = StatsHandler()
        service = common.plugin.ThriftService(
            name="StatsService",
            service=StatsService,
            handler=self._handler,
            num_threads=2,
        )

        self.add_thrift_service(service)

    def start(self):
        self._handler.start()

plugin = StatsPlugin()
