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
from enum import enum, Enum
import copy
import logging
import random

from common.log import log_duration
from gen.resource.ttypes import CloneType
from gen.resource.ttypes import ResourceConstraintType
from host.hypervisor.exceptions import DatastoreNotFoundException
from host.hypervisor.resources import AgentResourcePlacement


@enum.unique
class PlaceResultCode(Enum):
    OK = 0
    NO_SUCH_RESOURCE = 1
    NOT_ENOUGH_CPU_RESOURCE = 2
    NOT_ENOUGH_MEMORY_RESOURCE = 3
    NOT_ENOUGH_DATASTORE_CAPACITY = 4
    RESOURCE_CONSTRAINT = 5


class NotEnoughSpaceException(Exception):
    pass


class ConflictedConstraintException(Exception):
    pass


class NoDatastoresException(Exception):
    pass


class DiskPlaceResult(object):
    """DiskPlaceResult is the result after disk place engine tries to place
    a disk list.
    """

    def __init__(self, result, disks_placement=None):
        self.result = result
        self.disks_placement = disks_placement


class DisksPlacement(object):
    """DisksPlacement contains all the information about a full disks
    placement. It has a list of disks, that need to be placed, a placement
    list, and a datastore selector.
    """

    def __init__(self, disks, selector):
        self.disks = disks
        self.selector = selector
        self.placement_list = []


class DatastoreSelector(object):
    """DatastoreSelector tracks free space for all available datastores. It
    tries to offer the best suitable datastores, reserve disk space when
    placement happens, and calculating free space ratio.
    """

    def __init__(self):
        self.datastore_free = {}
        self.datastore_total = {}

    @staticmethod
    def init_datastore_selector(datastore_manager, datastores):
        if len(datastores) == 0:
            raise NoDatastoresException()

        selector = DatastoreSelector()
        for datastore_id in datastores:
            info = datastore_manager.datastore_info(datastore_id)
            selector.add_datastore(datastore_id, info.total - info.used,
                                   info.total)
        return selector

    def get_datastore(self):
        """Get datastore with max free space"""

        return max(self.datastore_free,
                   key=lambda x: self.datastore_free[x])

    def consume_datastore_space(self, datastore_id, consumed_space):
        assert datastore_id in self.datastore_free

        if self.datastore_free[datastore_id] < consumed_space:
            raise NotEnoughSpaceException()

        self.datastore_free[datastore_id] -= consumed_space

    def add_datastore(self, datastore_id, free, total):
        self.datastore_free[datastore_id] = free
        self.datastore_total[datastore_id] = total

    def free_space(self, datastore_id):
        assert datastore_id in self.datastore_free
        return self.datastore_free[datastore_id]

    def total_free_space(self):
        if not self.datastore_free:
            return 0
        return sum(self.datastore_free.values())

    def total_space(self):
        if not self.datastore_total:
            return 0
        return sum(self.datastore_total.values())

    def ratio(self):
        if not self.datastore_free or not self.datastore_total:
            return 0

        free = sum(self.datastore_free.values())
        total = sum(self.datastore_total.values())
        return float(total - free) / total

    def __repr__(self):
        return "free: %s, total: %s" % (self.datastore_free,
                                        self.datastore_total)


class DiskPlaceEngine(object):
    """PlaceEngine is the abstract class for place algorithm for disks.
    """

    @abc.abstractmethod
    def place(self, disks_placement, constraints):
        """
        :rtype PlaceResult: Result of place
        """
        pass


