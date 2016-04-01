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

import abc
from common.kind import Flavor
import enum

from gen.host.ttypes import HostConfig as ThriftHostConfig
from gen.resource.ttypes import Datastore as ThriftDatastore
from gen.resource.ttypes import Disk as ThriftDisk
from gen.resource.ttypes import DiskImage as ThriftDiskImage
from gen.resource.ttypes import Resource as ThriftResource
from gen.resource.ttypes import ResourceConstraintType
from gen.resource.ttypes import ResourcePlacementType
from gen.resource.ttypes import ResourcePlacement
from gen.resource.ttypes import ResourcePlacementList
from gen.resource.ttypes import State as ThriftState
from gen.resource.ttypes import Vm as ThriftVm
from gen.roles.ttypes import ChildInfo as ThriftChildInfo
from gen.resource.ttypes import CloneType


@enum.unique
class State(enum.Enum):
    STARTED = 0
    STOPPED = 1
    SUSPENDED = 2


class BaseResource(object):

    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def to_thrift(self):
        pass


class Resource(BaseResource):
    """Resource"""

    def __init__(self, vm=None, disks=[]):
        """Create Resource
        :type vm: Vm
        :type disks: list of Disk
        """
        self.vm = vm
        self.disks = disks

    @staticmethod
    def from_thrift(thrift_object):
        instance = Resource()

        if thrift_object.vm:
            instance.vm = Vm.from_thrift(thrift_object.vm)

        if thrift_object.disks:
            instance.disks = [Disk.from_thrift(thrift_disk)
                              for thrift_disk in thrift_object.disks]

        return instance

    def to_thrift(self):
        return ThriftResource(
            self.vm.to_thrift(),
            [disk.to_thrift() for disk in self.disks]
        )


class Vm(BaseResource):
    """Virtual machine"""

    def __init__(self, vm_id=None, flavor=None,
                 state=None, datastore=None, environment=None,
                 disks=[], datastore_name=None,
                 resource_constraints=[], tenant_id=None, project_id=None):
        """
        Create a new VM.
        :param vm_id:
        :param flavor:
        :param state:
        :param datastore: str, id of datastore where vm is created
        :param datastore_name: str, name of datastore where vm is created
        :param environment:
        :param disks:
        :param resource_constraints:
        :param tenant_id: str, tenant id of the vm
        :param project_id: str, project id of the vm
        :return:
        """
        self.id = vm_id
        self.flavor = flavor
        self.state = state
        self.datastore = datastore
        self.datastore_name = datastore_name
        self.environment = environment
        self.disks = disks
        self.resource_constraints = resource_constraints
        self.placement = None
        self.networks = []
        self.tenant_id = tenant_id
        self.project_id = project_id

    @staticmethod
    def from_thrift_constraints(thrift_object):
        """Parse vm constraints, skipping scheduling only constraints

        :type ThriftVm
        :rtype list of ResourceConstraint
        """
        if not hasattr(thrift_object, 'resource_constraints') or thrift_object.resource_constraints is None:
            return []

        vm_constraints = []
        for thrift_constraint in thrift_object.resource_constraints:
            if not hasattr(thrift_constraint, 'scheduling_only') or not thrift_constraint.scheduling_only:
                vm_constraints.append(thrift_constraint)

        return vm_constraints

    @staticmethod
    def from_thrift(thrift_object):
        instance = Vm(
            vm_id=thrift_object.id,
            flavor=Flavor.from_thrift(thrift_object.flavor_info),
            state=State(thrift_object.state),
            environment=thrift_object.environment,
            tenant_id=thrift_object.tenant_id,
            project_id=thrift_object.project_id,
            resource_constraints=Vm.from_thrift_constraints(thrift_object)
        )
        if thrift_object.disks:
            instance.disks = [Disk.from_thrift(d)
                              for d in thrift_object.disks]
        if thrift_object.datastore:
            instance.datastore = thrift_object.datastore.id

        return instance

    def to_thrift(self):
        disks = [
            disk.to_thrift()
            for disk in self.disks
        ]

        resource_constraints = self.resource_constraints

        thrift_state = getattr(ThriftState, self.state.name)

        thrift_vm = ThriftVm(
            self.id, self.flavor.name,
            thrift_state, None, self.environment,
            disks, self.flavor.to_thrift(), resource_constraints,
            self.tenant_id, self.project_id,
        )

        if self.datastore:
            thrift_vm.datastore = ThriftDatastore(id=self.datastore,
                                                  name=self.datastore_name)

        return thrift_vm


