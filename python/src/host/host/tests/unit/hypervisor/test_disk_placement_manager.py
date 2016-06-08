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
import os
import tempfile
import unittest
import uuid

from hamcrest import *  # noqa
from mock import MagicMock
from nose_parameterized import parameterized

from common.kind import Flavor
from common.kind import QuotaLineItem
from common.kind import Unit
from common.state import State
from gen.resource.ttypes import ResourceConstraintType
from gen.resource.ttypes import ResourceConstraint
from host.hypervisor.disk_placement_manager import BestEffortPlaceEngine
from host.hypervisor.disk_placement_manager import DatastoreSelector
from host.hypervisor.disk_placement_manager import DisksPlacement
from host.hypervisor.disk_placement_manager import DiskUtil
from host.hypervisor.disk_placement_manager import ConstraintDiskPlaceEngine
from host.hypervisor.disk_placement_manager import OptimalPlaceEngine
from host.hypervisor.disk_placement_manager import PlaceResultCode
from host.hypervisor.placement_manager import PlacementOption
from host.hypervisor.resources import AgentResourcePlacement
from host.hypervisor.resources import Disk
from host.hypervisor.resources import DiskImage
from host.hypervisor.system import DatastoreInfo


DISK_FLAVOR = Flavor("default", [])
VM_FLAVOR = Flavor("default", [
    QuotaLineItem("vm.memory", "2", Unit.GB),
    QuotaLineItem("vm.cpu", "1", Unit.COUNT),
])


