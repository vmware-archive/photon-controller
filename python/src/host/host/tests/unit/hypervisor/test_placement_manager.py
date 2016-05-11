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
import unittest
import uuid

from hamcrest import *  # noqa
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from mock import MagicMock
from nose.tools import raises
from nose_parameterized import parameterized

from common.kind import Flavor
from common.kind import QuotaLineItem
from common.kind import Unit
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import ResourceConstraintType
from gen.resource.ttypes import ResourceConstraint
from host.hypervisor.placement_manager import AgentPlacementScore
from host.hypervisor.placement_manager import PlacementManager
from host.hypervisor.placement_manager import PlacementOption
from host.hypervisor.placement_manager import NoSuchResourceException
from host.hypervisor.placement_manager import NotEnoughCpuResourceException
from host.hypervisor.placement_manager import \
    NotEnoughDatastoreCapacityException
from host.hypervisor.placement_manager import NotEnoughMemoryResourceException
from host.hypervisor.resources import Disk
from host.hypervisor.resources import DiskImage
from host.hypervisor.resources import State
from host.hypervisor.resources import Vm
from host.hypervisor.system import DatastoreInfo


DISK_FLAVOR = Flavor("default", [])
VM_FLAVOR = Flavor("default", [
    QuotaLineItem("vm.memory", "2", Unit.GB),
    QuotaLineItem("vm.cpu", "1", Unit.COUNT),
])


