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

import atexit
import logging

from common.util import suicide
from host.hypervisor.datastore_manager import DatastoreManager
from host.hypervisor.disk_manager import DiskManager
from host.hypervisor.network_manager import NetworkManager
from host.hypervisor.vm_manager import VmManager
from host.hypervisor.system import System
from host.hypervisor.resources import Resource
from host.image.image_manager import ImageManager
from host.image.nfc_image_transfer import NfcImageTransferer
from host.image.image_monitor import ImageMonitor
from host.placement.placement_manager import PlacementManager
from host.placement.placement_manager import PlacementOption


class Hypervisor(object):
    def __init__(self, agent_config):
        self.logger = logging.getLogger(__name__)

        # If VimClient's housekeeping thread failed to update its own cache,
        # call errback to commit suicide. Watchdog will bring up the agent
        # again.
        self.host_client = Hypervisor.create_host_client(errback=lambda: suicide())
        self.host_client.connect_local()
        atexit.register(lambda client: client.disconnect(), self.host_client)

        self.datastore_manager = DatastoreManager(
            self, agent_config.datastores, agent_config.image_datastores)
        # datastore manager needs to update the cache when there is a change.
        self.host_client.add_update_listener(self.datastore_manager)
        self.vm_manager = VmManager(self.host_client, self.datastore_manager)
        self.disk_manager = DiskManager(self.host_client, self.datastore_manager)
        self.image_manager = ImageManager(self.host_client, self.datastore_manager)
        self.network_manager = NetworkManager(self.host_client)
        self.system = System(self.host_client)

        options = PlacementOption(agent_config.memory_overcommit,
                                  agent_config.cpu_overcommit,
                                  agent_config.image_datastores)
        self.placement_manager = PlacementManager(self, options)

        self.image_monitor = ImageMonitor(self.datastore_manager,
                                          self.image_manager,
                                          self.vm_manager)
        self.image_manager.monitor_for_cleanup()
        self.image_transferer = NfcImageTransferer(self.host_client)
        atexit.register(self.image_manager.cleanup)

    @staticmethod
    def create_host_client(auto_sync=True, errback=None):
        try:
            # check whether attache is installed. If not, find_module will throw ImportError.
            from host.hypervisor.esx.attache_client import AttacheClient
            return AttacheClient(auto_sync, errback)
        except ImportError:
            from host.hypervisor.esx.vim_client import VimClient
            return VimClient(auto_sync, errback)

    def check_image(self, image_id, datastore_id):
        return self.image_manager.check_image(
            image_id, self.datastore_manager.datastore_name(datastore_id)
        )

    def get_resources(self):
        return self.vm_manager.get_resources()

    def get_vm_resource(self, vm_id):
        vm = self.vm_manager.get_resource(vm_id)
        resource = Resource(vm=vm)
        return resource

    def acquire_vim_ticket(self):
        return self.host_client.get_vim_ticket()

    def add_update_listener(self, listener):
        self.host_client.add_update_listener(listener)

    def remove_update_listener(self, listener):
        self.host_client.remove_update_listener(listener)

    def transfer_image(self, source_image_id, source_datastore,
                       destination_image_id, destination_datastore,
                       host, port):
        return self.image_transferer.send_image_to_host(
            source_image_id, source_datastore,
            destination_image_id, destination_datastore, host, port)

    @property
    def memory_overcommit(self):
        return self.placement_manager.memory_overcommit

    def set_memory_overcommit(self, value):
        self.placement_manager.memory_overcommit = value
        # Enable/Disable large page support. If this host is removed
        # from the deployment, large page support will need to be
        # explicitly updated by the user.
        disable_large_pages = value > 1.0
        self.host_client.set_large_page_support(disable=disable_large_pages)

    @property
    def cpu_overcommit(self):
        return self.placement_manager.cpu_overcommit

    def set_cpu_overcommit(self, value):
        self.placement_manager.cpu_overcommit = value
