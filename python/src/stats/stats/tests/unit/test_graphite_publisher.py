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

    @patch("pickle.dumps", return_value="picklemsg")
    @patch("struct.pack", return_value="packed_header")
    def test_build_pickle_message(self, _pack, _dumps):
        hostname = "fake-hostname"
        stats = {"key1": [(1000000, 1), (1000020, 2)]}
        pub = graphite_publisher.GraphitePublisher(
            hostname=hostname, carbon_host="10.10.10.10", carbon_port=2004, host_tags="tag")

        result = pub._build_pickled_data_msg(stats)
        expected_data = [('photon.fake-hostname.tag.key1', (1000000, 1)),
                         ('photon.fake-hostname.tag.key1', (1000020, 2))]
        _dumps.assert_called_once_with(expected_data, protocol=2)
        _pack.assert_called_once_with("!L", len("picklemsg"))
        assert_that(result, is_("packed_header" + "picklemsg"))

    @patch("pickle.dumps", return_value="picklemsg")
    @patch("struct.pack", return_value="packed_header")
    def test_build_lines_message(self, _pack, _dumps):
        hostname = "fake-hostname"
        stats = {"key1": [(1000000, 1), (1000020, 2)]}
        pub = graphite_publisher.GraphitePublisher(
            hostname=hostname, carbon_host="10.10.10.10", carbon_port=2004, host_tags="tag", use_pickle_format=False)

        result = pub._build_pickled_data_msg(stats)
        expected_data = 'photon.fake-hostname.tag.key1 1 1000000\n' \
                        'photon.fake-hostname.tag.key1 2 1000020\n'
        assert_that(result, is_(expected_data))

    def test_host_tags_are_sensitized_and_sorted(self):
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(None), is_(""))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(" "), is_(""))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(" , "), is_(""))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags("abc"), is_(".abc"))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(" abc "), is_(".abc"))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags("abc,xyz.zzz"), is_(".abc.xyz-zzz"))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(" xyz, abc "), is_(".abc.xyz"))
        assert_that(graphite_publisher.GraphitePublisher.get_sensitized_tags(" xyz, abc, mno "), is_(".abc.mno.xyz"))

    @patch(
        "stats.graphite_publisher.GraphitePublisher._build_pickled_data_msg")
    @patch("socket.socket.connect")
    @patch("socket.socket.sendall")
    @patch("socket.socket.close")
    def test_publish(self, _close, _sendall, _connect, _build_msg):
        hostname = "fake-hostname"
        stats = {"key1": [(1000000, 1), (1000020, 2)]}
        pub = graphite_publisher.GraphitePublisher(
            hostname=hostname, carbon_host="10.10.10.10", carbon_port=2004)

        pub.publish(stats)

        _connect.assert_called_once_with(("10.10.10.10", 2004))
        _sendall.assert_called_once_with(_build_msg.return_value)
        _close.assert_called_once_with()
