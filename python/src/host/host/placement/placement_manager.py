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
import uuid
from enum import enum, Enum
from random import shuffle

from common.kind import Unit
from common.log import log_duration
from gen.resource.ttypes import ResourcePlacementType
from gen.resource.ttypes import ResourceConstraintType
from host.hypervisor.exceptions import DatastoreNotFoundException
from host.placement.disk_placement_manager import BestEffortPlaceEngine
from host.placement.disk_placement_manager import DatastoreSelector
from host.placement.disk_placement_manager import DisksPlacement
from host.placement.disk_placement_manager import DiskUtil
from host.placement.disk_placement_manager import ConstraintDiskPlaceEngine
from host.placement.disk_placement_manager import OptimalPlaceEngine
from host.placement.disk_placement_manager import PlaceResultCode
from host.placement.placement import AgentPlacementScore
from host.hypervisor.resources import AgentResourcePlacement
from host.hypervisor.vm_manager import VmNotFoundException


class InvalidReservationException(Exception):
    pass


class NoSuchResourceException(Exception):
    message_format = "{0} not available. {1}"

    def __init__(self, resource_type, reason):
        """
        :type resource_type: ResourceType
        :type reason: str
        """
        self.message = self.message_format.format(
            resource_type,
            reason)
        super(self.__class__, self).__init__(self.message)


class NotEnoughMemoryResourceException(Exception):
    pass


class NotEnoughCpuResourceException(Exception):
    pass


class NotEnoughDatastoreCapacityException(Exception):
    pass


class MissingPlacementDescriptorException(Exception):
    pass


@enum.unique
class ResourceType(Enum):
    CPU = 0
    MEMORY = 1
    DATASTORE = 2
    TRANSFER = 3
    IMAGE = 4
    DISK = 5


class PlacementOption(object):
    """The options for placement manager
    """

    def __init__(self, memory_overcommit, cpu_overcommit, image_datastores):
        self.memory_overcommit = memory_overcommit
        self.cpu_overcommit = cpu_overcommit
        self.image_datastores = image_datastores


