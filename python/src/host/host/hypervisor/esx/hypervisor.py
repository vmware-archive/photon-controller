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
import logging

from common.util import suicide
from host.hypervisor.esx.datastore_manager import EsxDatastoreManager
from host.hypervisor.esx.disk_manager import EsxDiskManager
from host.hypervisor.esx.http_disk_transfer import HttpNfcTransferer
from host.hypervisor.esx.nfc_image_transfer import NfcImageTransferer
from host.hypervisor.esx.network_manager import EsxNetworkManager
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
        self.host_client = EsxHypervisor.create_host_client(errback=lambda: suicide())
        self.host_client.connect_local()
        atexit.register(lambda client: client.disconnect(), self.host_client)

        self.datastore_manager = EsxDatastoreManager(
            self, agent_config.datastores, agent_config.image_datastores)
        self.logger.debug("longz.1")
        # datastore manager needs to update the cache when there is a change.
        self.host_client.add_update_listener(self.datastore_manager)
        self.logger.debug("longz.2")
        self.vm_manager = EsxVmManager(self.host_client, self.datastore_manager)
        self.logger.debug("longz.3")
        self.disk_manager = EsxDiskManager(self.host_client, self.datastore_manager)
        self.logger.debug("longz.4")
        self.image_manager = EsxImageManager(self.host_client, self.datastore_manager)
        self.logger.debug("longz.5")
        self.network_manager = EsxNetworkManager(self.host_client, agent_config.networks)
        self.logger.debug("longz.6")
        self.system = EsxSystem(self.host_client)
        self.logger.debug("longz.7")
        self.image_manager.monitor_for_cleanup()
        self.logger.debug("longz.8")
        if self.host_client.host_version.startswith("5."):
            # some CI hosts are not upgraded yet - use http transfer for ESX5 hosts.
            self.image_transferer = HttpNfcTransferer(
                    self.host_client,
                    self.datastore_manager.image_datastores())
        else:
            self.image_transferer = NfcImageTransferer(self.host_client)
        self.logger.debug("longz.9")
        atexit.register(self.image_manager.cleanup)
        self.logger.debug("longz.10")

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

    def acquire_vim_ticket(self):
        return self.host_client.acquire_clone_ticket()

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

    def prepare_receive_image(self, image_id, datastore):
        return self.image_manager.prepare_receive_image(image_id, datastore)

    def receive_image(self, image_id, datastore, imported_vm_name, metadata):
        self.image_manager.receive_image(image_id, datastore, imported_vm_name, metadata)

    def set_memory_overcommit(self, memory_overcommit):
        # Enable/Disable large page support. If this host is removed
        # from the deployment, large page support will need to be
        # explicitly updated by the user.
        disable_large_pages = memory_overcommit > 1.0
        self.host_client.set_large_page_support(disable=disable_large_pages)
