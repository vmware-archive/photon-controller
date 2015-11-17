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

"""Module to manage ESX modules"""

import atexit
import copy
import logging

from thrift import TSerialization

from common.util import suicide
import gen.hypervisor.esx.ttypes
from host.hypervisor.esx.datastore_manager import EsxDatastoreManager
from host.hypervisor.esx.disk_manager import EsxDiskManager
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer
from host.hypervisor.esx.network_manager import EsxNetworkManager
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_manager import EsxVmManager
from host.hypervisor.esx.image_manager import EsxImageManager
from host.hypervisor.esx.system import EsxSystem


class EsxHypervisor(object):
    """Manage ESX Hypervisor."""

    def __init__(self, agent_config):
        self.logger = logging.getLogger(__name__)

        # If VimClient's housekeeping thread failed to update its own cache,
        # call errback to commit suicide. Watchdog will bring up the agent
        # again.
        errback = lambda: suicide()
        self.vim_client = VimClient(wait_timeout=agent_config.wait_timeout,
                                    errback=errback)
        atexit.register(lambda client: client.disconnect(), self.vim_client)

        self._uuid = self.vim_client.host_uuid
        self.set_memory_overcommit(agent_config.memory_overcommit)

        image_datastores = [ds["name"] for ds in agent_config.image_datastores]
        self.datastore_manager = EsxDatastoreManager(
            self, agent_config.datastores, image_datastores)
        # datastore manager needs to update the cache when there is a change.
        self.vim_client.add_update_listener(self.datastore_manager)
        self.vm_manager = EsxVmManager(self.vim_client, self.datastore_manager)
        self.disk_manager = EsxDiskManager(self.vim_client,
                                           self.datastore_manager)
        self.image_manager = EsxImageManager(self.vim_client,
                                             self.datastore_manager)
        self.network_manager = EsxNetworkManager(self.vim_client,
                                                 agent_config.networks)
        self.system = EsxSystem(self.vim_client)
        self.image_manager.monitor_for_cleanup()
        self.image_transferer = HttpNfcTransferer(self.vim_client,
                                                  image_datastores)
        atexit.register(self.image_manager.cleanup)

    @property
    def uuid(self):
        return self._uuid

    @property
    def config(self):
        config = gen.hypervisor.esx.ttypes.EsxConfig()
        return TSerialization.serialize(config)

    def normalized_load(self):
        """ Return the maximum of the normalized memory/cpu loads"""
        memory = self.system.memory_info()
        memory_load = memory.used * 100 / memory.total

        # get average cpu load percentage in past 20 seconds
        # since hostd takes a sample in every 20 seconds
        # we use the min 20secs here to get the latest
        # CPU active average over 1 minute
        host_stats = copy.copy(self.vim_client.get_perf_manager_stats(20))

        cpu_load = host_stats['rescpu.actav1'] / 100

        return max(memory_load, cpu_load)

    def check_image(self, image_id, datastore_id):
        return self.image_manager.check_image(
            image_id, self.datastore_manager.datastore_name(datastore_id)
        )

    def acquire_vim_ticket(self):
        return self.vim_client.acquire_clone_ticket()

    def acquire_cgi_ticket(self, url, op):
        return self.vim_client.acquire_cgi_ticket(url, op)

    def add_update_listener(self, listener):
        self.vim_client.add_update_listener(listener)

    def remove_update_listener(self, listener):
        self.vim_client.remove_update_listener(listener)

    def transfer_image(self, source_image_id, source_datastore,
                       destination_image_id, destination_datastore,
                       host, port):
        return self.image_transferer.send_image_to_host(
            source_image_id, source_datastore,
            destination_image_id, destination_datastore, host, port)

    def receive_image(self, image_id, datastore, imported_vm_name):
        self.image_manager.receive_image(
            image_id, datastore, imported_vm_name)

    def set_memory_overcommit(self, memory_overcommit):
        # Enable/Disable large page support. If this host is removed
        # from the deployment, large page support will need to be
        # explicitly updated by the user.
        disable_large_pages = memory_overcommit > 1.0
        self.vim_client.set_large_page_support(disable=disable_large_pages)