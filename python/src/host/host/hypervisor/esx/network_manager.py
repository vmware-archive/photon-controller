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

from gen.resource.ttypes import Network, NetworkType
from host.hypervisor.network_manager import NetworkManager


class EsxNetworkManager(NetworkManager):
    """ ESX network manager implementation.
    """

    def __init__(self, vim_client, configured_networks):
        self.vim_client = vim_client
        self.logger = logging.getLogger(__name__)
        self._configured_networks = configured_networks

    def _validate_networks(self, configured_networks):
        """ Validates the list of configured networks against the actual
            list of VM networks available on the host and returns the
            intersection unless the configured networks is an empty list.
            If the configured networks is empty, it simply returns the
            actual list.
        """
        networks = []
        actual_networks = self.vim_client.get_networks()
        if configured_networks:
            for network in configured_networks:
                if network not in actual_networks:
                    self.logger.warning("Unknown network %s: Skipping"
                                        % network)
                    continue
                networks.append(network)
        else:
            # HACK(mmutsuzaki) We are in the process of changing the installer
            # to not specify the network list. This is a temporary workaround
            # to support both new and old installers. If the installer does not
            # specify networks, return the actual networks on the host.
            networks = actual_networks
        return networks

    def get_vm_networks(self):
        """ Return the list of networks to use on this ESX server. """
        return self._validate_networks(self._configured_networks)

    def get_networks(self):
        """ This method will call vim_client to get a list of networks and
        translate them into thrift representation.

        - Get management networks and network types through VirtualNicManager.
        The type of the vnic infers the type of network it connects to.
        - Get VM networks from the network folder.

        Check _to_network_type method for the network type mappings.
        """
        net_configs = self.vim_client.get_network_configs()
        vm_networks = self.vim_client.get_networks()

        # Management networks
        mgmt_network_map = {}
        for net_config in net_configs:
            type = net_config.nicType

            if net_config.candidateVnic:
                for vnic in net_config.candidateVnic:
                    if vnic.portgroup not in mgmt_network_map:
                        mgmt_network_map[vnic.portgroup] = set()
                    mgmt_network_map[vnic.portgroup].add(type)

        # Add management networks in network list
        networks = []
        for network_name, type_set in mgmt_network_map.items():
            network = Network(network_name, [])

            for type_name in type_set:
                network.types.append(self._to_network_type(type_name))

            networks.append(network)

        # Add VM networks in network list
        for network_name in vm_networks:
            network = Network(network_name, [NetworkType.VM])
            networks.append(network)

        return networks

    @staticmethod
    def _to_network_type(type_name):
        if type_name == "faultToleranceLogging":
            return NetworkType.FT_LOGGING
        elif type_name == "management":
            return NetworkType.MANAGEMENT
        elif type_name == "vSphereReplication":
            return NetworkType.VSPHERE_REPLICATION
        elif type_name == "vSphereReplicationNFC":
            return NetworkType.VSPHERE_REPLICATION_NFC
        elif type_name == "vmotion":
            return NetworkType.VMOTION
        elif type_name == "vsan":
            return NetworkType.VSAN
        else:
            return NetworkType.OTHER