class TestDiskPlacementManager(unittest.TestCase):

    def setUp(self):
        self.file_location = tempfile.mktemp()
        self.state = State(self.file_location)

    def tearDown(self):
        try:
            os.remove(self.file_location)
        except:
            pass

    def create_datastore_manager(self, ds_map, image_ds):
        ds_mgr = MagicMock()
        ds_mgr.get_datastore_ids.return_value = ds_map.keys()
        ds_mgr.vm_datastores.return_value = [ds for ds in ds_map.keys() if ds != image_ds]
        ds_mgr.normalize.side_effect = lambda x: x

        def fake_datastore_info(datastore_id):
            if datastore_id not in ds_map.keys():
                raise Exception
            return ds_map[datastore_id]
        ds_mgr.datastore_info.side_effect = fake_datastore_info

        return ds_mgr

    def create_disks(self, disk_sizes):
        return [Disk(capacity_gb=disk_size) for disk_size in disk_sizes]

    @parameterized.expand([
        (PlaceResultCode.OK, 0.0, [], False),
        (PlaceResultCode.OK, 0.4, [2*1024], False),
        (PlaceResultCode.OK, 0.6, [3*1024], False),
        (PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY, 0.0, [4*1024], False),
        (PlaceResultCode.OK, 0.0, [], True),
        (PlaceResultCode.OK, 0.5, [3*1024], True),
        (PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY, 0.0, [4*1024], True),
    ])
    def test_optimal_place_engine(self, result, ratio, disk_sizes,
                                  use_image_ds):
        # Create optimal place engine
        image_datastore = "datastore_id_1"
        image_datastores = [{"name": image_datastore, "used_for_vms": use_image_ds}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {"datastore_id_1": DatastoreInfo(1 * 1024, 0),
                  "datastore_id_2": DatastoreInfo(2 * 1024, 0),
                  "datastore_id_3": DatastoreInfo(3 * 1024, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastore)

        engine = OptimalPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)
        disks_placement = DisksPlacement(self.create_disks(disk_sizes), selector)

        # Verify place result
        place_result = engine.place(disks_placement, [])
        assert_that(place_result.result, equal_to(result))
        assert_that(place_result.disks_placement.selector.ratio(),
                    equal_to(ratio))

        # Verify placements
        if disk_sizes and place_result.result == PlaceResultCode.OK:
            placement_list = place_result.disks_placement.placement_list
            assert_that(placement_list, has_length(1))
            assert_that(placement_list[0].type,
                        equal_to(AgentResourcePlacement.DISK))
            assert_that(placement_list[0].container_id, equal_to("datastore_id_3"))

    @parameterized.expand([
        (PlaceResultCode.OK, 0.0, [], [], False),
        (PlaceResultCode.OK, 0.4, [20], ["ds3"], False),
        (PlaceResultCode.OK, 0.4, [11, 9], ["ds3", "ds3"], False),
        (PlaceResultCode.OK, 0.4, [5, 6, 9], ["ds3", "ds3", "ds3"], False),
        (PlaceResultCode.OK, 0.7, [20, 15], ["ds3", "ds2"], False),
        (PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY,
            0.0, [21, 22], [], False),
        (PlaceResultCode.OK, 0.0, [], [], True),
        (PlaceResultCode.OK, 0.5, [30], ["ds3"], True),
        (PlaceResultCode.OK, 0.75, [21, 14, 10], ["ds3", "ds2", "ds1"], True),
        (PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY,
            0.0, [21, 22], [], True)])
    def test_best_effort_place_engine(self, result, ratio, disk_sizes, places, use_image_ds):
        # Create best effort place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": use_image_ds}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(10, 0),
                  "ds2": DatastoreInfo(20, 0),
                  "ds3": DatastoreInfo(30, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastore)
        engine = BestEffortPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)
        disks_placement = DisksPlacement(self.create_disks(disk_sizes), selector)

        # Try place
        place_result = engine.place(disks_placement, [])

        # Verify place result
        assert_that(place_result.result, equal_to(result))

        # Verify placements
        if disk_sizes and place_result.result == PlaceResultCode.OK:
            assert_that(place_result.disks_placement.selector.ratio(), equal_to(ratio))
            for i, place in enumerate(places):
                placement_list = place_result.disks_placement.placement_list
                assert_that(placement_list[0].type, equal_to(AgentResourcePlacement.DISK))
                assert_that(placement_list[i].container_id, equal_to(place))

    @parameterized.expand([
        ("ds1", ),
        ("ds2",),
    ])
    def test_constraint_place_engine(self, ds_constraint):
        # Create constraint place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": True}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(10, 0),
                  "ds2": DatastoreInfo(20, 0),
                  "ds3": DatastoreInfo(30, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastore)
        engine = ConstraintDiskPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)

        # Try place
        constraint = ResourceConstraint(ResourceConstraintType.DATASTORE, [ds_constraint])
        disks = [
            Disk(disk_id="disk1", capacity_gb=1),
            Disk(disk_id="disk2", capacity_gb=1),
            Disk(capacity_gb=1),
        ]
        disks_placement = DisksPlacement(disks, selector)
        place_result = engine.place(disks_placement, [constraint])

        # Verify place result
        assert_that(place_result.result, equal_to(PlaceResultCode.OK))

        # Verify placement list and unplaced list
        disks_placement = place_result.disks_placement
        assert_that(disks_placement.placement_list, has_length(3))
        assert_that(disks_placement.disks, has_length(0))
        assert_that([d.resource_id for d in disks_placement.placement_list if d.resource_id],
                    contains_inanyorder("disk1", "disk2"))
        for placement in disks_placement.placement_list:
            assert_that(placement.type, equal_to(AgentResourcePlacement.DISK))
            assert_that(placement.container_id, equal_to(constraint.values[0]))

    def test_constraint_place_engine_no_constraints(self):
        """ constraint place engine should not handle placement with no constraints
        """
        # Create constraint place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": True}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(10, 0),
                  "ds2": DatastoreInfo(20, 0),
                  "ds3": DatastoreInfo(30, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastore)
        engine = ConstraintDiskPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)

        # Try place
        disks = [
            Disk(disk_id="disk1", capacity_gb=1),
            Disk(disk_id="disk2", capacity_gb=1),
            Disk(capacity_gb=1),
        ]
        disks_placement = DisksPlacement(disks, selector)
        place_result = engine.place(disks_placement, [])

        # Verify place result
        assert_that(place_result.result, equal_to(PlaceResultCode.OK))

        # Verify unplaced list
        disks_placement = place_result.disks_placement
        assert_that(disks_placement.placement_list, has_length(0))
        assert_that(disks_placement.disks, has_length(3))

    def test_constraint_place_engine_cannot_fit(self):
        # Create constraint place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": True}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(5, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastore)
        engine = ConstraintDiskPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)

        # Try place
        constraint = ResourceConstraint(ResourceConstraintType.DATASTORE, ["ds1"])
        disk = Disk(disk_id="ds1", capacity_gb=6)
        place_result = engine.place(DisksPlacement([disk], selector), [constraint])

        # Verify place result
        assert_that(place_result.result,
                    equal_to(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY))

    def test_constraint_place_engine_constraint_violated(self):
        # Create constraint place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": True}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(5, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastores)
        engine = ConstraintDiskPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)

        # Try place
        constraint = ResourceConstraint(ResourceConstraintType.DATASTORE, ["ds2"])
        disk = Disk(disk_id="ds1", capacity_gb=1)
        place_result = engine.place(DisksPlacement([disk], selector), [constraint])

        # Verify place result
        assert_that(place_result.result, equal_to(PlaceResultCode.NO_SUCH_RESOURCE))

    def test_constraint_place_engine_conflicting_constraints(self):
        """ constraint place engine should fail if multiple constraints conflict
        """
        # Create constraint place engine
        image_datastore = "ds1"
        image_datastores = [{"name": image_datastore, "used_for_vms": True}]
        option = PlacementOption(1, 1, image_datastores)
        ds_map = {image_datastore: DatastoreInfo(10, 0),
                  "ds2": DatastoreInfo(20, 0)}
        ds_mgr = self.create_datastore_manager(ds_map, image_datastores)
        engine = ConstraintDiskPlaceEngine(ds_mgr, option)
        ds = engine.placeable_datastores()
        selector = DatastoreSelector.init_datastore_selector(ds_mgr, ds)

        # Try place
        constraints = [ResourceConstraint(ResourceConstraintType.DATASTORE, ["ds1"]),
                       ResourceConstraint(ResourceConstraintType.DATASTORE, ["ds2"])]
        disk = Disk(disk_id="ds1", capacity_gb=1)
        place_result = engine.place(DisksPlacement([disk], selector), constraints)

        # Verify place result
        assert_that(place_result.result, equal_to(PlaceResultCode.NO_SUCH_RESOURCE))

    def test_disks_capacity_gb(self):
        """Linked-cloned image disks are excluded from capacity calculation."""
        image1 = DiskImage("disk_image1", DiskImage.COPY_ON_WRITE)
        image2 = DiskImage("disk_image2", DiskImage.FULL_COPY)
        disk1 = Disk(new_id(), DISK_FLAVOR, False, True, 1, image1)
        disk2 = Disk(new_id(), DISK_FLAVOR, False, True, 2, image2)
        disk3 = Disk(new_id(), DISK_FLAVOR, False, True, 3)
        disk4 = Disk(new_id(), DISK_FLAVOR, False, True, 4)
        disk5 = Disk(new_id(), DISK_FLAVOR, False, True, 5)
        disks = [disk1, disk2, disk3, disk4, disk5]
        assert_that(DiskUtil().disks_capacity_gb(disks), equal_to(14))


def new_id():
    return str(uuid.uuid4())