class TestPlacementManager(unittest.TestCase):

    @parameterized.expand([
        # d1, d2,  oc,  (score)
        (512, 512, 1.0, (87, 100)),  # disk score dominates 1-(0.5k+0.5k)/8k
        (1,   1,   1.0, (97, 100)),  # memory score dominates 1-2k/64k
        (512, 512, 2.0, (87, 100)),  # disk score dominates 1-(0.5k+0.5k)/8k
        (1,   1,   2.0, (98, 100)),  # memory score dominates 1-2k/(64k*2)
    ])
    def test_place_new(self, disk_capacity_1, disk_capacity_2, overcommit,
                       expected):
        created_disk1 = Disk(new_id(), DISK_FLAVOR, True, True,
                             disk_capacity_1)
        created_disk2 = Disk(new_id(), DISK_FLAVOR, True, True,
                             disk_capacity_2)
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None)
        vm.disks = [created_disk1, created_disk2]

        manager = PMBuilder(mem_overcommit=overcommit).build()
        # Disable freespace based admission control.
        manager.FREESPACE_THRESHOLD = 0
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(*expected)))

    @parameterized.expand([
        (1.0,  100.0, (97, 100)),  # memory score dominates
        (10.0, 4.0,   (50, 100)),  # CPU score dominates
    ])
    def test_place_new_with_cpu(self, mem_overcommit,
                                cpu_overcommit, expected):
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None)
        manager = PMBuilder(mem_overcommit=mem_overcommit,
                            cpu_overcommit=cpu_overcommit).build()
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(*expected)))

    @parameterized.expand([
        (60*1024, 1.0, (51, 100)),  # consumed memory score comes into play
        (10*1024, 1.0, (97, 100)),  # consumed memory score is ignored
    ])
    def test_place_new_with_mem_consumed(self, mem_consumed,
                                         mem_overcommit, expected):
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None)
        manager = PMBuilder(mem_overcommit=mem_overcommit).build()
        manager._system.host_consumed_memory_mb.return_value = mem_consumed
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(*expected)))

    @parameterized.expand([
        (1.0, 2,  (97, 100)),    # memory score dominates 1-(2k)/64k
        (1.5, 2,  (98, 100)),    # memory score dominates 1-(2k)/(64k*1.5)
        (2.0, 2,  (98, 100)),    # memory score dominates 1-(2k)/(64k*2)
        (2.0, 64, (50, 100)),    # memory score dominates 1-(64k)/(64k*2)
    ])
    def test_empty_disk_place(self, overcommit, vm_memory, expected):
        vm = Vm(new_id(), self._vm_flavor_gb(vm_memory), State.STOPPED, None)
        manager = PMBuilder(mem_overcommit=overcommit).build()
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(*expected)))

    @raises(NotEnoughCpuResourceException)
    def test_place_cpu_constraint(self):
        disk1 = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        disk2 = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        vm = Vm(new_id(),
                self._vm_flavor_gb(memory_gb=1, cpu=100),
                State.STARTED, None)
        vm.disks = [disk1, disk2]
        manager = PMBuilder(cpu_overcommit=1).build()
        manager.place(vm, None)

    @raises(NotEnoughMemoryResourceException)
    def test_place_memory_constraint(self):
        # Try to deploy a VM with 64GB memory on a system with 64GB memory
        # will fail because the memory score is 0.
        # Actually if memory score is less than 5 (1 - max_usage) will be
        # rejected. In this case, 60GB is also rejected.
        disk1 = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        disk2 = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        vm = Vm(new_id(), self._vm_flavor_gb(64), State.STARTED, None)
        vm.disks = [disk1, disk2]
        manager = PMBuilder().build()
        manager.place(vm, None)

    @raises(NotEnoughDatastoreCapacityException)
    def test_place_storage_constraint(self):
        disk = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        vm = Vm(new_id(), VM_FLAVOR, State.STARTED, None)
        vm.disks = [disk]
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 7 * 1024),
                                     set([]))}
        manager = PMBuilder(ds_map=ds_map).build()
        manager.place(vm, None)

    @parameterized.expand([
        (512, 512, 1.0, (75, 100)),  # disk score dominates 1-(0.5k+0.5k)*2/8k
        (1,   1,   1.0, (94, 100)),  # memory score dominates 1-(2k+2k)/64k
        (1,   1,   2.0, (97, 100))   # memory score dominates 1-(2k+2k)/64k/2
    ])
    def test_place_new_with_reservations(self, disk1_capacity, disk2_capacity,
                                         overcommit, expected):
        disk1 = Disk(new_id(), DISK_FLAVOR, True, True, disk1_capacity)
        disk2 = Disk(new_id(), DISK_FLAVOR, True, True, disk2_capacity)
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None)
        vm.disks = [disk1, disk2]

        manager = PMBuilder(mem_overcommit=overcommit).build()
        # Disable freespace based admission control.
        manager.FREESPACE_THRESHOLD = 0
        manager.reserve(vm, None)
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(*expected)))

    def test_reserved_resources(self):
        manager = PMBuilder().build()

        disk = Disk(new_id(), DISK_FLAVOR, True, True, 1024)
        vm = Vm(new_id(), VM_FLAVOR, State.STARTED)
        reservations = []

        for _ in range(3):
            reservations.append(manager.reserve(vm, [disk]))
            assert_that(manager._storage_reserved(),
                        equal_to(disk.capacity_gb *
                                 len(reservations)))
            assert_that(manager._memory_reserved(),
                        equal_to(manager._vm_memory_mb(vm) *
                                 len(reservations)))

        for rid in reservations:
            manager.remove_disk_reservation(rid)
            manager.remove_vm_reservation(rid)

        assert_that(manager._storage_reserved(), equal_to(0))
        assert_that(manager._memory_reserved(), equal_to(0))

        # test that reserve sums as expected if vm or disks is None
        rid = manager.reserve(vm, None)
        assert_that(manager._storage_reserved(), equal_to(0))
        assert_that(manager._memory_reserved(),
                    equal_to(manager._vm_memory_mb(vm)))
        manager.remove_vm_reservation(rid)

        rid = manager.reserve(None, [disk])
        assert_that(manager._storage_reserved(),
                    equal_to(disk.capacity_gb))
        assert_that(manager._memory_reserved(),
                    equal_to(0))
        manager.remove_disk_reservation(rid)

    def test_place_vm_no_disks(self):
        manager = PMBuilder().build()
        vm = Vm(new_id(), VM_FLAVOR,
                State.STOPPED, None, None, None)
        score, placement_list = manager.place(vm, None)
        assert_that(score, is_(AgentPlacementScore(97, 100)))
        assert_that(len(placement_list) is 1)
        assert_that(placement_list[0].
                    resource_id, equal_to(vm.id))
        assert_that(placement_list[0].
                    container_id, equal_to('datastore_id_1'))

    def test_place_vm_with_network_constraint(self):
        host_vm_network_list = ["net_1", "net_2", "net_3", "net_4"]
        manager = PMBuilder(vm_networks=host_vm_network_list).build()

        # place with resource constraints that host can match, should succeed.
        resource_constraints = [
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_2", "net_1"]),
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_4"]),
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_3", "net_5"])]
        vm = Vm(new_id(), VM_FLAVOR,
                State.STOPPED, None, None, None, None, resource_constraints)

        score, placement_list = manager.place(vm, None)
        assert_that(len(placement_list) is 4)
        assert_that(placement_list[1].
                    resource_id, equal_to(vm.id))

        # randomly pick matched resources from host.
        assert_that(placement_list[1].
                    container_id == 'net_1' or
                    placement_list[1].
                    container_id == 'net_2', True)
        assert_that(placement_list[2].container_id == 'net_4', True)
        assert_that(placement_list[3].container_id == 'net_3', True)

        # place with resource constraints AND logic that host can not match,
        # should fail.
        resource_constraints = [
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_2", "net_1"]),
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_5"])]
        vm = Vm(new_id(), VM_FLAVOR,
                State.STOPPED, None, None, None, None, resource_constraints)

        self.assertRaises(NoSuchResourceException, manager.place, vm, None)

        # place with resource constraints OR logic that host can not match,
        # should fail.
        resource_constraints = [
            ResourceConstraint(ResourceConstraintType.NETWORK,
                               ["net_6", "net_7"])]
        vm = Vm(new_id(), VM_FLAVOR,
                State.STOPPED, None, None, None, None, resource_constraints)

        self.assertRaises(NoSuchResourceException, manager.place, vm, None)

    @parameterized.expand([
        [0,         100],
        [102 << 20, 90],
        [204 << 20, 80],
        [512 << 20, 50],
        [1 << 30,   0],
        [2 << 30,   0],
    ])
    def test_place_vm_non_existing_image(self, image_size, expected):
        manager = PMBuilder(ds_with_image=[], image_size=image_size).build()
        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        disk = Disk(new_id(), DISK_FLAVOR, False, True, 1024, image)
        vm = Vm(new_id(), VM_FLAVOR,
                State.STOPPED, None, None, [disk])
        score, placement_list = manager.place(vm, None)
        # Image size is 2GB, 50% of 1GB, so transfer score is
        assert_that(score.transfer, is_(expected))
        assert_that(placement_list, has_length(2))  # vm and disk

    def test_place_vm_in_no_image_datastore(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(8 * 1024, 0), set([])),
                  "datastore_id_3": (DatastoreInfo(16 * 1024, 0), set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image,
                            image_size=100*1024*1024).build()
        total_storage = sum(t[0].total for t in ds_map.values())

        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        # disk1 and disk2 both take 1/100
        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image)
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100)
        vm = Vm(new_id(), self._vm_flavor_mb(1),
                State.STOPPED, None, None, [disk1, disk2])
        score, placement_list = manager.place(vm, None)

        # disk1 and disk2 both take 1% space, so utilization score is 98
        # image takes 100MB, which is 10% of 1GB, so transfer score is 90
        assert_that(score, is_(AgentPlacementScore(98, 90)))
        assert_that(placement_list, has_length(3))
        for placement in placement_list:
            assert_that(placement.container_id, is_("datastore_id_3"))

        # no other datastores except only image datastore available,
        # and use_image_datastore_for_vms:False, thus should fail.
        ds_map = {"image_datastore": (DatastoreInfo(8 * 1024, 0), set())}
        manager = PMBuilder(ds_map=ds_map, im_ds_for_vm=False).build()
        # vm with disks
        self.assertRaises(NoSuchResourceException, manager.place, vm, None)
        # vm without disks
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED)
        self.assertRaises(NoSuchResourceException, manager.place, vm, None)

    def test_place_vm_existing_image_two_matching_datastores(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(16 * 1024, 0), set([])),
                  "datastore_id_3": (DatastoreInfo(15 * 1024, 0), set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()
        total_storage = sum(t[0].total for t in ds_map.values())

        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        # disk1 and disk2 both take 1/100
        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image)
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image)
        vm = Vm(new_id(), self._vm_flavor_mb(1),
                State.STOPPED, None, None, [disk1, disk2])
        score, placement_list = manager.place(vm, None)

        # There are 2 disk of approx size 1/100 of avail space
        # the score should be 98
        assert_that(score, is_(AgentPlacementScore(98, 100)))

        base_index = 0
        assert_that(placement_list[base_index].
                    resource_id, is_(vm.id))
        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_2"))
        base_index += 1
        assert_that(placement_list[base_index].
                    resource_id, is_(disk1.id))
        assert_that(placement_list[base_index + 1].
                    resource_id, is_(disk2.id))
        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_2"))
        assert_that(placement_list[base_index + 1].
                    container_id, is_("datastore_id_2"))

    def test_place_with_disks_constraints(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(16 * 1024, 0), set([])),
                  "datastore_id_3": (DatastoreInfo(16 * 1024, 0), set([]))}
        ds_name_id_map = {"ds1": "datastore_id_1",
                          "ds2": "datastore_id_2",
                          "ds3": "datastore_id_3"}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        total_storage = sum(t[0].total for t in ds_map.values())
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image,
                            ds_name_id_map=ds_name_id_map).build()
        # Disable freespace based admission control.
        manager.FREESPACE_THRESHOLD = 0

        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        constraint1 = ResourceConstraint(
            ResourceConstraintType.DATASTORE, ["datastore_id_1"])
        constraint2 = ResourceConstraint(
            ResourceConstraintType.DATASTORE, ["ds2"])
        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image,
                     constraints=[constraint1])
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image,
                     constraints=[constraint2])
        disks = [disk1, disk2]

        score, placement_list = manager.place(None, disks)
        # Image disks doesn't count in utilization score
        assert_that(score, is_(AgentPlacementScore(100, 100)))

        base_index = 0
        assert_that(placement_list[base_index].
                    resource_id, is_(disk1.id))
        assert_that(placement_list[base_index + 1].
                    resource_id, is_(disk2.id))
        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_1"))
        assert_that(placement_list[base_index + 1].
                    container_id, is_("datastore_id_2"))

    @parameterized.expand([
        [True],
        [False]
    ])
    def test_place_large_disks(self, use_vm):
        ds_map = {"datastore_id_1": (DatastoreInfo(1 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(2 * 1024, 0), set([])),
                  "datastore_id_3": (DatastoreInfo(3 * 1024, 0), set([]))}
        ds_with_image = ["datastore_id_3"]
        total_storage = sum(t[0].total for t in ds_map.values())
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()

        image = DiskImage("disk_image", DiskImage.COPY_ON_WRITE)
        disk1 = Disk(new_id(), DISK_FLAVOR, False, True, 1024, image)
        disk2 = Disk(new_id(), DISK_FLAVOR, False, True, 1024, image)
        disk3 = Disk(new_id(), DISK_FLAVOR, False, True, 1024, image)
        disk4 = Disk(new_id(), DISK_FLAVOR, False, True, 1024, image)
        disk_list = [disk1, disk2, disk3, disk4]
        used_storage = sum(disk.capacity_gb for disk in disk_list)

        if use_vm:
            vm = Vm(new_id(), VM_FLAVOR,
                    State.STOPPED, None, None, disk_list)
            disks = None
        else:
            vm = None
            disks = disk_list
        score, placement_list = manager.place(vm, disks)

        # optimal placement cannot be achieved,
        # divide the expected score by NOT_OPTIMAL_DIVIDE_FACTOR
        expected_score = 100 * (total_storage - used_storage) / \
            total_storage / PlacementManager.NOT_OPTIMAL_DIVIDE_FACTOR
        assert_that(score, is_(AgentPlacementScore(expected_score, 100)))
        base_index = 0

        if vm:
            assert_that(placement_list[base_index].
                        resource_id, is_(vm.id))
            assert_that(placement_list[base_index].
                        container_id, is_("datastore_id_3"))
            base_index += 1

        assert_that(placement_list[base_index].
                    resource_id, is_(disk1.id))
        assert_that(placement_list[base_index + 1].
                    resource_id, is_(disk2.id))
        assert_that(placement_list[base_index + 2].
                    resource_id, is_(disk3.id))
        assert_that(placement_list[base_index + 3].
                    resource_id, is_(disk4.id))
        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_3"))
        assert_that(placement_list[base_index + 1].
                    container_id, is_("datastore_id_3"))
        assert_that(placement_list[base_index + 2].
                    container_id, is_("datastore_id_3"))
        assert_that(placement_list[base_index + 3].
                    container_id, is_("datastore_id_2"))

    @parameterized.expand([
        [True],
        [False]
    ])
    def test_place_large_disks_image_datastore(self, use_vm):
        ds_map = {"datastore_id_1": (DatastoreInfo(3 * 1024, 0), set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        total_storage = sum(t[0].total for t in ds_map.values())
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()
        # Disable freespace based admission control.
        manager.FREESPACE_THRESHOLD = 0

        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 1024)
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 1024)
        disk_list = [disk1, disk2]
        used_storage = sum(disk.capacity_gb for disk in disk_list)
        if use_vm:
            vm = Vm(new_id(), VM_FLAVOR,
                    State.STOPPED, None, None, disk_list)
            disks = None
        else:
            vm = None
            disks = disk_list
        score, placement_list = manager.place(vm, disks)
        expected_score = 100 * (total_storage - used_storage) / total_storage
        assert_that(score, is_(AgentPlacementScore(expected_score, 100)))
        base_index = 0

        if vm:
            assert_that(placement_list[base_index].
                        resource_id, is_(vm.id))
            assert_that(placement_list[base_index].
                        container_id, is_("datastore_id_1"))
            base_index += 1

        assert_that(placement_list[base_index].
                    resource_id, is_(disk1.id))
        assert_that(placement_list[base_index + 1].
                    resource_id, is_(disk2.id))

        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_1"))
        assert_that(placement_list[base_index + 1].
                    container_id, is_("datastore_id_1"))

    @parameterized.expand([
        (True, None),
        (False, None)
    ])
    @raises(NotEnoughDatastoreCapacityException)
    def test_place_vm_fail_disk_too_large(self, use_vm, expected):
        ds_map = {"datastore_id_1": (DatastoreInfo(1 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(2 * 1024, 0), set([])),
                  "datastore_id_3": (DatastoreInfo(3 * 1024, 0), set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()

        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 2 * 1024)
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 2 * 1024)
        disk3 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 2 * 1024)
        disk4 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 2 * 1024)
        disk_list = [disk1, disk2, disk3, disk4]
        if use_vm:
            vm = Vm(new_id(), VM_FLAVOR,
                    State.STOPPED, None, None, disk_list)
            disks = None
        else:
            vm = None
            disks = disk_list
        manager.place(vm, disks)

    @parameterized.expand([
        (True, True),
        (True, False),
        (False, True),
        (False, False)
    ])
    def test_placement_vm_on_image_datastore(self, use_image_ds, use_vm):
        ds_map = {"datastore_id_1": (DatastoreInfo(7 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(8 * 1024, 0), set([])),
                  "image_datastore": (DatastoreInfo(16 * 1024, 0), set([]))}
        ds_name_id_map = {"ds1": "datastore_id_1",
                          "ds2": "datastore_id_2",
                          "ids": "image_datastore"}
        image_ds = ["inaccessible_image_datastore", "image_datastore"]
        manager = PMBuilder(im_ds_for_vm=use_image_ds,
                            ds_name_id_map=ds_name_id_map,
                            image_ds=image_ds,
                            ds_map=ds_map).build()

        image = DiskImage("disk_image", DiskImage.COPY_ON_WRITE)
        disk1 = Disk(new_id(), DISK_FLAVOR, False, True, 7 * 1024, image)
        disk2 = Disk(new_id(), DISK_FLAVOR, False, True, 7 * 1024, image)
        disk3 = Disk(new_id(), DISK_FLAVOR, False, True, 7 * 1024, image)
        disk4 = Disk(new_id(), DISK_FLAVOR, False, True, 7 * 1024, image)
        disk_list = [disk1, disk2, disk3, disk4]

        if use_vm:
            vm = Vm(new_id(), VM_FLAVOR,
                    State.STOPPED, None, None, disk_list)
            disks = None
        else:
            vm = None
            disks = disk_list

        if not use_image_ds:
            self.assertRaises(
                NotEnoughDatastoreCapacityException, manager.place,
                vm, disks)
        else:
            (score, placement_list) = manager.place(vm, disks)

            container_ids = [item.container_id for item in placement_list]
            resource_ids = [item.resource_id for item in placement_list]

            if use_vm:
                assert_that(container_ids, contains(
                    "image_datastore",  # vm
                    "image_datastore",  # 16T, 8T, 7T
                    "image_datastore",  # 9T,  8T, 7T
                    "datastore_id_2",   # 2T,  8T, 7T
                    "datastore_id_1"))  # 2T,  1T, 7T
                assert_that(resource_ids, contains(vm.id, disk1.id, disk2.id,
                                                   disk3.id, disk4.id))
            else:
                assert_that(container_ids, contains(
                    "image_datastore",  # 16T, 8T, 7T
                    "image_datastore",  # 9T,  8T, 7T
                    "datastore_id_2",   # 2T,  8T, 7T
                    "datastore_id_1"))  # 2T,  1T, 7T
                assert_that(resource_ids,
                            contains(disk1.id, disk2.id, disk3.id,
                                     disk4.id))

    @parameterized.expand([
        (True, 0.8, 97),    # Disk score is not used
        (True, 0.2, 50),    # disk score is used and wins
        (False, 0.8, 100),  # Disk score is 100 as util below threshold
        (False, 0.2, 50)    # Disk score is computed, as util above threshold
    ])
    def test_place_disks_with_threshold(self, use_vm, threshold, expected):
        ds_map = {"datastore_id_1": (DatastoreInfo(1 * 1024, 0), set([]))}
        manager = PMBuilder(ds_map=ds_map).build()
        manager.FREESPACE_THRESHOLD = threshold

        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, 512, None)
        disk_list = [disk1]

        if use_vm:
            vm = Vm(new_id(), VM_FLAVOR,
                    State.STOPPED, None, None, disk_list)
            disks = None
        else:
            vm = None
            disks = disk_list
        score, placement_list = manager.place(vm, disks)

        # optimal placement cannot be achieved,
        # divide the expected score by NOT_OPTIMAL_DIVIDE_FACTOR
        assert_that(score, is_(AgentPlacementScore(expected, 100)))

    def test_place_image_disk_not_included(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(1 * 1024, 0), set([]))}
        manager = PMBuilder(ds_map=ds_map).build()

        image = DiskImage("disk_image1", DiskImage.COPY_ON_WRITE)
        # Place a image disk that is linked cloned from image. The size
        # should not be included in calculation.
        disk = Disk(new_id(), DISK_FLAVOR, False, True, 2048, image)
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None, None, [disk])

        score, placement_list = manager.place(vm, None)
        assert_that(score.utilization, equal_to(97))
        assert_that(score.transfer, equal_to(100))
        assert_that(placement_list, has_length(2))
        assert_that(placement_list[0].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[0].resource_id, equal_to(vm.id))
        assert_that(placement_list[1].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[1].resource_id, equal_to(disk.id))

        # Try the same place with constraints
        constraint = ResourceConstraint(
            ResourceConstraintType.DATASTORE, ["datastore_id_1"])
        disk = Disk(new_id(), DISK_FLAVOR, False, True, 2048, image,
                    constraints=[constraint])
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None, None, [disk])
        score, placement_list = manager.place(vm, None)
        assert_that(score.utilization, equal_to(97))
        assert_that(score.transfer, equal_to(100))
        assert_that(placement_list, has_length(2))
        assert_that(placement_list[0].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[0].resource_id, equal_to(vm.id))
        assert_that(placement_list[1].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[1].resource_id, equal_to(disk.id))

    def test_place_image_disk_best_effort(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(1 * 1024, 0), set([])),
                  "datastore_id_2": (DatastoreInfo(2 * 1024, 0), set([]))}
        manager = PMBuilder(ds_map=ds_map).build()

        image = DiskImage("disk_image1", DiskImage.COPY_ON_WRITE)
        # Place a image disk that is linked cloned from image. The size
        # should not be included in calculation.
        disk1 = Disk(new_id(), DISK_FLAVOR, False, True, 512, image)
        disk2 = Disk(new_id(), DISK_FLAVOR, False, True, 2048)
        disk3 = Disk(new_id(), DISK_FLAVOR, False, True, 512)
        vm = Vm(new_id(), VM_FLAVOR, State.STOPPED, None, None, [disk1,
                                                                 disk2,
                                                                 disk3])

        score, placement_list = manager.place(vm, None)
        # storage score is 1. 17/10. divided by 10 which is not optimal penalty
        assert_that(score.utilization, equal_to(1))
        assert_that(score.transfer, equal_to(100))
        assert_that(placement_list, has_length(4))
        assert_that(placement_list[0].container_id, equal_to("datastore_id_2"))
        assert_that(placement_list[0].resource_id, equal_to(vm.id))
        assert_that(placement_list[1].container_id, equal_to("datastore_id_2"))
        assert_that(placement_list[1].resource_id, equal_to(disk2.id))
        assert_that(placement_list[2].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[2].resource_id, equal_to(disk3.id))
        assert_that(placement_list[3].container_id, equal_to("datastore_id_1"))
        assert_that(placement_list[3].resource_id, equal_to(disk1.id))

    def test_place_with_disks_tagging_constraints(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0),
                                     set(["tag1", "tag2"])),
                  "datastore_id_2": (DatastoreInfo(16 * 1024, 0),
                                     set(["tag3", "tag2"])),
                  "datastore_id_3": (DatastoreInfo(16 * 1024, 0),
                                     set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        total_storage = sum(t[0].total for t in ds_map.values())
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()
        # Disable freespace based admission control.
        manager.FREESPACE_THRESHOLD = 0

        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        constraint1 = ResourceConstraint(
            ResourceConstraintType.DATASTORE_TAG, ["tag1"])
        constraint2 = ResourceConstraint(
            ResourceConstraintType.DATASTORE_TAG, ["tag2"])
        constraint3 = ResourceConstraint(
            ResourceConstraintType.DATASTORE_TAG, ["tag3"])
        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image,
                     constraints=[constraint1, constraint2])
        disk2 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image,
                     constraints=[constraint3])
        disks = [disk1, disk2]

        score, placement_list = manager.place(None, disks)

        base_index = 0
        assert_that(placement_list[base_index].
                    resource_id, is_(disk1.id))
        assert_that(placement_list[base_index + 1].
                    resource_id, is_(disk2.id))
        assert_that(placement_list[base_index].
                    container_id, is_("datastore_id_1"))
        assert_that(placement_list[base_index + 1].
                    container_id, is_("datastore_id_2"))

    @raises(NoSuchResourceException)
    def test_place_with_conflicting_constraints(self):
        ds_map = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0),
                                     set(["tag1", "tag2"])),
                  "datastore_id_2": (DatastoreInfo(16 * 1024, 0),
                                     set(["tag3", "tag2"])),
                  "datastore_id_3": (DatastoreInfo(16 * 1024, 0),
                                     set([]))}
        ds_with_image = ["datastore_id_1", "datastore_id_2"]
        total_storage = sum(t[0].total for t in ds_map.values())
        manager = PMBuilder(ds_map=ds_map, ds_with_image=ds_with_image).build()
        image = DiskImage("disk_image",
                          DiskImage.COPY_ON_WRITE)
        # The following 2 constraints conflict
        constraint1 = ResourceConstraint(
            ResourceConstraintType.DATASTORE_TAG, ["tag1"])
        constraint2 = ResourceConstraint(
            ResourceConstraintType.DATASTORE, ["datastore_id_2"])
        disk1 = Disk(new_id(), DISK_FLAVOR, False,
                     True, total_storage / 100, image,
                     constraints=[constraint1, constraint2])
        disks = [disk1]
        manager.place(None, disks)

    def _vm_flavor_gb(self, memory_gb, cpu=1):
        return Flavor("default", [
            QuotaLineItem("vm.memory", str(memory_gb), Unit.GB),
            QuotaLineItem("vm.cpu", str(cpu), Unit.COUNT),
        ])

    def _vm_flavor_mb(self, memory_mb, cpu=1):
        return Flavor("default", [
            QuotaLineItem("vm.memory", str(memory_mb), Unit.MB),
            QuotaLineItem("vm.cpu", str(cpu), Unit.COUNT),
        ])