class Disk(BaseResource):

    def __init__(self, disk_id=None, flavor=None,
                 persistent=None, new_disk=None,
                 capacity_gb=None, image=None,
                 datastore=None, constraints=None):
        """
        Create a new disk.
        :param disk_id:
        :param flavor:
        :param persistent:
        :param new_disk:
        :param capacity_gb:
        :param image:
        :param datastore:
        :param constraints:
        :return:
        """
        self.id = disk_id
        self.flavor = flavor
        self.persistent = persistent
        self.new_disk = new_disk
        self.capacity_gb = capacity_gb
        self.image = image
        self.datastore = datastore
        self.constraints = constraints
        self.placement = None

    @staticmethod
    def from_thrift(thrift_object):
        flavor = Flavor.from_thrift(thrift_object.flavor_info)
        image = DiskImage.from_thrift(thrift_object.image)
        constraints = Disk.from_thrift_constraints(thrift_object)

        instance = Disk(
            thrift_object.id,
            flavor,
            thrift_object.persistent,
            thrift_object.new_disk,
            thrift_object.capacity_gb,
            image,
            None,
            constraints
        )

        if thrift_object.datastore:
            instance.datastore = thrift_object.datastore.id

        return instance

    def to_thrift(self):
        thrift_disk_image = None
        thrift_datastore = None
        thrift_constraints = None
        if self.image:
            thrift_disk_image = self.image.to_thrift()
        if self.datastore:
            thrift_datastore = ThriftDatastore(self.datastore)
        if self.constraints:
            thrift_constraints = self.to_thrift_constraints()

        return ThriftDisk(
            self.id, self.flavor.name, self.persistent, self.new_disk,
            self.capacity_gb, thrift_disk_image, thrift_datastore,
            self.flavor.to_thrift(), thrift_constraints
        )

    @staticmethod
    def from_thrift_constraints(thrift_object):
        """Parse disk constraints, select datastores, datastore_tags only

        :type ThriftDisk
        :rtype list of ResourceConstraint
        """
        if not hasattr(thrift_object, 'resource_constraints') or thrift_object.resource_constraints is None:
            return None

        disk_constraints = []
        for thrift_constraint in thrift_object.resource_constraints:
            if not hasattr(thrift_constraint, 'scheduling_only') or not thrift_constraint.scheduling_only:
                if thrift_constraint.type in (ResourceConstraintType.DATASTORE, ResourceConstraintType.DATASTORE_TAG):
                    disk_constraints.append(thrift_constraint)

        return disk_constraints

    def to_thrift_constraints(self):
        """Parse disk constraints, select datastores only

        :type ThriftDisk
        :rtype list of ResourceConstraint
        """
        if not self.constraints:
            return None

        disk_constraints = []
        for constraint in self.constraints:
            if constraint.type in \
                (ResourceConstraintType.DATASTORE,
                 ResourceConstraintType.DATASTORE_TAG):
                disk_constraints.append(constraint)

        return disk_constraints


class DiskImage(BaseResource):
    """Disk image"""

    FULL_COPY = CloneType.FULL_COPY
    COPY_ON_WRITE = CloneType.COPY_ON_WRITE

    def __init__(self, image_id=None, clone_type=None):
        """Create a disk image.

        :type image_id: str
        :type clone_type: int
        """
        self.id = image_id
        self.clone_type = clone_type

    @staticmethod
    def from_thrift(thrift_object):
        if not thrift_object:
            return None
        if thrift_object.clone_type is not CloneType.FULL_COPY and \
           thrift_object.clone_type is not CloneType.COPY_ON_WRITE:
            raise ValueError("Invalid clone type")
        return DiskImage(thrift_object.id, thrift_object.clone_type)

    def to_thrift(self):
        return ThriftDiskImage(self.id, self.clone_type)


