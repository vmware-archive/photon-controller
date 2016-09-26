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


class NetworkManager(object):
    """ ESX network manager implementation.
    """

    def __init__(self, vim_client):
        self.vim_client = vim_client
        self.logger = logging.getLogger(__name__)

    def get_vm_networks(self):
        """ Return the list of VM Networks on this host. """
        return self.vim_client.get_networks()

    def get_networks(self):
        """ Return all networks; translate into thrift representation. """
        networks = []

        # Add VM networks to network list
        for portgroup in self.vim_client.get_networks():
            network = Network(portgroup.name, [NetworkType.VM])
            networks.append(network)

        return networks
