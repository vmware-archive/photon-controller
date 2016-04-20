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
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa


from gen.resource.ttypes import Host
from common.plugin import Plugin, ThriftService

from mock import MagicMock


service = ThriftService(
    name="Host",
    service=Host,
    handler=MagicMock(),
    num_threads=1,
)


class SamplePlugin(Plugin):
    """ Sample plugin for testing
    """

    def __init__(self):
        super(SamplePlugin, self).__init__("sample")

    def init(self):
        pass


class TestPlugin(unittest.TestCase):

    def test_add_remove_service(self):
        plugin = SamplePlugin()

        # Add the first service
        plugin.add_thrift_service(service)
        assert_that(plugin.thrift_services, has_length(1))

        # Add the same service
        plugin.add_thrift_service(service)
        assert_that(plugin.thrift_services, has_length(1))

        # Remove the service
        plugin.remove_thrift_service(service)
        assert_that(plugin.thrift_services, has_length(0))

    def test_add_bad_service(self):
        plugin = SamplePlugin()

        # Not allow to add a string as service
        self.assertRaises(AssertionError,
                          plugin.add_thrift_service, "Sample Service")
