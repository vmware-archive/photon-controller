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
import threading
import time
import unittest
import uuid

import common
from common.exclusive_set import ExclusiveSet
from common.file_util import mkdtemp
from common.kind import Flavor as HostFlavor
from common.mode import MODE, Mode
from common.service_name import ServiceName
from common.state import State as CommonState
from common.task_runner import TaskAlreadyRunning
from concurrent.futures import ThreadPoolExecutor
from gen.common.ttypes import ServerAddress
from gen.flavors.ttypes import Flavor
from gen.flavors.ttypes import QuotaLineItem
from gen.host.ttypes import CopyImageRequest
from gen.host.ttypes import CopyImageResultCode
from gen.host.ttypes import CreateDiskResultCode
from gen.host.ttypes import CreateDisksRequest
from gen.host.ttypes import CreateDisksResultCode
from gen.host.ttypes import CreateVmRequest
from gen.host.ttypes import CreateVmResultCode
from gen.host.ttypes import DeleteVmRequest
from gen.host.ttypes import DeleteVmResultCode
from gen.host.ttypes import DetachISORequest
from gen.host.ttypes import DetachISOResultCode
from gen.host.ttypes import GetDeletedImagesRequest
from gen.host.ttypes import GetHostModeRequest
from gen.host.ttypes import GetHostModeResultCode
from gen.host.ttypes import GetInactiveImagesRequest
from gen.host.ttypes import GetMonitoredImagesResultCode
from gen.host.ttypes import GetResourcesRequest
from gen.host.ttypes import GetResourcesResultCode
from gen.host.ttypes import HostMode
from gen.host.ttypes import PowerVmOpRequest
from gen.host.ttypes import PowerVmOpResultCode
from gen.host.ttypes import ReserveRequest
from gen.host.ttypes import ReserveResultCode
from gen.host.ttypes import StartImageOperationResultCode
from gen.host.ttypes import StartImageScanRequest
from gen.host.ttypes import StartImageSweepRequest
from gen.host.ttypes import StopImageOperationRequest
from gen.host.ttypes import StopImageOperationResultCode
from gen.host.ttypes import VmDiskOpResultCode
from gen.host.ttypes import VmDisksDetachRequest
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import Disk
from gen.resource.ttypes import Image
from gen.resource.ttypes import InactiveImageDescriptor
from gen.resource.ttypes import Resource
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType
from gen.resource.ttypes import ResourcePlacement
from gen.resource.ttypes import ResourcePlacementList
from gen.resource.ttypes import ResourcePlacementType
from gen.resource.ttypes import State
from gen.resource.ttypes import Vm
from gen.scheduler.ttypes import PlaceRequest
from gen.scheduler.ttypes import PlaceResultCode
from gen.scheduler.ttypes import Score
from host.host_handler import HostHandler
from host.hypervisor.datastore_manager import DatastoreNotFoundException
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.image_scanner import DatastoreImageScanner
from host.hypervisor.image_sweeper import DatastoreImageSweeper
from host.hypervisor.placement_manager import InvalidReservationException
from host.hypervisor.placement_manager import NoSuchResourceException
from host.hypervisor.resources import AgentResourcePlacement
from host.hypervisor.resources import Disk as HostDisk
from host.hypervisor.resources import NetworkInfo
from host.hypervisor.resources import NetworkInfoType
from host.hypervisor.resources import State as VmState
from host.hypervisor.system import DatastoreInfo
from host.hypervisor.vm_manager import DiskNotFoundException
from host.hypervisor.vm_manager import IsoNotAttachedException
from host.hypervisor.vm_manager import VmAlreadyExistException
from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.vm_manager import VmPowerStateException
from matchers import *  # noqa
from mock import MagicMock
from mock import call
from nose_parameterized import parameterized


def stable_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, name))


