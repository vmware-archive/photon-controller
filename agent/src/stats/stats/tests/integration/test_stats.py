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
import time
import unittest

from hamcrest import *  # noqa
from gen.stats.plugin import StatsService
from nose.plugins.skip import SkipTest
from thrift.transport import TTransport

from common.photon_thrift.direct_client import DirectClient
from gen.host import Host

from gen.stats.plugin.ttypes import SetCollectionLevelRequest
from gen.stats.plugin.ttypes import SetCollectionLevelResultCode


class TestStats(unittest.TestCase):
    def setUp(self):
        self.logger = logging.getLogger(__name__)
        from testconfig import config
        if "remote_servers" not in config:
            raise SkipTest("remote_servers are not specified")

        self.remote_servers = config["remote_servers"].split(",")

        self.client = self.connect_client("Host", Host.Client,
                                          self.remote_servers[0])
        self.stats_client = self.connect_client("StatsService",
                                                StatsService.Client,
                                                self.remote_servers[0])

    def tearDown(self):
        self.client.close()
        self.stats_client.close()

    def connect_client(self, service, cls, server):
        """ Utility method to connect to a remote agent """
        max_sleep_time = 32
        sleep_time = 0.1
        while sleep_time < max_sleep_time:
            try:
                client = DirectClient(service, cls, server, 8835,
                                      client_timeout=30)
                client.connect()
                return client
            except TTransport.TTransportException:
                time.sleep(sleep_time)
                sleep_time *= 2
        self.fail("Cannot connect to host %s" % server)

    def test_set_collection_level(self):
        request = SetCollectionLevelRequest(level=5)
        res = self.stats_client.set_collection_level(reqeust)
        self.assertEqual(res.result,
                         SetCollectionLevelResultCode.INVALID_LEVEL)

        request = SetCollectionLevelRequest(level=2)
        res = self.stats_client.set_collection_level(request)
        self.assertEqual(res.result, SetCollectionLevelResultCode.OK)
