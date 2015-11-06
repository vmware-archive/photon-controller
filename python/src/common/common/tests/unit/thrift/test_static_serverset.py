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

from common.photon_thrift import ServerSetListener
from common.photon_thrift.serverset import StaticServerSet
from gen.common.ttypes import ServerAddress


class Listener(ServerSetListener):

    def __init__(self):
        self.servers = []

    def on_server_added(self, address):
        self.servers.append(ServerAddress(address[0], address[1]))

    def on_server_removed(self, address):
        pass


class TestStaticServerSet(unittest.TestCase):

    def test_basic(self):
        servers = [ServerAddress("server1", 1234),
                   ServerAddress("server2", 1234)]
        listenerA = Listener()
        listenerB = Listener()
        serverset = StaticServerSet(servers)
        serverset.add_change_listener(listenerA)
        serverset.add_change_listener(listenerB)
        self.assertEqual(servers, listenerA.servers)
        self.assertEqual(servers, listenerB.servers)