class BaseDiskPlacementEngine(DiskPlaceEngine):
    """BasePlacementEngine provides base methods that is useful for place
    engines.
    """

    def __init__(self, datastore_manager, option):
        self._logger = logging.getLogger(__name__)
        self.datastore_manager = datastore_manager
        self.disk_util = DiskUtil()
        self._option = option

    def placeable_datastores(self):
        datastores = self.datastore_manager.vm_datastores()
        for image_ds in self._option.image_datastores:
            if image_ds["used_for_vms"]:
                try:
                    dsid = self.datastore_manager.normalize(image_ds["name"])
                    datastores.append(dsid)
                except DatastoreNotFoundException:
                    self._logger.debug("skip unavailable datastore: %s" %
                                       image_ds["name"])

        self._logger.debug("Placeable datastores: %s, image datastores: %s" %
                           (datastores, self._option.image_datastores))
        return datastores

    def get_datastore_constraint(self, constraints):
        """Place a disk in datastore based on the datastore constraints
        specified in disk.
        :return: datastore
        :raise ConflictedConstraintException when constraints conflicts
        """
        # Get all datastores that meet ResourceConstraintType.DATASTORE

        # Temporary change to allow datastore names to be passed in.
        # The deployer currently doesn't know about datastore ids and
        # passes in datastore names as part of the mgmt VM installs.
        # Workaround this by converting datastore names to ids if
        # applicable.
        datastores = set([self.datastore_manager.normalize(c.values[0])
                          for c in constraints
                          if c.type is ResourceConstraintType.DATASTORE])

        # Get all datastores that meet ResourceConstraintType.DATASTORE_TAG
        datastore_tags = [c.values[0] for c in constraints
                          if c.type is ResourceConstraintType.DATASTORE_TAG]
        self._logger.info("Found tags %s" % datastore_tags)

        datastores_with_tags = self._find_datastores_with_tags(datastore_tags)
        self._logger.info("Found datastores that satisfy tags: %s" % datastores_with_tags)

        if len(datastores) > 1:
            raise ConflictedConstraintException(
                "Conflicted DATASTORE ResourceConstraint for %s" % datastores)

        if datastores:
            datastore = datastores.pop()
            if datastore_tags:
                if datastore in datastores_with_tags:
                    return datastore
                else:
                    raise ConflictedConstraintException(
                        "Conflicted DATASTORE and DATASTORETAG constraints")
            else:
                return datastore
        else:
            if datastore_tags:
                # Randomly pick among the datastores with tags
                return random.choice(list(datastores_with_tags))
            else:
                return None

    def _find_datastores_with_tags(self, tags):
        placeable_datastores = self.placeable_datastores()
        datastores = self.datastore_manager.get_datastores()
        return set([datastore.id for datastore in datastores
                    if datastore.id in placeable_datastores and
                    set(datastore.tags).issuperset(tags)])


class OptimalPlaceEngine(BaseDiskPlacementEngine):
    """Optimal place engine tries to put all disks into one datastore. This
    engine doesn't look into constraints so far.
    """
    def __init__(self, datastore_manager, option):
        super(OptimalPlaceEngine, self).__init__(datastore_manager, option)

    @log_duration
    def place(self, disks_placement, constraints):
        disks_total_size = self.disk_util.disks_capacity_gb(
            disks_placement.disks)

        # Get the optimal datastore
        optimal_datastore = disks_placement.selector.get_datastore()

        # Fail if the optimal datastore cannot fit all the remaining disks
        if disks_total_size > \
                disks_placement.selector.free_space(optimal_datastore):
            return DiskPlaceResult(
                result=PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY,
                disks_placement=disks_placement)

        # Find the optimal placement. Prepare the return value
        disks_placement.selector.consume_datastore_space(optimal_datastore,
                                                         disks_total_size)
        for disk in disks_placement.disks:
            disks_placement.placement_list.append(
                AgentResourcePlacement(AgentResourcePlacement.DISK,
                                       disk.id,
                                       optimal_datastore)
            )
        return DiskPlaceResult(result=PlaceResultCode.OK,
                               disks_placement=disks_placement)


