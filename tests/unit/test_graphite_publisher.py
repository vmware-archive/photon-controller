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


""" test_graphite_publisher.py

    Unit tests for the Graphite publisher.
"""

import unittest

from hamcrest import *  # noqa
from mock import patch

from stats import graphite_publisher


class TestGraphitePublisher(unittest.TestCase):
    def test_plain_format_not_supported(self):
        self.assertRaises(
            NotImplementedError,
            graphite_publisher.GraphitePublisher,
            host_id="hostid", carbon_host="1.1.1.1", carbon_port=2003,
            use_pickle_format=False)

    @patch("pickle.dumps", return_value="picklemsg")
    @patch("struct.pack", return_value="packed_header")
    def test_build_pickle_message(self, _pack, _dumps):
        host_id = "fake-id"
        stats = {"key1": [(1000000, 1), (1000020, 2)]}
        pub = graphite_publisher.GraphitePublisher(
            host_id=host_id, carbon_host="10.10.10.10", carbon_port=2004)

        result = pub._build_pickled_data_msg(stats)
        expected_data = [('fake-id.key1', (1000000, 1)),
                         ('fake-id.key1', (1000020, 2))]
        _dumps.assert_called_once_with(expected_data, protocol=2)
        _pack.assert_called_once_with("!L", len("picklemsg"))
        assert_that(result, is_("packed_header" + "picklemsg"))

    @patch(
        "stats.graphite_publisher.GraphitePublisher._build_pickled_data_msg")
    @patch("socket.socket.connect")
    @patch("socket.socket.sendall")
    @patch("socket.socket.close")
    def test_publish(self, _close, _sendall, _connect, _build_msg):
        host_id = "fake-id"
        stats = {"key1": [(1000000, 1), (1000020, 2)]}
        pub = graphite_publisher.GraphitePublisher(
            host_id=host_id, carbon_host="10.10.10.10", carbon_port=2004)

        pub.publish(stats)

        _connect.assert_called_once_with(("10.10.10.10", 2004))
        _sendall.assert_called_once_with(_build_msg.return_value)
        _close.assert_called_once_with()