class PMBuilder(object):

    DEFAULT_DS_MAP = {"datastore_id_1": (DatastoreInfo(8 * 1024, 0), set([]))}

    def __init__(self, total_mem=64*1024, image_id='image_id',
                 image_ds=['image_datastore'], mem_overcommit=1.0, ds_map=None,
                 ds_with_image=None, cpu_overcommit=None, im_ds_for_vm=False,
                 image_size=100*1024*1024, ds_name_id_map=None,
                 vm_networks=[], host_version="version1"):
        self._logger = logging.getLogger(__name__)
        self.total_mem = total_mem
        self.host_version = host_version
        self.image_id = image_id
        self.image_ds = image_ds
        self.mem_overcommit = mem_overcommit
        self.cpu_overcommit = cpu_overcommit
        if not self.cpu_overcommit:
            # Large number to prevent tests that do not specify overcommit,
            # from failing.
            self.cpu_overcommit = 100.0
        self.ds_map = ds_map or self.DEFAULT_DS_MAP
        if ds_with_image is None:
            self.ds_with_image = self.ds_map.keys()
        else:
            self.ds_with_image = ds_with_image
        self.image_size = image_size
        self._ds_name_id_map = ds_name_id_map
        self.vm_networks = vm_networks
        self.image_datastores = [{"name": ds, "used_for_vms": im_ds_for_vm}
                                 for ds in image_ds]

    def normalize(self, ds_name_or_id):
        # if test does not set ds_name_id_map, simply return name as id
        if not self._ds_name_id_map:
            return ds_name_or_id

        if ds_name_or_id in self._ds_name_id_map:
            return self._ds_name_id_map[ds_name_or_id]
        if ds_name_or_id in self._ds_name_id_map.values():
            return ds_name_or_id
        raise DatastoreNotFoundException("%s not found" % ds_name_or_id)

    def build(self):
        hypervisor = MagicMock()

        hypervisor.datastore_manager = MagicMock()
        hypervisor.datastore_manager.vm_datastores.return_value = \
            [ds for ds in self.ds_map.keys() if ds not in self.image_ds]
        hypervisor.datastore_manager.get_datastore_ids.return_value = \
            self.ds_map.keys()
        hypervisor.datastore_manager.datastore_info = self.datastore_info
        hypervisor.datastore_manager.normalize.side_effect = self.normalize
        hypervisor.datastore_manager.get_datastores.return_value = \
            [Datastore(id=ds_id, tags=self.ds_map[ds_id][1])
             for ds_id in self.ds_map.keys()]

        hypervisor.network_manager.get_vm_networks.return_value = \
            self.vm_networks

        hypervisor.system = MagicMock()
        hypervisor.system.total_vmusable_memory_mb.return_value = \
            self.total_mem
        hypervisor.system.host_version.return_value = \
            self.host_version
        hypervisor.system.num_physical_cpus.return_value = 1

        hypervisor.image_manager = MagicMock()
        hypervisor.image_manager.get_image_id_from_disks.return_value = \
            self.image_id
        hypervisor.image_manager.check_image = self.check_image
        hypervisor.image_manager.image_size.return_value = self.image_size

        hypervisor.vm_manager = MagicMock()
        hypervisor.vm_manager.get_used_memory_mb.return_value = 0

        placement_option = PlacementOption(self.mem_overcommit,
                                           self.cpu_overcommit,
                                           self.image_datastores)
        return PlacementManager(hypervisor, placement_option)

    def datastore_info(self, datastore_id):
        if datastore_id not in self.ds_map.keys():
            self._logger.warning("Datastore (%s) not connected" %
                                 datastore_id)
            raise Exception
        return self.ds_map[datastore_id][0]

    def check_image(self, image_id, datastore_id):
        if datastore_id in self.ds_with_image:
            return True
        else:
            return False


def new_id():
    return str(uuid.uuid4())