class BestEffortPlaceEngine(BaseDiskPlacementEngine):
    """Try to place as many disks in the biggest datastore until the diggest
    datastore cannot fit disks, then place in second biggest one. Repeat this
    process until all the disks are placed.
    """
    def __init__(self, datastore_manager, option):
        super(BestEffortPlaceEngine, self).__init__(datastore_manager, option)

    def place(self, disks_placement, constraints):
        disks_placement = copy.deepcopy(disks_placement)
        selector = disks_placement.selector

        optimal_datastore = selector.get_datastore()

        disks = sorted(disks_placement.disks,
                       key=lambda d: self.disk_util.disk_capacity(d),
                       reverse=True)

        for disk in disks:
            disk_capacity_gb = self.disk_util.disk_capacity(disk)
            if selector.free_space(optimal_datastore) < disk_capacity_gb:
                optimal_datastore = selector.get_datastore()
                if selector.free_space(optimal_datastore) < disk_capacity_gb:
                    return DiskPlaceResult(
                        result=PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY,
                        disks_placement=disks_placement)

            disks_placement.selector.consume_datastore_space(optimal_datastore,
                                                             disk_capacity_gb)
            disks_placement.placement_list.append(
                AgentResourcePlacement(AgentResourcePlacement.DISK,
                                       disk.id, optimal_datastore))

        return DiskPlaceResult(result=PlaceResultCode.OK,
                               disks_placement=disks_placement)


class ConstraintDiskPlaceEngine(BaseDiskPlacementEngine):
    """Place disks with constraints
    """
    def __init__(self, datastore_manager, option):
        super(ConstraintDiskPlaceEngine, self).__init__(datastore_manager, option)

    def place(self, disks_placement, constraints):
        disks_placement = copy.deepcopy(disks_placement)

        unplaced_disks = []
        for disk in disks_placement.disks:
            datastore_id = None
            try:
                if constraints:
                    datastore_id = self.get_datastore_constraint(constraints)
            except DatastoreNotFoundException:
                self._logger.warning("Data store constraint failed: %s" % disk)
                return DiskPlaceResult(PlaceResultCode.NO_SUCH_RESOURCE)
            except ConflictedConstraintException:
                self._logger.info("Conflicted constraints", exc_info=True)
                return DiskPlaceResult(PlaceResultCode.NO_SUCH_RESOURCE)

            if not datastore_id:
                unplaced_disks.append(disk)
                continue

            # if the datastore_id is not visible by this host throw an exception
            if datastore_id not in self.placeable_datastores():
                self._logger.warning("Data store constraint failed: %s" % datastore_id)
                return DiskPlaceResult(PlaceResultCode.NO_SUCH_RESOURCE)

            disk_capacity_gb = self.disk_util.disk_capacity(disk)
            try:
                disks_placement.selector.consume_datastore_space(datastore_id, disk_capacity_gb)
            except NotEnoughSpaceException:
                return DiskPlaceResult(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY)

            # Append in placement list
            disks_placement.placement_list.append(
                AgentResourcePlacement(AgentResourcePlacement.DISK, disk.id, datastore_id))

        disks_placement.disks = unplaced_disks
        return DiskPlaceResult(result=PlaceResultCode.OK, disks_placement=disks_placement)


class DiskUtil(object):

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def disks_capacity_gb(self, disks):
        """
        Returns the sum of capacities of disks in GB, not including linked-
        cloned image disks. We exclude linked-cloned image disks because they
        take very small amount of disk space.
        """
        if not disks:
            return 0
        capacity_gb = 0
        for disk in disks:
            capacity_gb += self.disk_capacity(disk)
        return capacity_gb

    def disk_capacity(self, disk):
        """Return the disk capacity in GB, ignoring the disk that is linked
        cloned from an image. We exclude it because it takes small amount of
        disk space
        :param disk: host.hypervisor.resources.Disk
        :return: the disk size in gb
        """
        if disk.image and disk.image.clone_type == CloneType.COPY_ON_WRITE:
            self._logger.info("Ignore capacity for image disk: %s" % disk)
            return 0
        else:
            return disk.capacity_gb
