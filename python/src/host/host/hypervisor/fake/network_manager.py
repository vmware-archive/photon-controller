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


from gen.resource.ttypes import NetworkType, Network
from host.hypervisor.network_manager import NetworkManager


class FakeNetworkManager(NetworkManager):
    """ Fake network manager implementation.
    """

    def __init__(self, hypervisor, vm_networks, networks=None):
        """
        :param hypervisor: fake hypervisor
        :type hypervisor: FakeHypervisor
        :param networks: configured networks on the host
        :type networks: list of gen.resource.ttypes.Network
        """
        self._vm_networks = vm_networks
        self._networks = networks or self._default_networks()
        pass

    def get_networks(self):
        """ Fake implementation. It returns 2 networks, one VM network,
        the other is management network which is tagged as all management
        types.
        """
        return self._networks

    def get_vm_networks(self):
        """ Fake implementation. It returns the vm_network configured
        """
        return self._vm_networks

    def _default_networks(self):
        """ Default networks
        """
        mgmt_types = [
            NetworkType.FT_LOGGING,
            NetworkType.MANAGEMENT,
            NetworkType.VSPHERE_REPLICATION,
            NetworkType.VSPHERE_REPLICATION_NFC,
            NetworkType.VMOTION,
            NetworkType.VSAN,
        ]
        mgmt_network = Network("Management Network", mgmt_types)
        vm_network = Network("VM Network", [NetworkType.VM])

        return [vm_network, mgmt_network]