class Host(BaseResource):
    """Hack: HostConfig"""

    def __init__(self, agent_id=None, datastores=[], availability_zone=None,
                 hypervisor=None, vm_network=None):
        """Create host

        :type agent_id: str
        :type datastores: list of Datastore
        :type availability_zone: str
        :type hypervisor: binary
        :type vm_network: str
        """
        self.agent_id = agent_id
        self.datastores = datastores
        self.availability_zone = availability_zone
        self.hypervisor = hypervisor
        self.vm_network = vm_network

    @staticmethod
    def from_thrift(thrift_object):
        instance = Host(
            thrift_object.agent_id
        )
        instance.datastores = [ds.id for ds in thrift_object.datastores]
        return instance

    def to_thrift(self):
        thrift_host_config = ThriftHostConfig(self.agent_id,
                                              self.availability_zone)
        thrift_host_config.datastores = [ThriftDatastore(ds)
                                         for ds in self.datastores]
        if self.availability_zone:
            thrift_host_config.availability_zone = self.availability_zone
        if self.hypervisor:
            thrift_host_config.hypervisor = self.hypervisor
        if self.vm_network:
            thrift_host_config.vm_network = self.vm_network

        return thrift_host_config


class ChildInfo(BaseResource):
    """Child info, representing thrift interface on the child and resource
       constraint list
    """

    def __init__(self, id, address, port, constraints=set()):
        self.id = id
        self.address = address
        self.port = port
        self.constraints = constraints

    def to_thrift(self):
        # Set to List
        constraints = None
        if self.constraints:
            constraints = list(self.constraints)
        # for constraint in self.constraints:
        #   constraints.append(constraint)
        return ThriftChildInfo(self.id, self.address, self.port, constraints)


class AgentResourcePlacement(BaseResource):
    """
    Each Entry in the placement plan is identified by
    a type, a resource id and a container id
    """

    VM = ResourcePlacementType.VM
    DISK = ResourcePlacementType.DISK
    NETWORK = ResourcePlacementType.NETWORK

    def __init__(self, type, resource_id, container_id):
        """Placement score.

        :type utilization: int
        :type transfer: int
        """
        self.type = type
        self.resource_id = resource_id
        self.container_id = container_id

    def to_thrift(self):
        thrift_placement_entry = ResourcePlacement(
            type=self.type,
            resource_id=self.resource_id,
            container_id=self.container_id)
        return thrift_placement_entry

    @staticmethod
    def from_thrift(thrift_object):
        if not thrift_object:
            return None
        placement_entry = AgentResourcePlacement(
            type=thrift_object.type,
            resource_id=thrift_object.resource_id,
            container_id=thrift_object.container_id)
        return placement_entry

    def __repr__(self):
        return \
            "AgentResourcePlacement(type=%r, " \
            "resource_id=%r, " \
            "container_id=%r)" \
            % (self.type, self.resource_id, self.container_id)


class AgentResourcePlacementList(BaseResource):
    def __init__(self, placements):
        """
        :param placements:
        :return:
        """
        self.placements = placements

    def to_thrift(self):
        thrift_list = \
            [entry.to_thrift() for entry in self.placements]
        return ResourcePlacementList(thrift_list)

    def __repr__(self):
        return self.placements.__repr__()

    @staticmethod
    def from_thrift(thrift_list):
        if not thrift_list:
            return None

        placements = \
            [AgentResourcePlacement.from_thrift(entry)
             for entry in thrift_list.placements]

        return AgentResourcePlacementList(placements)

    """
    The following methods are used to copy the placement
    information for the individual resources. The
    placement information is computed by the agent during
    a placement request and echoed back here through the FE.
    In case of VM there is placement information for the VM
    itself and for all disks in the "disks" list.
    """
    @staticmethod
    def unpack_placement_list_vm(placement_list, vm):
        """
        :param placement_list: AgentResourcePlacementList
        :param vm:
        :return
        """

        if not placement_list:
            return

        for placement in placement_list.placements:
            if placement.type is \
                    ResourcePlacementType.VM:
                vm.placement = placement
                continue

            if placement.type is ResourcePlacementType.NETWORK and \
               placement.resource_id == vm.id:
                    vm.networks.append(placement.container_id)

        AgentResourcePlacementList.\
            unpack_placement_list_disks(placement_list,
                                        vm.disks)
        return None

    @staticmethod
    def unpack_placement_list_disks(placement_list, disks):
        """
        :param placement_list: AgentResourcePlacementList
        :param disks:
        :return:
        """

        if not placement_list:
            return

        for placement in placement_list.placements:
            if placement.type is not \
                    ResourcePlacementType.DISK:
                continue
            for disk in disks:
                if disk.id == placement.resource_id:
                    disk.placement = placement
        return None
