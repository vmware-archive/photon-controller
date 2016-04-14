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

"""Module to manage Fake hypervisor modules"""

import logging
import os
import socket
import uuid
from host.hypervisor.fake.datastore_manager import FakeDatastoreManager

from common.file_util import mkdtemp
from host.hypervisor.fake.disk_manager import FakeDiskManager
from host.hypervisor.fake.image_manager import FakeImageManager
from host.hypervisor.fake.network_manager import FakeNetworkManager
from host.hypervisor.fake.system import FakeSystem
from host.hypervisor.fake.vm_manager import FakeVmManager


class FakeHypervisor(object):

    """Manage Fake Hypervisor."""

    def __init__(self, agent_config):
        self.logger = logging.getLogger(__name__)
        prefix = socket.gethostname()
        suffix = str(agent_config.host_port)
        self._uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS, prefix + suffix))

        tempdir = mkdtemp(prefix='disk', delete=True)
        self.disk_manager = FakeDiskManager(self,
                                            os.path.join(tempdir, 'disk'))
        self.vm_manager = FakeVmManager(self)
        self.network_manager = FakeNetworkManager(self, agent_config.networks)
        self.system = FakeSystem(self)
        datastores = agent_config.datastores
        # For fake hypervisor, we assume there is always one image datastore.
        if agent_config.image_datastores:
            image_datastore = list(agent_config.image_datastores)[0]["name"]
        else:
            image_datastore = None
        self.datastore_manager = FakeDatastoreManager(self.system, datastores,
                                                      image_datastore)
        self.image_manager = FakeImageManager(self, image_datastore)

        self.image_manager.copy_to_datastores(
            "ttylinux",
            self.datastore_manager.get_datastore_ids())

    @property
    def uuid(self):
        return self._uuid

    def check_image(self, image_id, datastore_id):
        return self.image_manager.\
            check_image(image_id, datastore_id)

    @staticmethod
    def datastore_id(name):
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, str(name)))

    def acquire_vim_ticket(self):
        return 'cst-526d7e8f-b126-1686-9b49-bde6f34f0be8--tp-71-18-5C-87' + \
            '-F9-DB-C1-B9-D7-92-7A-19-99-1E-45-56-73-D6-CC-99'

    def acquire_cgi_ticket(self, url, op):
        return '52524918-2252-f24d-3a2b-2609c0fe795e'

    def add_update_listener(self, listener):
        # Only triggers VM update listener
        self.vm_manager.add_update_listener(listener)

    def remove_update_listener(self, listener):
        self.vm_manager.remove_update_listener(listener)

    def transfer_image(self, source_image_id, source_datastore,
                       destination_image_id, destination_datastore,
                       host, port):
        return ""

    def prepare_receive_image(self, image_id, datastore):
        pass

    def receive_image(self, image_id, datastore, imported_vm_name, metadata):
        pass

    def set_memory_overcommit(self, memory_overcommit):
        pass