class PlacementManager(object):
    """PlacementManager handles resource placement and reservation."""

    MAX_USAGE = 0.95
    NOT_OPTIMAL_DIVIDE_FACTOR = 10

    # Indicates ~ 80% consumption
    MEM_UTIL_SCORE_THRESHOLD = 20

    # Data store freespace threshold
    FREESPACE_THRESHOLD = 0.8

    # The image copy size (1GB) that translates to transfer score 1
    MAX_IMAGE_COPY_SIZE = 1024 * 1024 * 1024

    # Maximum score possible
    MAX_SCORE = 100

    def __init__(self, hypervisor, option):
        self._logger = logging.getLogger(__name__)
        self._hypervisor = hypervisor
        self._option = option
        self._reserved_vms = {}
        self._reserved_disks = {}
        self._diskutil = DiskUtil()
        self._vm_manager = hypervisor.vm_manager
        self._image_manager = hypervisor.image_manager
        self._datastore_manager = hypervisor.datastore_manager
        self._system = hypervisor.system
        self._optimal_placement = OptimalPlaceEngine(self._datastore_manager, option)
        self._best_effort_placement = BestEffortPlaceEngine(self._datastore_manager, option)
        self._constrainted_placement = ConstraintDiskPlaceEngine(self._datastore_manager, option)

    def reserve(self, vm, disks):
        """Reserve room for specified resources.

        :type vm: Vm
        :type disks: list of Disk
        :rtype: str
        :returns: reservation id
        """
        rid = str(uuid.uuid4())
        if vm:
            self._reserved_vms[rid] = vm
        if disks:
            self._reserved_disks[rid] = disks
        return rid

    def consume_disk_reservation(self, reservation_id):
        return self._consume(self._reserved_disks, reservation_id)

    def consume_vm_reservation(self, reservation_id):
        return self._consume(self._reserved_vms, reservation_id)

    def remove_disk_reservation(self, reservation_id):
        return self._remove(self._reserved_disks, reservation_id)

    def remove_vm_reservation(self, reservation_id):
        return self._remove(self._reserved_vms, reservation_id)

    @property
    def memory_overcommit(self):
        return self._option.memory_overcommit

    @memory_overcommit.setter
    def memory_overcommit(self, value):
        self._option.memory_overcommit = value

    @property
    def cpu_overcommit(self):
        return self._option.cpu_overcommit

    @cpu_overcommit.setter
    def cpu_overcommit(self, value):
        self._option.cpu_overcommit = value

    def place(self, vm, disks):
        """Place specified resources.

        :type vm: Vm
        :type disks: list of Disk
        :rtype: PlacementScore, Placement List
        :raises: ResourceConstraintException if can't place resources
        """

        """Check if there are datastores for placing vm or disks """
        if len(self._placeable_datastores()) == 0:
            raise NoSuchResourceException(ResourceType.DATASTORE, "No placeable datastores.")

        if vm:
            return self._place_vm(vm)
        elif disks:
            # API-FE only place one disk per request. We should modify thrift Resource.disks to singular.
            if len(disks) != 1:
                raise NoSuchResourceException(ResourceType.DISK, "Only one disk allowed in disk place request.")
            return self._place_disk(disks[0])

    def _extract_resource_constraints(self, constraints, resource_types):
        """ Extract ResourceConstraint with same resource_type

        :param constraints: list of ResourceConstraint
        :param resource_types: set of ResourceConstraintType
        :return: dict of {ResourceConstraintType: list of ResourceConstraint}
        """
        matched_constraints = {}

        for _constraint in constraints:
            if _constraint.type in resource_types:
                if _constraint.type not in matched_constraints:
                    matched_constraints[_constraint.type] = []
                matched_constraints[_constraint.type].append(
                    _constraint)

        return matched_constraints

    def _collect_matched_resource(self, constraints, resources):
        """ Prepare host resources that match the request vm constraints.

        :param constraints: dict of
            {ResourceConstraintType: list of ResourceConstraint}
        :param resources: dict of host available resources
            {ResourceConstraintType: set of resources}
        :return: dict of matched resources
            {ResourceConstraintType: list of matched resources}
        :raise: NoSuchResourceException
        """

        def _validate_single_constraint(resource_type, values):
            """
            :param resource_type: ResourceConstraintType
            :param values: values are sorted in ResourceConstraint.
            :return: randomly picked matched resource.
            :raise: NoSuchResourceException
            """
            def prepare_exception(resource_type):
                resource_type_str = ResourceConstraintType._VALUES_TO_NAMES[resource_type]
                return NoSuchResourceException(
                    resource_type_str,
                    "Host has no {0} resource.".format(resource_type_str))

            if resource_type not in resources:
                raise prepare_exception(resource_type)

            matched = []
            for value in values:
                if value in resources[resource_type]:
                    matched.append(value)

            if len(matched) != 0:
                self._logger.debug("matched resources: {0}".format(matched))
                shuffle(matched)
                return matched[0]
            raise prepare_exception(resource_type)

        matched_resource = {}

        for resource_type, constraint_list in constraints.iteritems():
            for constraint in constraint_list:
                if resource_type not in matched_resource:
                    matched_resource[resource_type] = []
                matched_resource[resource_type].append(
                    _validate_single_constraint(
                        resource_type,
                        constraint.values))
        return matched_resource

    def _place_vm(self, vm):
        utilization_score, placement_list = self._compute_vm_utilization_score(vm)
        transfer_score = self._transfer_score(vm, placement_list)
        placement_score = AgentPlacementScore(utilization_score, transfer_score)

        resource_placement_list = self._pick_available_resources(vm)
        placement_list.extend(resource_placement_list)

        self._logger.debug("placement score: %s" % placement_score)

        return placement_score, placement_list

    def _place_disk(self, disk):
        utilization_score, placement_list = self._compute_disk_utilization_score(disk)
        transfer_score = self._score(0, False, ResourceType.TRANSFER)
        placement_score = AgentPlacementScore(utilization_score, transfer_score)
        self._logger.debug("placement score: %s" % placement_score)
        return placement_score, placement_list

    def _pick_available_resources(self, vm):
        """ Pick host's resources that match vm's resource constraints.

        :param vm: Vm
        :rtype: Placement List
        :raise: NoSuchResourceException
        """
        placement_list = []

        # supports network and virtual_network constraints.
        mapping = {
            ResourceConstraintType.NETWORK: AgentResourcePlacement.NETWORK,
            ResourceConstraintType.VIRTUAL_NETWORK: AgentResourcePlacement.VIRTUAL_NETWORK}

        if vm.resource_constraints:
            # resource type to extract from VM's resource_constraint for matching
            extract_resources_type = set([ResourceConstraintType.NETWORK])

            # host available resources.
            host_available_resources = {
                ResourceConstraintType.NETWORK:
                set(self._hypervisor.network_manager.get_vm_networks() +
                    self._hypervisor.network_manager.get_dvs())}

            constraints = self._extract_resource_constraints(
                vm.resource_constraints, extract_resources_type)

            matched_resources = self._collect_matched_resource(
                constraints, host_available_resources)

            for resource_type, values in matched_resources.iteritems():
                placement_list.extend([
                    AgentResourcePlacement(
                        mapping[resource_type],
                        vm.id,
                        value)
                    for value in values])

            # resource type to extract from VM's resource_constraint for copying
            virtual_network_resource_placement_list = self._pick_resources(
                vm, ResourceConstraintType.VIRTUAL_NETWORK, AgentResourcePlacement.VIRTUAL_NETWORK)
            placement_list.extend(virtual_network_resource_placement_list)

        return placement_list

    def _pick_resources(self, vm, constraintType, placementType):
        """ Pick vm's specific resource constraints.

        :param vm: Vm
        :param constraintType: ResourceConstraintType
        :param placementType: AgentResourcePlacement
        :rtype: Placement List
        """
        placement_list = []

        constraints = self._extract_resource_constraints(
            vm.resource_constraints, set([constraintType]))

        if len(constraints) > 0:
            constraint_list = constraints[constraintType]
            for constraint in constraint_list:
                placement_list.extend([
                    AgentResourcePlacement(
                        placementType,
                        vm.id,
                        value)
                    for value in constraint.values])

        return placement_list

    @log_duration
    def _transfer_score(self, vm, placement_list=[]):
        """ Transfer score evaluates the cost of copying image lazily from
        shared datastore to datastore.

        If there is an image copy, the transfer score is 1. Otherwise the
        score is 0.
        """

        # Get image disks from Vm's disks list. If there is no
        # image disks, no transfer penalty.
        images = None
        if vm and vm.disks:
            images = [disk for disk in vm.disks
                      if disk.image]
        if not images:
            return self._score(0, False, ResourceType.TRANSFER)

        # Get vm placement from placement list. Image copy happens in the
        # datastore where the Vm is placed.
        vm_placements = [placement for placement in placement_list
                         if placement.type == ResourcePlacementType.VM]
        if not vm_placements:
            return self._score(0, False, ResourceType.TRANSFER)
        vm_placement = vm_placements[0]

        # Try to find an image that is missing in the datastore where Vm is
        # placed. If there is a missing image, then apply the penalty.
        copy_size = 0

        for image in images:
            if not self._image_manager.check_image(image.image.id,
                                                   vm_placement.container_id):
                copy_size += self._image_manager.image_size(image.image.id)

        score = float(copy_size) / self.MAX_IMAGE_COPY_SIZE
        if score > 1:
            score = 1
        return self._score(score, False, ResourceType.TRANSFER)

    """
    Compute utilization score for a vm, returns
    the minimum between the memory and storage score
    """
    def _compute_vm_utilization_score(self, vm):
        memory_score = self._compute_memory_score(vm)
        cpu_score = self._compute_cpu_score(vm)
        storage_score, placement_list = self._compute_storage_score(vm.disks, vm.resource_constraints)

        # If vm is not None, need to put vm placement in placement list
        if vm:
            if placement_list:
                vm_datastore = placement_list[0].container_id
            else:
                vm_datastore = self._optimal_datastore()
            vm_placement = AgentResourcePlacement(
                AgentResourcePlacement.VM, vm.id, vm_datastore
            )
            placement_list = [vm_placement] + placement_list

        self._logger.debug("Scores: memory: %d, cpu: %d, storage: %d"
                           % (memory_score, cpu_score, storage_score))
        return min(memory_score, cpu_score, storage_score), placement_list

    """
    Compute utilization score when creating independent disks (outside a vm creation call)
    """
    def _compute_disk_utilization_score(self, disk):
        return self._compute_storage_score([disk], disk.constraints)

    """
    This method computes the memory score for this host. Used during VM creation.
    """
    @log_duration
    def _compute_memory_score(self, vm):
        total_memory = self._system.total_vmusable_memory_mb()
        total_overcommited_memory = total_memory * self._option.memory_overcommit
        consumed_memory = self._system.host_consumed_memory_mb()

        try:
            used_memory = self._vm_manager.get_used_memory_mb()
        except VmNotFoundException:
            used_memory = 0

        used_memory += self._memory_reserved()
        memory = self._vm_memory_mb(vm)
        self._logger.debug("memory: %d, used_memory: %d, "
                           "total_overcommited_memory: %d" %
                           (memory, used_memory, total_overcommited_memory))
        score = self._score((float(used_memory) + memory) / total_overcommited_memory,
                            True, ResourceType.MEMORY)

        if consumed_memory:
            # For consumed memory, we don't want to enforce MAX_USAGE limits.
            consumed_mem_score = self._score(
                float(consumed_memory) / total_memory,
                False, ResourceType.MEMORY)
            self._logger.debug("Consumed memory: %d, score: %d",
                               consumed_memory, consumed_mem_score)
            if (consumed_mem_score < self.MEM_UTIL_SCORE_THRESHOLD and
                    consumed_mem_score < score):
                # This host seems to be busy, hence bias the overall score by
                # considering the consumed score as well. This essentially
                # means that at high utilizations, consumption becomes equally
                # important as over commit. At the same time, we check if the
                # consumed score is lower than the overcommit score. We do not
                # want to increase the score if the host is severely
                # overcommitted. For now we take a simple average.
                score = (score + consumed_mem_score) / 2

        self._logger.debug("memory score: %d" % score)
        return score

    """
    This method computes the CPU score for this host. The score is calculated by considering
    total available number of pCpus*overcommit and the total number of vCpus across all VMs on the
    host and finding their ratio.
    """
    @log_duration
    def _compute_cpu_score(self, vm):
        total_cpu_count = self._system.num_physical_cpus()
        total_cpu_count *= self._option.cpu_overcommit

        if total_cpu_count is 0:
            return 0

        try:
            configured_cpu = self._vm_manager.get_configured_cpu_count()
        except VmNotFoundException:
            configured_cpu = 0

        configured_cpu += self._cpu_reserved()
        cpu = self._vm_cpu_count(vm)
        score = self._score((float(configured_cpu) + cpu) / total_cpu_count, True, ResourceType.CPU)
        return score

    """
    The method computes the storage score for this host. The score is based on the ratio of two sums:
    used and total. The sums are computed over all the datastores available on the host.
    If an image id is requested the code checks if there is a copy of that image on at least one of the datastores
    connected to the host. If there is none, the score is drastically reduced.
    """

    def _compute_storage_score(self, disks, constraints):
        best_effort = False

        if not disks:
            return self.MAX_SCORE, []

        selector = DatastoreSelector.init_datastore_selector(
            self._datastore_manager, self._placeable_datastores())

        disks_placement = DisksPlacement(disks, selector)
        # Place constraint disks first
        place_result = self._constrainted_placement.place(disks_placement, constraints)
        if place_result.result == PlaceResultCode.NO_SUCH_RESOURCE:
            raise NoSuchResourceException(ResourceType.DISK, "Disk placement failed.")
        elif place_result.result == PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY:
            raise NotEnoughDatastoreCapacityException()

        # Try optimal for unconstrainted disks first
        place_result = self._optimal_placement.place(
            place_result.disks_placement, constraints)

        if place_result.result == PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY:
            # If it doesn't work then try best effort
            place_result = self._best_effort_placement.place(place_result.disks_placement, constraints)
            best_effort = True

        # Still NOT_ENOUGH_SYSTEM_RESOURCE then have to give up.
        if place_result.result == PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY:
            raise NotEnoughDatastoreCapacityException()

        # Congrat! Successful placement.

        # Count reserved storage
        free = place_result.disks_placement.selector.total_free_space()
        total = place_result.disks_placement.selector.total_space()
        ratio = float(total-free+self._storage_reserved()) / total

        # If we find an optimal data store (that contains the image needed)
        # and the the data stores will not cross the free space threshold,
        # on admitting the VM, return a high score, so data store score will
        # not be used, if at all.
        if (ratio < self.FREESPACE_THRESHOLD and
                not best_effort):
            return self.MAX_SCORE, place_result.disks_placement.placement_list

        # Compute score
        score = self._score(ratio, True, ResourceType.DATASTORE)

        # If an optimal placement could not be achieved reduce the score for
        # this host
        if best_effort:
            score /= self.NOT_OPTIMAL_DIVIDE_FACTOR

        return score, place_result.disks_placement.placement_list

    def _score(self, inverse_ratio, utilization, resource_type):
        if utilization and inverse_ratio > self.MAX_USAGE:
            self._logger.warning(
                "Resource constraint exception, %s: %f > %f" %
                (resource_type, inverse_ratio, self.MAX_USAGE))
            if resource_type == ResourceType.MEMORY:
                raise NotEnoughMemoryResourceException()
            if resource_type == ResourceType.CPU:
                raise NotEnoughCpuResourceException()
            if resource_type == ResourceType.DATASTORE:
                raise NotEnoughDatastoreCapacityException()

        return self.MAX_SCORE - int(round(inverse_ratio * 100))

    def _memory_reserved(self):
        return sum(self._vm_memory_mb(val)
                   for val in self._reserved_vms.values())

    def _cpu_reserved(self):
        return sum(self._vm_cpu_count(val)
                   for val in self._reserved_vms.values())

    def _storage_reserved(self):
        score = sum(self._diskutil.disks_capacity_gb(disks)
                    for disks in self._reserved_disks.values())
        score += sum(self._diskutil.disks_capacity_gb(vm.disks)
                     for vm in self._reserved_vms.values()
                     if vm.disks)
        self._logger.debug("storage reserved score: %d" % score)
        return score

    def _placeable_datastores(self):
        datastores = self._datastore_manager.vm_datastores()
        for image_ds in self._option.image_datastores:
            if image_ds["used_for_vms"]:
                try:
                    dsid = self._datastore_manager.normalize(image_ds["name"])
                    datastores.append(dsid)
                except DatastoreNotFoundException:
                    self._logger.debug("skip unavailable datastore: %s" %
                                       image_ds["name"])

        self._logger.debug("Placeable datastores: %s, image datastores: %s" %
                           (datastores, self._option.image_datastores))
        return datastores

    def _optimal_datastore(self):
        free = 0
        optimal = None
        for datastore_id in self._placeable_datastores():
            datastore_info = self._datastore_manager.datastore_info(datastore_id)
            if datastore_info.total - datastore_info.used > free:
                optimal = datastore_id
        return optimal

    @staticmethod
    def _vm_memory_mb(vm):
        return int(vm.flavor.cost["vm.memory"].convert(Unit.MB))

    @staticmethod
    def _vm_cpu_count(vm):
        return int(vm.flavor.cost["vm.cpu"].convert(Unit.COUNT))

    @staticmethod
    def _consume(resources, reservation_id):
        if reservation_id not in resources:
            raise InvalidReservationException()
        resource = resources[reservation_id]
        return resource

    @staticmethod
    def _remove(resources, reservation_id):
        if reservation_id in resources:
            del resources[reservation_id]