class HostHandlerTestCase(unittest.TestCase):

    REGEX_TIME = "^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)*$"

    def setUp(self):
        self._threadpool = ThreadPoolExecutor(16)
        self.state_file = tempfile.mktemp()
        self.state = CommonState(self.state_file)

        common.services.register(ThreadPoolExecutor, self._threadpool)

        common.services.register(ServiceName.REQUEST_ID, threading.local())
        common.services.register(ServiceName.LOCKED_VMS, ExclusiveSet())
        common.services.register(ServiceName.MODE, Mode(self.state))

        self.agent_conf_dir = mkdtemp(delete=True)
        self.hostname = "localhost"

        self._config = MagicMock()
        self._config.agent_id = stable_uuid("agent_id")
        self._config.hostname = "localhost"
        self._config.host_port = 1234
        self._config.host_version = "X.X.X"
        self._config.reboot_required = False
        self._config.image_datastores = []
        common.services.register(ServiceName.AGENT_CONFIG, self._config)

    def tearDown(self):
        common.services.reset()
        try:
            os.unlink(self.state_file)
        except:
            pass

    def create_resource(self, id):
        flavor = Flavor(name="flavor", cost=[QuotaLineItem("a", "b", 1)])
        disks = [
            Disk(stable_uuid("%s-1" % id), flavor, False, False, 1, datastore=Datastore("ds1")),
            Disk(stable_uuid("%s-2" % id), flavor, False, False, 1, datastore=Datastore("ds2")),
        ]

        vm = Vm()
        vm.id = id
        vm.flavor = "flavor"
        vm.flavor_info = flavor
        vm.state = State().STARTED
        vm.datastore = Datastore("ds1")

        resource = Resource(vm, disks)

        return resource

    def test_get_resources(self):
        handler = HostHandler(MagicMock())
        request = GetResourcesRequest()

        response = handler.get_resources(request)

        assert_that(response.result, equal_to(GetResourcesResultCode.OK))

    def test_place(self):
        handler = HostHandler(MagicMock())

        score = Score(100, 100)
        place_list = [MagicMock()]
        address = ServerAddress(host="localhost", port=1234)

        request = PlaceRequest(resource=Resource(self._sample_vm(), []))
        handler.hypervisor.placement_manager.place.return_value = (score, place_list)
        response = handler.place(request)
        assert_that(response.result, is_(PlaceResultCode.OK))
        assert_that(response.score, is_(score))
        assert_that(response.placementList.placements, is_([item.to_thrift() for item in place_list]))
        assert_that(response.address, is_(address))

        common.services.get(ServiceName.MODE).set_mode(MODE.ENTERING_MAINTENANCE)
        response = handler.place(request)
        assert_that(response.result, is_(PlaceResultCode.INVALID_STATE))

        common.services.get(ServiceName.MODE).set_mode(MODE.MAINTENANCE)
        response = handler.place(request)
        assert_that(response.result, is_(PlaceResultCode.INVALID_STATE))

    def test_place_resource_constraint(self):
        handler = HostHandler(MagicMock())
        request = PlaceRequest(resource=Resource(self._sample_vm(), []))
        handler.hypervisor.placement_manager.place.side_effect = \
            NoSuchResourceException("DATASTORE", "Datastore not available.")

        response = handler.place(request)
        assert_that(response.result, is_(PlaceResultCode.NO_SUCH_RESOURCE))

    def test_reserve_vm(self):
        disk_ids = ["disk_id_1", "disk_id_2", "disk_id_3"]
        datastore_ids = ["datastore_1", "datastore_2", "datastore_3"]
        disk_flavor = "disk_flavor_1"
        networks = [NetworkInfo(NetworkInfoType.NETWORK, "net_1"),
                    NetworkInfo(NetworkInfoType.NETWORK, "net_2")]
        vm_flavor = "vm_flavor_1"
        vm_id = "vm_id_1"

        def reserve_vm_validate(vm, disks):
            assert_that(vm)
            assert_that(not disks)
            assert_that(vm.id, equal_to(vm_id))
            # Check VM placement
            vm_placement = vm.placement
            assert_that(vm_placement is not None)
            assert_that(vm_placement.type is ResourcePlacementType.VM)
            assert_that(vm_placement.resource_id, equal_to(vm_id))
            assert_that(vm_placement.container_id, equal_to(datastore_ids[0]))

            # Check VM networks
            vm_networks = vm.networks
            assert_that(vm_networks is not None)
            assert_that(len(vm_networks) is 2)
            network_index = 0
            for network in vm_networks:
                assert_that(networks[network_index].type, equal_to(network.type))
                assert_that(networks[network_index].id, equal_to(network.id))
                network_index += 1

            disks = vm.disks
            assert_that(len(disks) is 3)
            disk_index = 0
            for vm_disk in disks:
                assert_that(vm_disk.id, equal_to(disk_ids[disk_index]))
                assert_that(vm_disk.placement is not None)
                disk_placement = vm_disk.placement
                assert_that(disk_placement.type is ResourcePlacementType.DISK)
                assert_that(disk_placement.resource_id, equal_to(vm_disk.id))
                assert_that(disk_placement.container_id, equal_to(datastore_ids[disk_index]))
                disk_index += 1
            return "reservation_id"

        handler = HostHandler(MagicMock())
        mocked_reserve = MagicMock()
        mocked_reserve.side_effect = reserve_vm_validate
        handler.hypervisor.placement_manager = MagicMock()
        handler.hypervisor.placement_manager.reserve = mocked_reserve

        placements = []

        # Add VM placement info
        placement = ResourcePlacement()
        placement.type = ResourcePlacementType.VM
        placement.resource_id = vm_id
        placement.container_id = datastore_ids[0]
        placements.append(placement)

        # Add Network placement info : net_1
        placement = ResourcePlacement()
        placement.type = ResourcePlacementType.NETWORK
        placement.resource_id = vm_id
        placement.container_id = networks[0].id
        placements.append(placement)

        # Add Network placement info : net_2
        placement = ResourcePlacement()
        placement.type = ResourcePlacementType.NETWORK
        placement.resource_id = vm_id
        placement.container_id = networks[1].id
        placements.append(placement)

        # Add disks placement info
        index = 0
        for disk_id in disk_ids:
            placement = ResourcePlacement()
            placement.type = ResourcePlacementType.DISK
            placement.container_id = datastore_ids[index]
            index += 1
            placement.resource_id = disk_id
            placements.append(placement)

        placement_list = ResourcePlacementList(placements)

        disk_flavor_info = Flavor(name=disk_flavor, cost=[QuotaLineItem("size", "1", 1)])
        disks = []
        for disk_id in disk_ids:
            disk = Disk(id=disk_id,
                        flavor=disk_flavor,
                        persistent=True,
                        new_disk=True,
                        capacity_gb=2,
                        flavor_info=disk_flavor_info)
            disks.append(disk)

        vm_flavor_info = Flavor(name=vm_flavor, cost=[QuotaLineItem("cpu", "1", 5)])
        vm = Vm(vm_id, vm_flavor, State.STOPPED, None, None, disks, vm_flavor_info)

        request = ReserveRequest()
        request.generation = 1
        request.resource = Resource(vm=vm, disks=None, placement_list=placement_list)

        response = handler.reserve(request)
        assert_that(response.result, equal_to(ReserveResultCode.OK))

        # test reserve under entering-maintenance-mode and maintenance mode
        state = common.services.get(ServiceName.MODE)
        state.set_mode(MODE.ENTERING_MAINTENANCE)
        request = MagicMock()
        response = handler.reserve(request)
        assert_that(response.result, equal_to(ReserveResultCode.OPERATION_NOT_ALLOWED))

        state.set_mode(MODE.MAINTENANCE)
        response = handler.reserve(request)
        assert_that(response.result, equal_to(ReserveResultCode.OPERATION_NOT_ALLOWED))

    def test_reserve_vm_with_virtual_network(self):
        network = NetworkInfo(NetworkInfoType.VIRTUAL_NETWORK, "vnet_1")
        vm_flavor = "vm_flavor_1"
        vm_id = "vm_id_1"

        def reserve_vm_vnet_validate(vm, disks):
            assert_that(vm)
            assert_that(not disks)
            assert_that(vm.id, equal_to(vm_id))

            # Check VM virtual network
            assert_that(len(vm.networks) is 1)
            vm_network = vm.networks[0]
            assert_that(vm_network is not None)
            assert_that(network.type, equal_to(vm_network.type))
            assert_that(network.id, equal_to(vm_network.id))

            return "reservation_id"

        handler = HostHandler(MagicMock())
        mocked_reserve = MagicMock()
        mocked_reserve.side_effect = reserve_vm_vnet_validate
        handler.hypervisor.placement_manager = MagicMock()
        handler.hypervisor.placement_manager.reserve = mocked_reserve

        # Add Network placement info : vnet_1
        placements = []
        placement = ResourcePlacement()
        placement.type = ResourcePlacementType.VIRTUAL_NETWORK
        placement.resource_id = vm_id
        placement.container_id = network.id
        placements.append(placement)
        placement_list = ResourcePlacementList(placements)

        vm = Vm(vm_id, vm_flavor, State.STOPPED, None, None, None, None)

        request = ReserveRequest()
        request.generation = 1
        request.resource = Resource(vm=vm, disks=None, placement_list=placement_list)

        response = handler.reserve(request)
        assert_that(response.result, equal_to(ReserveResultCode.OK))

    @parameterized.expand([
        ("datastore_1", None, "datastore_1"),
        (None, "datastore_2", "datastore_2")
    ])
    def test_reserve_disk(self, constraint_value,
                          placement_id, expected):
        disk_id = "disk_id_1"
        disk_flavor = "disk_flavor_1"

        def reserve_disk_validate(vm, disks):
            assert_that(vm is None)
            assert isinstance(disks, list)
            assert_that(len(disks) is 1)
            disk = disks[0]
            assert isinstance(disk, HostDisk)
            assert_that(disk.id, equal_to(disk_id))
            assert_that(disk.flavor.name, equal_to(disk_flavor))
            reserve_constraints = disk.constraints
            if reserve_constraints:
                assert isinstance(reserve_constraints, list)
                assert_that(len(reserve_constraints) is 1)
                reserve_constraint = reserve_constraints[0]
                assert_that(reserve_constraint.type is ResourceConstraintType.DATASTORE)
                assert_that(reserve_constraint.values, equal_to([expected, ]))

            reserve_placement = disk.placement
            if reserve_placement:
                assert_that(reserve_placement.type is ResourcePlacementType.DISK)
                assert_that(reserve_placement.resource_id, equal_to(disk_id))
                assert_that(reserve_placement.container_id, equal_to(expected))
            return "reservation_id"

        handler = HostHandler(MagicMock())
        mocked_reserve = MagicMock()
        mocked_reserve.side_effect = reserve_disk_validate
        handler.hypervisor.placement_manager = MagicMock()
        handler.hypervisor.placement_manager.reserve = mocked_reserve

        constraints = None
        placement_list = None

        if constraint_value:
            constraint = ResourceConstraint()
            constraint.values = [constraint_value]
            constraint.type = ResourceConstraintType.DATASTORE
            constraints = [constraint]

        if placement_id:
            placement = ResourcePlacement()
            placement.type = ResourcePlacementType.DISK
            placement.container_id = placement_id
            placement.resource_id = disk_id
            placement_list = ResourcePlacementList([placement])

        flavor_info = Flavor(name=disk_flavor, cost=[QuotaLineItem("a", "b", 1)])
        disk = Disk(id=disk_id,
                    flavor=disk_flavor,
                    persistent=True,
                    new_disk=True,
                    capacity_gb=2,
                    flavor_info=flavor_info,
                    resource_constraints=constraints)
        request = ReserveRequest()
        request.generation = 1
        request.resource = Resource(vm=None, disks=[disk], placement_list=placement_list)

        response = handler.reserve(request)
        assert_that(response.result, equal_to(ReserveResultCode.OK))

    def test_serialize_vm_op(self):
        handler = HostHandler(MagicMock())
        vmm = handler.hypervisor.vm_manager

        result = {
            PowerVmOpResultCode.OK: 0,
            PowerVmOpResultCode.CONCURRENT_VM_OPERATION: 0,
            "concurrent_threads": 0
        }
        lock = threading.Lock()

        def _slow_op(vm_id):
            with lock:
                result["concurrent_threads"] += 1
                # Verify that at any point of time, there is only one thread
                # running for a specific VM
                assert_that(result["concurrent_threads"], is_(1))
            time.sleep(0.01)
            with lock:
                result["concurrent_threads"] -= 1

        vmm.power_on_vm.side_effect = _slow_op
        vmm.power_off_vm.side_effect = _slow_op
        vmm.reset_vm.side_effect = _slow_op
        vmm.suspend_vm.side_effect = _slow_op

        def _test_thread(op):
            request = PowerVmOpRequest(vm_id='vm_id', op=op)
            response = handler.power_vm_op(request)
            with lock:
                result[response.result] += 1

        threads = []
        # Issue 4 operations, 50 each
        for op in range(200):
            # poweron, poweroff, reset, suspend are 1-4
            thread = threading.Thread(target=_test_thread, args=(op % 4 + 1,))
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

        # All 200 operations are either OK or CONCURRENT_VM_OPERATION.
        ok = result[PowerVmOpResultCode.OK]
        concurrent = result[PowerVmOpResultCode.CONCURRENT_VM_OPERATION]
        assert_that(ok + concurrent, is_(200), "(OK:%d)+(concurrent:%d) should==200" % (ok, concurrent))

    def test_power_vm_op(self):
        handler = HostHandler(MagicMock())

        # test power_vm_op under entering-maintenance-mode and maintenance mode
        state = common.services.get(ServiceName.MODE)
        state.set_mode(MODE.ENTERING_MAINTENANCE)
        request = MagicMock()
        response = handler.power_vm_op(request)
        assert_that(response.result, equal_to(PowerVmOpResultCode.OPERATION_NOT_ALLOWED))

        state.set_mode(MODE.MAINTENANCE)
        response = handler.power_vm_op(request)
        assert_that(response.result, equal_to(PowerVmOpResultCode.OPERATION_NOT_ALLOWED))

    @parameterized.expand([
        (None, CreateDiskResultCode.PLACEMENT_NOT_FOUND)
    ])
    def test_create_disk_placement_failure(
            self, placement, expected):

        def local_consume_disk_reservation(reservation_id):
            assert_that(reservation_id is "reservation_id_1")
            host_disk = HostDisk()
            host_disk.id = "disk_id_1"
            host_disk.flavor = HostFlavor("disk_flavor_1")
            host_disk.persistent = True
            host_disk.new_disk = True
            host_disk.capacity_gb = 2
            if placement:
                host_disk.placement = AgentResourcePlacement(
                    AgentResourcePlacement.DISK,
                    host_disk.id,
                    placement
                )
            return [host_disk]

        handler = HostHandler(MagicMock())

        # set the list of datastores
        handler.hypervisor.vm_datastores = ["datastore_1", "datastore_2", "datastore_3"]
        handler.hypervisor.placement_manager = MagicMock()

        mocked_consume_disk_reservation = MagicMock()
        mocked_consume_disk_reservation.side_effect = local_consume_disk_reservation
        pm = handler.hypervisor.placement_manager
        pm.consume_disk_reservation = mocked_consume_disk_reservation

        request = CreateDisksRequest()
        request.generation = 1
        request.reservation = "reservation_id_1"
        response = handler.create_disks(request)
        pm.remove_disk_reservation.assert_called_once_with(request.reservation)
        assert_that(response.result is CreateDisksResultCode.OK)
        disk_errors = response.disk_errors
        assert_that(disk_errors is not None)
        assert_that(len(disk_errors) is 1)
        disk_error = disk_errors["disk_id_1"]
        assert_that(disk_error is not None)
        assert_that(disk_error.result is expected)

    @parameterized.expand([
        ('datastore_1', 'datastore_1', 'ds_name_1'),
        ('datastore_2', 'datastore_2', 'ds_name_2')
    ])
    def test_create_disk_placement(self, placement, expected_id,
                                   expected_name):

        def local_consume_disk_reservation(reservation_id):
            assert_that(reservation_id is "reservation_id_1")
            host_disk = HostDisk()
            host_disk.id = "disk_id_1"
            host_disk.flavor = HostFlavor("disk_flavor_1")
            host_disk.persistent = True
            host_disk.new_disk = True
            host_disk.capacity_gb = 2
            if placement:
                host_disk.placement = AgentResourcePlacement(
                    AgentResourcePlacement.DISK,
                    host_disk.id,
                    placement
                )
            return [host_disk]

        handler = HostHandler(MagicMock())

        # set the list of datastores
        handler.hypervisor.datastore_manager.vm_datastores.return_value = ["datastore_1", "datastore_2", "datastore_3"]
        handler.hypervisor.datastore_manager.datastore_name.return_value = expected_name
        handler.hypervisor.placement_manager = MagicMock()

        mocked_consume_disk_reservation = MagicMock()
        mocked_consume_disk_reservation.side_effect = local_consume_disk_reservation
        handler.hypervisor.placement_manager.consume_disk_reservation = mocked_consume_disk_reservation

        request = CreateDisksRequest()
        request.generation = 1
        request.reservation = "reservation_id_1"
        response = handler.create_disks(request)
        assert_that(response.result is CreateDisksResultCode.OK)
        response_disks = response.disks
        assert_that(len(response_disks) is 1)
        response_disk = response_disks[0]
        assert_that(response_disk.id, equal_to("disk_id_1"))
        assert_that(response_disk.datastore.id, equal_to(expected_id))
        assert_that(response_disk.datastore.name, equal_to(expected_name))

    def test_create_vm(self):
        handler = HostHandler(MagicMock())
        # test create_vm under entering-maintenance-mode and maintenance mode
        state = common.services.get(ServiceName.MODE)
        state.set_mode(MODE.ENTERING_MAINTENANCE)
        request = MagicMock()
        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.OPERATION_NOT_ALLOWED))

        state.set_mode(MODE.MAINTENANCE)
        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.OPERATION_NOT_ALLOWED))

        # Back to NORMAL mode
        state.set_mode(MODE.NORMAL)
        handler.hypervisor.placement_manager = MagicMock()
        handler._select_datastore_for_vm_create = MagicMock(return_value="ds1")
        handler.hypervisor.datastore_manager.image_datastores = MagicMock(return_value=set("image_ds"))
        handler.hypervisor.image_manager.get_image_id_from_disks = MagicMock(return_value="image_id")

        vm = MagicMock()
        vm.id = str(uuid.uuid4())
        vm_manager = handler.hypervisor.vm_manager
        vm_location_id = str(uuid.uuid4())
        vm_manager.get_location_id.return_value = vm_location_id
        pm = handler.hypervisor.placement_manager
        pm.consume_vm_reservation.return_value = vm
        dm = handler.hypervisor.datastore_manager
        dm.datastore_type.return_value = DatastoreType.EXT3
        dm.image_datastores.return_value = set(["image_ds"])
        im = handler.hypervisor.image_manager
        request = MagicMock()
        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.OK))
        pm.remove_vm_reservation.assert_called_once_with(request.reservation)
        vm_manager.get_location_id.assert_called_once_with(vm.id)
        assert_that(vm.location_id, equal_to(vm_location_id))
        vm.to_thrift.assert_called_once()

        # Test lazy image copy
        assert_that(im.copy_image.called, is_(False))
        im.check_and_validate_image = MagicMock(side_effect=[False, True])
        im.check_and_validate_image.return_value = False
        pm.remove_vm_reservation.reset_mock()
        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.OK))
        pm.remove_vm_reservation.assert_called_once_with(request.reservation)
        im.copy_image.assert_called_once_with(
            "image_ds", "image_id", "ds1", "image_id"
        )

        # Test VM existed
        im.check_image.return_value = True
        im.check_and_validate_image = MagicMock(side_effect=[False, True])
        pm.remove_vm_reservation.reset_mock()
        handler.hypervisor.vm_manager.create_vm.side_effect = VmAlreadyExistException

        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.VM_ALREADY_EXIST))
        pm.remove_vm_reservation.assert_called_once_with(request.reservation)

        # Test invalid reservation
        class PlacementManagerInvalidReservation:
            def consume_vm_reservation(self, reservation):
                raise InvalidReservationException

        handler.hypervisor.placement_manager = PlacementManagerInvalidReservation()
        response = handler.create_vm(request)
        assert_that(response.result, equal_to(CreateVmResultCode.INVALID_RESERVATION))

    def test_create_vm_on_correct_resource(self):
        """Check that we create the vm on the correct datastore"""

        vm = MagicMock()
        vm.id = str(uuid.uuid4())
        vm.networks = [NetworkInfo(NetworkInfoType.NETWORK, "net_1"),
                       NetworkInfo(NetworkInfoType.NETWORK, "net_2")]
        vm.project_id = "p1"
        vm.tenant_id = "t1"

        mock_env = MagicMock()
        mock_reservation = MagicMock()

        req = CreateVmRequest(reservation=mock_reservation, environment=mock_env)
        image_id = stable_uuid('image_id')
        handler = HostHandler(MagicMock())
        pm = handler.hypervisor.placement_manager
        pm.consume_vm_reservation.return_value = vm
        handler._datastores_for_image = MagicMock()
        handler.hypervisor.datastore_manager.datastore_type.return_value = DatastoreType.EXT3
        handler.hypervisor.datastore_manager.image_datastores = MagicMock(return_value=set("ds2"))
        im = handler.hypervisor.image_manager
        im.get_image_refcount_filename.return_value = os.path.join(self.agent_conf_dir, vm.id)
        im.get_image_id_from_disks.return_value = image_id

        # No placement descriptor
        vm.placement = None
        response = handler.create_vm(req)
        pm.remove_vm_reservation.assert_called_once_with(mock_reservation)
        assert_that(response.result, equal_to(CreateVmResultCode.PLACEMENT_NOT_FOUND))

        # If vm reservation has placement datastore info, it should
        # be placed there
        handler.hypervisor.vm_manager.create_vm_spec.reset_mock()
        pm.remove_vm_reservation.reset_mock()
        vm.placement = AgentResourcePlacement(AgentResourcePlacement.VM, "vm_ids", "ds2")
        handler.hypervisor.network_manager.get_vm_networks.return_value = ["net_2", "net_1"]

        response = handler.create_vm(req)
        spec = handler.hypervisor.vm_manager.create_vm_spec.return_value
        metadata = handler.hypervisor.image_manager.image_metadata.return_value
        handler.hypervisor.vm_manager.create_vm_spec.assert_called_once_with(
            vm.id, "ds2", vm.flavor, metadata, mock_env)
        handler.hypervisor.vm_manager.create_vm.assert_called_once_with(vm.id, spec)
        pm.remove_vm_reservation.assert_called_once_with(mock_reservation)
        assert_that(response.result, equal_to(CreateVmResultCode.OK))

        # Test create_vm honors vm.networks information
        # Host has the provisioned networks required by placement_list,
        # should succeed.
        handler.hypervisor.network_manager.get_vm_networks.return_value = ["net_2", "net_1"]

        handler.hypervisor.vm_manager.create_vm_spec.reset_mock()
        pm.remove_vm_reservation.reset_mock()
        spec = handler.hypervisor.vm_manager.create_vm_spec.return_value
        req = CreateVmRequest(reservation=mock_reservation)
        response = handler.create_vm(req)

        called_networks = spec.add_nic.call_args_list
        expected_networks = [call('net_1'), call('net_2')]
        assert_that(called_networks == expected_networks, is_(True))
        pm.remove_vm_reservation.assert_called_once_with(mock_reservation)
        assert_that(response.result, equal_to(CreateVmResultCode.OK))

        # Host does not have the provisioned networks
        # required by placement_list, should fail.
        handler.hypervisor.network_manager.get_vm_networks.return_value = ["net_1", "net_7"]
        pm.remove_vm_reservation.reset_mock()

        req = CreateVmRequest(reservation=mock_reservation)
        response = handler.create_vm(req)

        pm.remove_vm_reservation.assert_called_once_with(mock_reservation)
        assert_that(response.result, equal_to(CreateVmResultCode.NETWORK_NOT_FOUND))

    def test_delete_vm_wrong_state(self):
        handler = HostHandler(MagicMock())
        dm = handler.hypervisor.datastore_manager
        dm.datastore_type.return_value = DatastoreType.EXT3
        vm_id = str(uuid.uuid4())
        im = handler.hypervisor.image_manager
        im.get_image_refcount_filename.return_value = os.path.join(self.agent_conf_dir, vm_id)
        request = DeleteVmRequest(vm_id=vm_id)
        delete_vm = handler.hypervisor.vm_manager.delete_vm

        delete_vm.side_effect = VmPowerStateException
        response = handler.delete_vm(request)
        assert_that(response.result, is_(DeleteVmResultCode.VM_NOT_POWERED_OFF))

        delete_vm.side_effect = VmNotFoundException
        response = handler.delete_vm(request)
        assert_that(response.result, is_(DeleteVmResultCode.VM_NOT_FOUND))

        delete_vm.side_effect = None
        response = handler.delete_vm(request)
        assert_that(response.result, is_(DeleteVmResultCode.OK))

    def test_disk_ops_disk_not_found(self):
        """Attaching/detaching a disk that doesn't exist should report error"""

        def _raise_disk_not_found_exception(disk_id):
            raise DiskNotFoundException

        req = VmDisksDetachRequest(vm_id="vm.id", disk_ids=["disk.id"])

        handler = HostHandler(MagicMock())
        get_resource = MagicMock()
        get_resource.side_effect = _raise_disk_not_found_exception
        handler.hypervisor.disk_manager.get_resource = get_resource

        response = handler.attach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.DISK_NOT_FOUND))
        response = handler.detach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.DISK_NOT_FOUND))

    def test_disk_ops_vm_not_found(self):
        """
        Attaching/detaching a disk from a vm that doesn't exist should report
        error
        """
        req = VmDisksDetachRequest(vm_id="vm.id", disk_ids=["disk.id"])

        handler = HostHandler(MagicMock())
        handler.hypervisor = MagicMock()
        handler.hypervisor.disk_manager.get_resource = MagicMock()
        handler.hypervisor.vm_manager._get_vm_config.side_effect = VmNotFoundException
        handler.hypervisor.vm_manager.get_resource.side_effect = VmNotFoundException

        response = handler.attach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.VM_NOT_FOUND))
        response = handler.detach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.VM_NOT_FOUND))

    def test_disk_ops_vm_suspended(self):
        """ Attach/detach a disk from a vm when vm is suspended
        """
        req = VmDisksDetachRequest(vm_id="vm.id", disk_ids=["disk.id"])

        handler = HostHandler(MagicMock())
        handler.hypervisor = MagicMock()
        vm = Vm(id="vm.id", state=VmState.SUSPENDED)
        handler.hypervisor.vm_manager.get_resource.return_value = vm

        response = handler.attach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.INVALID_VM_POWER_STATE))
        response = handler.detach_disks(req)
        assert_that(response.result, equal_to(VmDiskOpResultCode.INVALID_VM_POWER_STATE))

    def test_copy_image(self):
        """ Copy image unit tests """
        handler = HostHandler(MagicMock())
        image_mgr = MagicMock()
        image_mgr.check_image = MagicMock(return_value=False)
        handler.hypervisor.image_manager = image_mgr

        # Simulates ds name-to-id normalization by assuming all parameters are
        # datastore ids, exception those ending in "_name" are treated as names
        # which normalizes to an id with suffix removed.
        def fake_normalize(ds_name_or_id):
            if ds_name_or_id.endswith("_name"):
                return ds_name_or_id[:-5]
            return ds_name_or_id

        mock_ds_manager = handler.hypervisor.datastore_manager
        mock_ds_manager.datastore_type.return_value = DatastoreType.EXT3
        mock_ds_manager.normalize.side_effect = fake_normalize

        # Check self copy is a no-op.
        src_image = Image("id1", Datastore("datastore1"))
        dst_image = Image("id1", Datastore("datastore1"))
        req = CopyImageRequest(src_image, dst_image)
        response = handler.copy_image(req)
        self.assertEqual(response.result, CopyImageResultCode.OK)

        # Still a self copy if datastores normalizes to the same id.
        src_image = Image("id1", Datastore("datastore1_name"))
        req = CopyImageRequest(src_image, dst_image)
        response = handler.copy_image(req)
        self.assertEqual(response.result, CopyImageResultCode.OK)

        # Check that if image is not found we return the correct code.
        dst_image = Image("id1", Datastore("datastore2"))
        req = CopyImageRequest(src_image, dst_image)
        response = handler.copy_image(req)
        self.assertEqual(response.result, CopyImageResultCode.IMAGE_NOT_FOUND)

        # Test destination image already exists.
        image_mgr.check_image = MagicMock(return_value=True)
        image_mgr.copy_image = MagicMock(side_effect=DiskAlreadyExistException)
        req = CopyImageRequest(src_image, dst_image)
        response = handler.copy_image(req)
        self.assertEqual(response.result, CopyImageResultCode.DESTINATION_ALREADY_EXIST)

        # Happy path.
        src_image = Image("id1", Datastore("datastore1_name"))
        dst_image = Image("id1", Datastore("datastore2_name"))
        image_mgr.check_image = MagicMock(return_value=True)
        image_mgr.copy_image = MagicMock()
        req = CopyImageRequest(src_image, dst_image)
        response = handler.copy_image(req)
        self.assertEqual(response.result, CopyImageResultCode.OK)
        image_mgr.check_image.assert_called_once_with("id1", "datastore1")
        image_mgr.copy_image.assert_called_once_with("datastore1", "id1", "datastore2", "id1")

    def test_detach_iso(self):
        """ ISO detach unit tests """
        handler = HostHandler(MagicMock())

        request = DetachISORequest(vm_id="vm.id", delete_file=False)

        response = handler.detach_iso(request)
        self.assertEqual(response.result, DetachISOResultCode.SYSTEM_ERROR)
        self.assertEqual(response.error, 'NotImplementedError')

        request = DetachISORequest(vm_id="vm.id", delete_file=True)
        vm_manager = handler.hypervisor.vm_manager

        vm_manager.detach_iso.side_effect = VmNotFoundException
        response = handler.detach_iso(request)
        self.assertEqual(response.result, DetachISOResultCode.VM_NOT_FOUND)

        vm_manager.detach_iso.side_effect = IsoNotAttachedException
        response = handler.detach_iso(request)
        self.assertEqual(response.result, DetachISOResultCode.ISO_NOT_ATTACHED)

    def test_get_mode(self):
        handler = HostHandler(MagicMock())
        response = handler.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        assert_that(response.mode, equal_to(HostMode.NORMAL))

    def _sample_vm(self):
        flavor = Flavor(name="flavor", cost=[QuotaLineItem("a", "b", 1)])
        disks = [
            Disk(stable_uuid("%s-1" % id), flavor, False, False, 1, datastore=Datastore("ds1")),
            Disk(stable_uuid("%s-2" % id), flavor, False, False, 1, datastore=Datastore("ds2")),
        ]

        vm = Vm()
        vm.id = id
        vm.flavor = "flavor"
        vm.flavor_info = flavor
        vm.disks = disks
        vm.state = State().STARTED
        vm.datastore = Datastore("ds1")

        return vm

    def test_touch_image_timestamp(self):
        """Test touch image timestamp against mock"""
        handler = HostHandler(MagicMock())
        # no failure
        vm_id = uuid.uuid4()
        res = handler._touch_image_timestamp(vm_id, "ds", "image")
        assert_that(res.result, equal_to(CreateVmResultCode.OK))

        # image not found
        handler.hypervisor.image_manager.touch_image_timestamp.side_effect = OSError()
        res = handler._touch_image_timestamp(vm_id, "ds", "image")
        assert_that(res.result, equal_to(CreateVmResultCode.IMAGE_NOT_FOUND))

        # invalid datastore, this must be last in the sequence
        handler.hypervisor.datastore_manager.normalize.side_effect = DatastoreNotFoundException()
        res = handler._touch_image_timestamp(vm_id, "ds", "image")
        assert_that(res.result, equal_to(CreateVmResultCode.SYSTEM_ERROR))

    def test_start_image_scan(self):
        """Test start_image_scan against mock"""
        handler = HostHandler(MagicMock())
        image_monitor = MagicMock()
        image_monitor.get_image_scanner = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        # Setup request
        request = StartImageScanRequest()
        request.datastore_id = "DS_ID_1"
        request.timeout = 10
        request.scan_rate = 100

        # Test success
        image_scanner = MagicMock()
        image_monitor.get_image_scanner.return_value = image_scanner

        result = handler.start_image_scan(request)

        assert_that(result.result is StartImageOperationResultCode.OK)
        image_scanner.start.assert_called_with(10, 100, 100)

        # Test operation in progress
        image_scanner.start = MagicMock()
        image_scanner.start.side_effect = TaskAlreadyRunning
        result = handler.start_image_scan(request)

        assert_that(result.result is StartImageOperationResultCode.SCAN_IN_PROGRESS)

        # Test invalid datastore
        image_monitor.get_image_scanner.side_effect = DatastoreNotFoundException
        result = handler.start_image_scan(request)

        assert_that(result.result is StartImageOperationResultCode.DATASTORE_NOT_FOUND)

    def test_stop_image_scan(self):
        """Test start_image_scan against mock"""
        handler = HostHandler(MagicMock())

        image_monitor = MagicMock()
        image_monitor.get_image_scanner = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        # Setup request
        request = StopImageOperationRequest()
        request.datastore_id = "DS_ID_1"

        # Test success
        image_scanner = MagicMock()
        image_monitor.get_image_scanner.return_value = image_scanner

        result = handler.stop_image_scan(request)

        assert_that(result.result is StopImageOperationResultCode.OK)
        image_scanner.stop.assert_called()

        # Test invalid datastore
        image_monitor.get_image_scanner.side_effect = DatastoreNotFoundException
        result = handler.stop_image_scan(request)

        assert_that(result.result is StopImageOperationResultCode.DATASTORE_NOT_FOUND)

    def test_start_image_sweep(self):
        """Test touch image timestamp against mock"""
        handler = HostHandler(MagicMock())

        image_monitor = MagicMock()
        image_monitor.get_image_scanner = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        # Setup request
        request = StartImageSweepRequest()
        image_descriptors = list()

        image_desc1 = InactiveImageDescriptor()
        image_desc1.image_id = "image_id_1"
        image_desc1.timestamp = 100
        image_descriptors.append(image_desc1)

        image_desc2 = InactiveImageDescriptor()
        image_desc2.image_id = "image_id_2"
        image_desc2.timestamp = 200
        image_descriptors.append(image_desc2)

        request.datastore_id = "DS_ID_1"
        request.image_descs = image_descriptors
        request.timeout = 10
        request.sweep_rate = 100

        # Test success
        image_sweeper = MagicMock()
        image_sweeper.start = MagicMock()
        image_sweeper.start.side_effect = self._local_image_sweeper_start
        image_monitor.get_image_sweeper.return_value = image_sweeper

        result = handler.start_image_sweep(request)

        assert_that(result.result is StartImageOperationResultCode.OK)

        # Test operation in progress
        image_sweeper.start.side_effect = TaskAlreadyRunning
        result = handler.start_image_sweep(request)

        assert_that(result.result is StartImageOperationResultCode.SWEEP_IN_PROGRESS)

        # Test invalid datastore
        image_monitor.get_image_sweeper.side_effect = DatastoreNotFoundException
        result = handler.start_image_sweep(request)

        assert_that(result.result is StartImageOperationResultCode.DATASTORE_NOT_FOUND)

    def _local_image_sweeper_start(self, image_list, timeout,
                                   sweep_rate, grace_period):
        assert_that(len(image_list) is 2)
        assert_that(image_list[0] is "image_id_1")
        assert_that(image_list[1] is "image_id_2")
        assert_that(timeout is 10)
        assert_that(sweep_rate is 100)
        assert_that(grace_period is None)

    def test_stop_image_sweep(self):
        """Test start_image_scan against mock"""
        handler = HostHandler(MagicMock())

        image_monitor = MagicMock()
        image_monitor.get_image_sweeper = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        # Setup request
        request = StopImageOperationRequest()
        request.datastore_id = "DS_ID_1"

        # Test success
        image_scanner = MagicMock()
        image_monitor.get_image_sweeper.return_value = image_scanner

        result = handler.stop_image_sweep(request)

        assert_that(result.result is StopImageOperationResultCode.OK)
        image_scanner.stop.assert_called()

        # Test invalid datastore
        image_monitor.get_image_sweeper.side_effect = DatastoreNotFoundException
        result = handler.stop_image_sweep(request)

        assert_that(result.result is StopImageOperationResultCode.DATASTORE_NOT_FOUND)

    def test_get_inactive_images(self):
        handler = HostHandler(MagicMock())

        image_monitor = MagicMock()
        image_monitor.get_image_scanner = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        image_scanner = MagicMock()

        # Mock datastore manager and datastore_info()
        datastore_manager = MagicMock()
        datastore_manager.datastore_info = MagicMock()
        datastore_manager.datastore_info.return_value = DatastoreInfo(10.2, 6.1)
        handler._hypervisor.datastore_manager = datastore_manager

        # Mock image manager and get_timestamp_mod_time_from_dir()
        image_manager = MagicMock()
        image_manager.get_timestamp_mod_time_from_dir = MagicMock()
        image_manager.get_timestamp_mod_time_from_dir.side_effect = self._local_get_mod_time
        handler._hypervisor.image_manager = image_manager

        # Setup request
        request = GetInactiveImagesRequest()
        request.datastore_id = "DS_ID_1"

        # Test success
        image_scanner.get_state = MagicMock()
        image_scanner.get_state.return_value = DatastoreImageScanner.State.IDLE
        image_scanner.get_unused_images = MagicMock()
        image_scanner.get_unused_images.side_effect = self._local_get_unused_images
        image_monitor.get_image_scanner.return_value = image_scanner

        response = handler.get_inactive_images(request)

        assert_that(response.result is GetMonitoredImagesResultCode.OK)

        assert_that(response.totalMB == 10L)
        assert_that(response.usedMB == 6L)

        image_descriptors = response.image_descs
        assert_that(image_descriptors[0].image_id is "Image_Id_1")
        assert_that(image_descriptors[0].timestamp == 10001)
        assert_that(image_descriptors[1].image_id is "Image_Id_2")
        assert_that(image_descriptors[1].timestamp == 10002)
        assert_that(len(image_descriptors) is 2)

        # Test exception from get_timestamp_mod_time_from_dir
        image_manager.get_timestamp_mod_time_from_dir.side_effect = OSError
        response = handler.get_inactive_images(request)
        assert_that(response.result is GetMonitoredImagesResultCode.OK)

        assert_that(response.totalMB == 10L)
        assert_that(response.usedMB == 6L)

        image_descriptors = response.image_descs
        assert_that(image_descriptors[0].image_id is "Image_Id_1")
        assert_that(image_descriptors[0].timestamp == 0)
        assert_that(image_descriptors[1].image_id is "Image_Id_2")
        assert_that(image_descriptors[1].timestamp == 0)
        assert_that(len(image_descriptors) is 2)

        # Test operation in progress
        image_scanner.get_state.return_value = DatastoreImageSweeper.State.IMAGE_SWEEP
        response = handler.get_inactive_images(request)
        assert_that(response.result is GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS)

        # Test invalid datastore
        image_monitor.get_image_scanner.side_effect = DatastoreNotFoundException
        response = handler.get_inactive_images(request)
        assert_that(response.result is GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND)

    def _local_get_unused_images(self):
        unused_images = dict()
        unused_images["Image_Id_1"] = "/temp/image_id_1"
        unused_images["Image_Id_2"] = "/temp/image_id_2"
        return unused_images, 1001

    def _local_get_mod_time(self, dirname):
        if dirname == "/temp/image_id_1":
            return True, 10001
        if dirname == "/temp/image_id_2":
            return True, 10002
        return True, 10000

    def test_get_deleted_images(self):
        handler = HostHandler(MagicMock())

        image_monitor = MagicMock()
        image_monitor.get_image_sweeper = MagicMock()
        handler._hypervisor.image_monitor = image_monitor

        image_sweeper = MagicMock()

        # Setup request
        request = GetDeletedImagesRequest()
        request.datastore_id = "DS_ID_1"

        # Test success
        image_sweeper.get_state = MagicMock()
        image_sweeper.get_state.return_value = DatastoreImageSweeper.State.IDLE
        image_sweeper.get_deleted_images = MagicMock()
        image_sweeper.get_deleted_images.return_value = (["Image_Id_1", "Image_Id_2"], 1001)
        image_monitor.get_image_sweeper.return_value = image_sweeper

        response = handler.get_deleted_images(request)

        assert_that(response.result is GetMonitoredImagesResultCode.OK)
        assert_that(response.image_descs[0].image_id is "Image_Id_1")
        assert_that(response.image_descs[1].image_id is "Image_Id_2")
        assert_that(len(response.image_descs) is 2)

        # Test operation in progress
        image_sweeper.get_state.return_value = DatastoreImageSweeper.State.IMAGE_SWEEP
        image_sweeper.get_deleted_images.return_value = (["Image_Id_3", "Image_Id_4"], 1002)

        response = handler.get_deleted_images(request)

        assert_that(response.result is GetMonitoredImagesResultCode.OPERATION_IN_PROGRESS)
        assert_that(response.image_descs[0].image_id is "Image_Id_3")
        assert_that(response.image_descs[1].image_id is "Image_Id_4")
        assert_that(len(response.image_descs) is 2)

        # Test invalid datastore
        image_monitor.get_image_sweeper.side_effect = DatastoreNotFoundException
        response = handler.get_deleted_images(request)

        assert_that(response.result is GetMonitoredImagesResultCode.DATASTORE_NOT_FOUND)


if __name__ == '__main__':
    unittest.main()
