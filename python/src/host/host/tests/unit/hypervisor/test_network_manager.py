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

from host.tests.unit.test_host_handler import MockPortgroup
from mock import MagicMock
from hamcrest import *  # noqa
from pyVmomi import vim

from gen.resource.ttypes import Network, NetworkType
from host.hypervisor.network_manager import NetworkManager

MGMT_NETWORK_NAME = "Management Network"


def _net_config(type, name=MGMT_NETWORK_NAME):
    net_config = vim.host.VirtualNicManager.NetConfig(nicType=type)
    net_config.candidateVnic = [vim.host.VirtualNic(portgroup=name)]
    return net_config


class TestNetworkManager(unittest.TestCase):

    def test_get_networks(self):
        """ Test normal get_network workflow:
        - call vim_client correctly.
        - collect network types and translate them to thrift representation
        correctly.
        """
        vim_client = MagicMock()
        vim_client.get_networks.return_value = [MockPortgroup("VM Network"), MockPortgroup("VM Network 2")]
        network_manager = NetworkManager(vim_client)
        networks = network_manager.get_networks()

        assert_that(networks, has_length(2))
        # Verify 2 VM networks
        assert_that(networks, has_item(Network("VM Network",
                                               [NetworkType.VM])))
        assert_that(networks, has_item(Network("VM Network 2",
                                               [NetworkType.VM])))

    def test_get_vm_netwokrs(self):
        vim_client = MagicMock()
        vim_client.get_networks.return_value = ["VM Network", "VM Network 2"]

        # Verify the function returns the actual network list.
        network_manager = NetworkManager(vim_client)
        networks = network_manager.get_vm_networks()
        self.assertEqual(networks, ["VM Network", "VM Network 2"])

    def _find(self, network_name, networks):
        network = [network for network in networks
                   if network.id == network_name]
        assert_that(network, has_length(1))
        return network[0]
