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

"""Provides a wrapper around hypervisor specific modules."""

import abc
import logging

from host.hypervisor.image_monitor import ImageMonitor
from host.hypervisor.placement_manager import PlacementManager
from host.hypervisor.placement_manager import PlacementOption
from host.hypervisor.resources import Resource


class UpdateListener(object):
    """
    Abstract base class for host update listener.

    IMPORTANT: The underlying hypervisor holds a lock while notifying
    listeners, so these callbacks should be reasonably light-weight.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def networks_updated(self):
        """Gets called when there is a change in the list of networks."""
        pass

    @abc.abstractmethod
    def virtual_machines_updated(self):
        """Gets called when there is a change in the list of VMs."""
        pass

    @abc.abstractmethod
    def datastores_updated(self):
        """Gets called when tehre is a change in the list of datastores."""
        pass


class Hypervisor(object):
    """A class that wraps hypervisor functionality.

    Based on which hypervisor the agent was configured to use, this will setup
    the proper modules.
    """
    def __init__(self, hypervisor_type, availability_zone_id,
                 datastores, networks, image_datastore, port, wait_timeout,
                 memory_overcommit, cpu_overcommit,
                 image_datastore_for_vms, multi_agent_id=None):

        self._logger = logging.getLogger(__name__)

        if hypervisor_type == "esx":
            from esx.hypervisor import EsxHypervisor
            # This will throw an error if it can't connect to the local vim.
            self.hypervisor = EsxHypervisor(availability_zone_id, datastores,
                                            networks, image_datastore,
                                            wait_timeout,
                                            memory_overcommit > 1.0)
        elif hypervisor_type == "fake":
            from fake.hypervisor import FakeHypervisor
            self.hypervisor = FakeHypervisor(availability_zone_id, datastores,
                                             networks, image_datastore,
                                             port, multi_agent_id)
        else:
            raise ValueError("Invalid hypervisor")

        """
        The creation of the Hypervisors above translates datastore names
        into datastore ids. Methods that access datastores through this
        class should use datastore ids.
        """

        self.datastore_manager = self.hypervisor.datastore_manager
        self.disk_manager = self.hypervisor.disk_manager
        self.image_manager = self.hypervisor.image_manager
        self.vm_manager = self.hypervisor.vm_manager
        self.network_manager = self.hypervisor.network_manager
        self.system = self.hypervisor.system

        placement_option = PlacementOption(memory_overcommit, cpu_overcommit,
                                           image_datastore_for_vms)
        self.placement_manager = PlacementManager(self, placement_option)

        self.image_monitor = ImageMonitor(self.datastore_manager,
                                          self.image_manager,
                                          self.vm_manager)

    def add_update_listener(self, listener):
        """
        Adds an update listener.
        """
        if not issubclass(listener.__class__, UpdateListener):
            raise TypeError("Not a subclass of UpdateListener")
        self.hypervisor.add_update_listener(listener)

    def remove_update_listener(self, listener):
        """
        Removes an update listener.
        """
        if not issubclass(listener.__class__, UpdateListener):
            raise TypeError("Not a subclass of UpdateListener")
        self.hypervisor.remove_update_listener(listener)

    @property
    def uuid(self):
        return self.hypervisor.uuid

    @property
    def config(self):
        return self.hypervisor.config

    def check_image(self, image_id, datastore_id):
        return self.hypervisor.check_image(image_id, datastore_id)

    def normalized_load(self):
        return self.hypervisor.normalized_load()

    def get_resources(self):
        result = []
        if hasattr(self.vm_manager, "get_resources"):
            return self.vm_manager.get_resources()
        else:
            for vm_id in self.vm_manager.get_resource_ids():
                result.append(self.get_vm_resource(vm_id))
        return result

    def get_vm_resource(self, vm_id):
        vm = self.vm_manager.get_resource(vm_id)
        resource = Resource(vm=vm)
        return resource

    def acquire_vim_ticket(self):
        return self.hypervisor.acquire_vim_ticket()

    @property
    def memory_overcommit(self):
        return self.placement_manager.memory_overcommit

    def set_memory_overcommit(self, value):
        self.placement_manager.memory_overcommit = value

    @property
    def cpu_overcommit(self):
        return self.placement_manager.cpu_overcommit

    def set_cpu_overcommit(self, value):
        self.placement_manager.cpu_overcommit = value
