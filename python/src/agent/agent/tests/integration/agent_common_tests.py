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

"""Common agent tests which can be run against a local or remote agent"""

import logging
import time
import uuid

from gen.agent.ttypes import AgentStatusCode
from gen.agent.ttypes import PingRequest
from gen.agent.ttypes import ProvisionRequest
from gen.agent.ttypes import ProvisionResultCode
from gen.agent.ttypes import VersionRequest
from gen.agent.ttypes import VersionResultCode
from gen.common.ttypes import ServerAddress
from gen.flavors.ttypes import Flavor
from gen.flavors.ttypes import QuotaLineItem
from gen.flavors.ttypes import QuotaUnit
from gen.host import Host
from gen.host.ttypes import CopyImageResultCode
from gen.host.ttypes import CreateDiskResultCode
from gen.host.ttypes import DeleteDirectoryRequest
from gen.host.ttypes import DeleteDirectoryResultCode
from gen.host.ttypes import DeleteDiskResultCode
from gen.host.ttypes import GetHostModeRequest
from gen.host.ttypes import GetHostModeResultCode
from gen.host.ttypes import GetImagesResultCode
from gen.host.ttypes import GetResourcesRequest
from gen.host.ttypes import GetResourcesResultCode
from gen.host.ttypes import HostMode
from gen.host.ttypes import MksTicketRequest
from gen.host.ttypes import MksTicketResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from gen.host.ttypes import SetHostModeRequest
from gen.host.ttypes import SetHostModeResultCode
from gen.resource.ttypes import CloneType
from gen.resource.ttypes import Disk
from gen.resource.ttypes import DiskImage
from gen.resource.ttypes import DiskLocator
from gen.resource.ttypes import Image
from gen.resource.ttypes import Locator
from gen.resource.ttypes import NetworkType
from gen.resource.ttypes import Resource
from gen.resource.ttypes import State
from gen.resource.ttypes import Vm
from gen.resource.ttypes import VmLocator
from gen.scheduler.ttypes import PlaceRequest
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from hamcrest import *  # noqa
from hamcrest.library.text.stringmatches import matches_regexp  # hamcrest bug
from host.host_handler import HostHandler
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import IMAGE_FOLDER_NAME_PREFIX
from matchers import *  # noqa
from thrift.transport import TTransport

logger = logging.getLogger(__name__)

uuid_list = set()


def new_id():
    uuid_gen = str(uuid.uuid4())
    if uuid_gen in uuid_list:
        logger.error("UUID collision for uuid %s" % uuid_gen)
        raise AssertionError
    uuid_list.add(uuid_gen)
    return uuid_gen


def stable_uuid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, name))


def vm_resource():
    cost = [
        QuotaLineItem("vm.cpu", "1", QuotaUnit.COUNT),
        QuotaLineItem("vm.memory", "32", 2)]
    return Vm(id=new_id(),
              flavor="default",
              flavor_info=Flavor(name="default", cost=cost),
              state=State.STOPPED)


def rpc_call(method, request):
    logger.info("request  %s(%s)" % (method.__name__, request))
    response = method(request)
    logger.info("response %s" % response)
    return response


class VmWrapper(object):

    def __init__(self, client):
        self.host_client = client
        self._vm = self.vm_create()

    @property
    def id(self):
        return self._vm.id

    @property
    def flavor(self):
        return self._vm.flavor

    @property
    def vm(self):
        return self._vm

    def set_flavor(self, flavor):
        self._vm.flavor = flavor

    @staticmethod
    def vm_create():
        cost = [
            QuotaLineItem("vm.cpu", "1", QuotaUnit.COUNT),
            QuotaLineItem("vm.memory", "32", 2)]
        return Vm(id=new_id(),
                  flavor="default",
                  flavor_info=Flavor(name="default", cost=cost),
                  state=State.STOPPED,
                  tenant_id="t1",
                  project_id="p1")

    @staticmethod
    def create_request(res_id, env=None):
        return Host.CreateVmRequest(reservation=res_id,
                                    environment=env)

    def delete_request(self, disk_ids=None, force=False):
        return Host.DeleteVmRequest(vm_id=self.id,
                                    disk_ids=disk_ids,
                                    force=force)

    def power_request(self, op):
        return Host.PowerVmOpRequest(vm_id=self.id, op=op)

    def resource_request(self, disk=None, vm_disks=None, vm_constraints=[]):
        assert(disk is None or vm_disks is None)
        if disk is not None:
            return Resource(None, [disk])

        resource = Resource(self._vm, None)
        resource.vm.disks = vm_disks
        resource.vm.resource_constraints = vm_constraints

        return resource

    def power(self, op, expect=Host.PowerVmOpResultCode.OK):
        request = self.power_request(op)
        response = rpc_call(self.host_client.power_vm_op, request)
        assert_that(response.result, equal_to(expect))

    def get_vm(self, expect_found=True):
        request = GetResourcesRequest([Locator(vm=VmLocator(self.id))])
        response = rpc_call(self.host_client.get_resources, request)
        assert_that(response.result, equal_to(GetResourcesResultCode.OK))
        if expect_found:
            assert_that(len(response.resources), is_(1))
            return response.resources[0].vm
        else:
            assert_that(len(response.resources), is_(0))
            return None

    def place_and_reserve(self, disk=None, vm_disks=None, expect=PlaceResultCode.OK):
        place_response = self.place(disk, vm_disks, expect)
        return self.reserve(disk, vm_disks, place_response, expect)

    def place(self, disk=None, vm_disks=None, expect=PlaceResultCode.OK, vm_constraints=[]):
        resource = self.resource_request(disk, vm_disks, vm_constraints)
        response = rpc_call(self.host_client.place, PlaceRequest(resource))
        assert_that(response.result, equal_to(expect))
        return response

    def reserve(self, disk=None, vm_disks=None, place_response=PlaceResponse(), expect=Host.ReserveResultCode.OK):
        resource = self.resource_request(disk, vm_disks)
        resource.placement_list = place_response.placementList
        request = Host.ReserveRequest(resource, place_response.generation)
        response = rpc_call(self.host_client.reserve, request)
        assert_that(response.result, equal_to(expect))
        return response

    def create(self, expect=Host.CreateVmResultCode.OK, request=None):
        if request is None:
            res = self.place_and_reserve()
            request = self.create_request(res_id=res.reservation)
        response = rpc_call(self.host_client.create_vm, request)
        assert_that(response.result, equal_to(expect))
        return response

    def delete(self, expect=Host.DeleteVmResultCode.OK, request=None):
        if request is None:
            request = self.delete_request()
        response = rpc_call(self.host_client.delete_vm, request)
        assert_that(response.result, equal_to(expect))

    def create_disk(self, disk, res_id, expect=Host.CreateDisksResultCode.OK, validate=False):
        request = Host.CreateDisksRequest(reservation=res_id)
        response = rpc_call(self.host_client.create_disks, request)
        assert_that(response.result, equal_to(expect))

        if validate:
            assert_that(response.disk_errors, has_len(1))
            assert_that(response.disks, has_len(1))

            disk_error = response.disk_errors[disk.id]
            assert_that(disk_error, is_not(none()))
            assert_that(disk_error.result, is_(CreateDiskResultCode.OK))

            assert_that(disk.id, is_in([d.id for d in response.disks]))
            assert_that(disk.datastore.id, not_none())
            assert_that(disk.datastore.name, not_none())

        return response

    def create_image_from_vm(self, image_id, datastore, tmp_image_path,
                             expect=Host.CreateImageFromVmResultCode.OK):

        request = Host.CreateImageFromVmRequest(
            vm_id=self.id, image_id=image_id, tmp_image_path=tmp_image_path,
            datastore=datastore)

        response = rpc_call(self.host_client.create_image_from_vm, request)
        assert_that(response.result, equal_to(expect))
        return response

    def get_disk(self, disk_id, expect_found=True):
        request = GetResourcesRequest([Locator(disk=DiskLocator(disk_id))])
        response = rpc_call(self.host_client.get_resources, request)
        assert_that(response.result, equal_to(GetResourcesResultCode.OK))
        if expect_found:
            assert_that(len(response.resources), is_(1))
            return response.resources[0].disks[0]
        else:
            assert_that(len(response.resources), is_(0))
            return None

    def delete_disks(self, disk_ids,
                     expect=Host.DeleteDisksResultCode.OK, validate=False):
        request = Host.DeleteDisksRequest(disk_ids)
        response = rpc_call(self.host_client.delete_disks, request)
        assert_that(response.result, equal_to(expect))
        if validate:
            assert_that(response.disk_errors, has_len(len(disk_ids)))

            for disk_id in disk_ids:
                disk_error = response.disk_errors[disk_id]
                assert_that(disk_error, is_not(none()))
                assert_that(disk_error.result, is_(DeleteDiskResultCode.OK))

        return response

    def attach_disks(self, vm_id, disk_ids,
                     expect=Host.VmDiskOpResultCode.OK):
        request = Host.VmDisksAttachRequest(vm_id, disk_ids)
        response = rpc_call(self.host_client.attach_disks, request)
        assert_that(response.result, equal_to(expect))
        return response

    def detach_disks(self, vm_id, disk_ids,
                     expect=Host.VmDiskOpResultCode.OK):
        request = Host.VmDisksAttachRequest(vm_id, disk_ids)
        response = rpc_call(self.host_client.detach_disks, request)
        assert_that(response.result, equal_to(expect))
        return response

    def get_network(self, vm_id, expect=Host.GetVmNetworkResultCode.OK):
        request = Host.GetVmNetworkRequest(vm_id)
        response = rpc_call(self.host_client.get_vm_networks, request)
        assert_that(response.result, equal_to(expect))
        return response

    def attach_iso(self, vm_id, iso_path, expect=Host.AttachISOResultCode.OK):
        request = Host.AttachISORequest(vm_id, iso_path)
        response = rpc_call(self.host_client.attach_iso, request)
        assert_that(response.result, equal_to(expect))
        return response

    def detach_iso(self, vm_id, delete, expect=Host.DetachISOResultCode.OK):
        request = Host.DetachISORequest(vm_id, delete)
        response = rpc_call(self.host_client.detach_iso, request)
        assert_that(response.result, equal_to(expect))
        return response


class AgentCommonTests(object):

    REGEX_TIME = "^\d{4}\-\d{2}\-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)*$"

    DEFAULT_DISK_FLAVOR = Flavor("default", [])

    def set_host_mode(self, mode):
        response = self.host_client.set_host_mode(SetHostModeRequest(mode))
        assert_that(response.result, equal_to(SetHostModeResultCode.OK))

    def _validate_datastore_id(self, datastore_id):
        """ Check if id is in one of the expected formats. """

        # standard uuids are 36 chars
        # vmfs volume uuids are 35 chars
        # nfs volume uuids are 17 chars
        assert_that(datastore_id, matches_regexp("^[0-9a-f-]+$"))
        assert_that(len(datastore_id),
                    any_of(equal_to(17), equal_to(35), equal_to(36)))

    def _delete_image(self, image, result_code=DeleteDirectoryResultCode.OK):
        resp = self.host_client.delete_directory(
            DeleteDirectoryRequest(image.datastore.id, compond_path_join(IMAGE_FOLDER_NAME_PREFIX, image.id)))
        assert_that(resp.result, is_(result_code))

    def test_get_nfc_ticket(self):
        request = ServiceTicketRequest(
            service_type=ServiceType.NFC,
            datastore_name=self.get_image_datastore())
        response = self.host_client.get_service_ticket(request)
        assert_that(response.result, is_(ServiceTicketResultCode.OK))

        ticket = response.ticket
        assert_that(ticket, not_none())
        assert_that(ticket.port, is_(902))
        assert_that(ticket.service_type, is_("nfc"))
        assert_that(ticket.session_id, not_none())
        assert_that(ticket.ssl_thumbprint, not_none())

        # TODO(agui): try ticket with nfc client

    def test_get_vim_ticket(self):
        request = ServiceTicketRequest(ServiceType.VIM)
        response = self.host_client.get_service_ticket(request)
        assert_that(response.result, is_(ServiceTicketResultCode.OK))

    def test_get_service_ticket_not_found(self):
        request = ServiceTicketRequest(ServiceType.NFC, 'not_existed')
        response = self.host_client.get_service_ticket(request)
        assert_that(response.result, is_(ServiceTicketResultCode.NOT_FOUND))

    def test_get_mks_ticket(self):
        vm = VmWrapper(self.host_client)

        # Before VM is created, result should be VM_NOT_FOUND
        request = MksTicketRequest(vm.id)
        response = self.host_client.get_mks_ticket(request)
        assert_that(response.result, is_(MksTicketResultCode.VM_NOT_FOUND))

        # After VM created, but before VM is powered on, result should be
        # INVALID_VM_POWER_STATE
        vm.create()
        response = self.host_client.get_mks_ticket(request)
        assert_that(response.result, is_(
            MksTicketResultCode.INVALID_VM_POWER_STATE))

        # After Vm powered on, result should be OK
        vm.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        response = self.host_client.get_mks_ticket(request)
        assert_that(response.result, is_(MksTicketResultCode.OK))
        assert_that(response.ticket.ticket, not_none())
        assert_that(response.ticket.cfg_file, not_none())

        vm.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)
        vm.delete()

    def test_create_delete_vm(self):
        """Test that the agent can create and delete a VM."""
        vm = VmWrapper(self.host_client)

        vm.get_vm(False)
        vm.create()

        # VM already exists
        vm.get_vm(True)
        vm.create(expect=Host.CreateVmResultCode.VM_ALREADY_EXIST)

        # VM in wrong state
        vm.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        vm.delete(expect=Host.DeleteVmResultCode.VM_NOT_POWERED_OFF)
        vm.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)

        vm.delete()

        # VM no longer exists
        vm.delete(expect=Host.DeleteVmResultCode.VM_NOT_FOUND)

    def test_continuous_create_delete_vm(self):
        vm = VmWrapper(self.host_client)
        for i in xrange(10):
            vm.create()
            vm.delete()

    def _test_create_vm_with_ephemeral_disks(self, image_id, concurrent=False,
                                             new_client=False):
        if new_client:
            client = self.create_client()
        else:
            client = self.host_client
        vm_wrapper = VmWrapper(client)

        image = DiskImage(image_id, CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR)
        ]

        reservation = vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        response = vm_wrapper.create(request=request)
        assert_that(response.vm, not_none())
        assert_that(response.vm.datastore, not_none())
        assert_that(response.vm.datastore.id, not_none())
        assert_that(response.vm.datastore.name, not_none())

        rc = Host.PowerVmOpResultCode
        op = Host.PowerVmOp

        vm_wrapper.power(op.ON, rc.OK)
        vm_wrapper.power(op.OFF, rc.OK)

        vm_wrapper.delete(request=vm_wrapper.delete_request(disk_ids=[]))

        # This is to test the image doesn't go away with the delete request,
        # Otherwise this create vm request will fail.
        if not concurrent:
            # create the VM with new VM_ID
            vm_wrapper = VmWrapper(client)
            reservation = vm_wrapper.place_and_reserve(vm_disks=disks).reservation
            request = vm_wrapper.create_request(res_id=reservation)
            vm_wrapper.create(request=request)
            vm_wrapper.delete(request=vm_wrapper.delete_request(disk_ids=[]))

    def test_batch_get_resources(self):
        """Test that the agent can return resources in batch."""
        vms = []
        for _ in xrange(2):
            vm = VmWrapper(self.host_client)
            image = DiskImage("ttylinux", CloneType.COPY_ON_WRITE)

            disks = [
                Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                     image=image, capacity_gb=0,
                     flavor_info=self.DEFAULT_DISK_FLAVOR)
            ]

            reservation = vm.place_and_reserve(vm_disks=disks).reservation

            request = vm.create_request(reservation)
            vm.create(request=request)
            vms.append((vm, disks))

        request = GetResourcesRequest()
        response = rpc_call(self.host_client.get_resources, request)
        assert_that(response.result, is_(GetResourcesResultCode.OK))

        assert_that(response.resources, has_len(2))
        resources = {}
        for resource in response.resources:
            resources[resource.vm.id] = resource

        for vm, disks in vms:
            assert_that(resources, has_key(vm.id))
            vm.delete(request=vm.delete_request())

    def test_create_delete_disks(self):
        """Test that the agent can create and delete disks."""
        vm_wrapper = VmWrapper(self.host_client)

        rc = Host.CreateDisksResultCode

        disk_1 = new_id()
        disk_2 = new_id()

        disks = [
            Disk(disk_1, self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(disk_2, self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]

        # Invalid reservation id
        vm_wrapper.create_disk(disks[0], new_id(), expect=rc.INVALID_RESERVATION)

        # Valid disk flavor
        for disk in disks:
            reservation = vm_wrapper.place_and_reserve(disk=disk).reservation
            vm_wrapper.create_disk(disk, reservation, validate=True)

        # Delete 2 disks: 1 - valid, 2 - valid
        disk_ids = [disk_1, disk_2]
        vm_wrapper.delete_disks(disk_ids, validate=True)

        # Delete 2 disks: 1 - invalid (deleted), 2 - invalid (deleted)
        response = vm_wrapper.delete_disks(disk_ids)
        assert_that(len(response.disk_errors), equal_to(2))
        assert_that(response.disk_errors, has_key(disk_1))
        assert_that(response.disk_errors[disk_1].result,
                    is_(DeleteDiskResultCode.DISK_NOT_FOUND))
        assert_that(response.disk_errors, has_key(disk_2))
        assert_that(response.disk_errors[disk_2].result,
                    is_(DeleteDiskResultCode.DISK_NOT_FOUND))

    def test_create_disk_from_image(self):
        """Test that the agent can clone images"""
        vm = VmWrapper(self.host_client)

        tests = [
            {"im": DiskImage("invalid", CloneType.FULL_COPY), "rc": CreateDiskResultCode.SYSTEM_ERROR},
            {"im": None, "rc": CreateDiskResultCode.OK},
            {"im": DiskImage("ttylinux", CloneType.FULL_COPY), "rc": CreateDiskResultCode.OK},
            {"im": DiskImage("ttylinux", CloneType.COPY_ON_WRITE), "rc": CreateDiskResultCode.SYSTEM_ERROR},
        ]

        for test in tests:
            disk = Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                        image=test["im"], flavor_info=self.DEFAULT_DISK_FLAVOR)
            if disk.image is None:
                disk.capacity_gb = 1
            else:
                disk.capacity_gb = 0

            reservation = vm.place_and_reserve(disk=disk).reservation

            response = vm.create_disk(disk, reservation)

            if test["rc"] == CreateDiskResultCode.OK:
                assert_that(response.disk_errors, has_len(1))
                assert_that(response.disk_errors, has_key(disk.id))
                assert_that(response.disk_errors[disk.id].result,
                            is_(test["rc"]))

                response = vm.delete_disks([disk.id])
                assert_that(response.disk_errors, has_len(1))
                assert_that(response.disk_errors, has_key(disk.id))
                assert_that(response.disk_errors[disk.id].result,
                            is_(DeleteDiskResultCode.OK))
            else:
                assert_that(response.disk_errors, has_len(1))
                assert_that(response.disk_errors, has_key(disk.id))
                disk_error = response.disk_errors[disk.id]
                assert_that(disk_error.result, is_(test["rc"]))

    def test_power_ops(self):
        """Test that the agent can invoke power ops on a VM."""
        vm = VmWrapper(self.host_client)

        vm.create()

        rc = Host.PowerVmOpResultCode
        op = Host.PowerVmOp

        tests = [
            {"op": op.OFF,     "rc": rc.OK},
            {"op": op.RESET,   "rc": rc.INVALID_VM_POWER_STATE},
            {"op": op.ON,      "rc": rc.OK},
            {"op": op.ON,      "rc": rc.OK},
            {"op": op.SUSPEND, "rc": rc.OK},
            {"op": op.SUSPEND, "rc": rc.OK},
            {"op": op.RESUME,  "rc": rc.OK},
            {"op": op.RESUME,  "rc": rc.OK},
            {"op": op.RESET,   "rc": rc.OK},
            {"op": op.OFF,     "rc": rc.OK},
            {"op": 1024,       "rc": rc.SYSTEM_ERROR},
        ]

        for test in tests:
            vm.power(test["op"], expect=test["rc"])

        vm.delete()

        # vm has been deleted, all power ops should result in VM_NOT_FOUND
        for power_op in [op.ON, op.OFF, op.RESET, op.SUSPEND, op.RESUME]:
            vm.power(power_op, expect=rc.VM_NOT_FOUND)

    def _find_configured_datastore_in_host_config(self):
        config_request = Host.GetConfigRequest()
        config_response = self.host_client.get_host_config(config_request)
        assert_that(config_response.hostConfig.datastores, not_none())
        assert_that(config_response.hostConfig.datastores, is_not(empty()))
        logger.debug("Configured test image datastore %s" %
                     self.get_image_datastore())
        for datastore_item in config_response.hostConfig.datastores:
            if datastore_item.name == self.get_image_datastore():
                return datastore_item
        self.fail("datastore list returned by agent does not contain %s" %
                  self.get_image_datastore())

    def test_copy_image_not_found(self):
        datastore = self._find_configured_datastore_in_host_config()
        src_image = Image("not-found-source-id", datastore)
        dst_image = Image("destination-id", datastore)

        request = Host.CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)

        assert_that(response.result, is_(CopyImageResultCode.IMAGE_NOT_FOUND))

    def test_copy_image_get_images(self):
        datastore = self._find_configured_datastore_in_host_config()
        assert_that(datastore, not_none())

        # ttylinux is the default image that is copied to datastore1
        # when agent starts
        src_image = Image("ttylinux", datastore)
        dst_image = Image("test-copy-image", datastore)

        # verify test-copy-image is not in datastore
        request = Host.GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item("ttylinux"))
        assert_that(response.image_ids, not(has_item("test-copy-image")))
        image_number = len(response.image_ids)

        # Copy image
        request = Host.CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)
        assert_that(response.result, is_(CopyImageResultCode.OK))

        # Copy image the second time should return DESTINATION_ALREADY_EXIST
        request = Host.CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)
        assert_that(response.result,
                    is_(CopyImageResultCode.DESTINATION_ALREADY_EXIST))

        # Verify test-copy-image is in datastore
        request = Host.GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item("ttylinux"))
        assert_that(response.image_ids, has_item("test-copy-image"))
        assert_that(response.image_ids, has_length(image_number + 1))

        # Create VM
        image = DiskImage("test-copy-image", CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]
        vm_wrapper = VmWrapper(self.host_client)
        reservation = vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        response = vm_wrapper.create(request=request)
        assert_that(response.vm, not_none())

        # Delete VM
        vm_wrapper.delete(request=vm_wrapper.delete_request(disk_ids=[]))

        # Verify test-copy-image is in datastore
        request = Host.GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item("ttylinux"))
        assert_that(response.image_ids, has_item("test-copy-image"))
        assert_that(response.image_ids, has_length(image_number + 1))

        # Copy image using datastore with name as id should succeed
        # This datastore object uses the datastore name as its id
        datastore.id = datastore.name
        dst_image2 = Image("test-copy-image2", datastore)
        request = Host.CopyImageRequest(src_image, dst_image2)
        response = self.host_client.copy_image(request)
        assert_that(response.result, is_(CopyImageResultCode.OK))

        # Clean destination image
        self._delete_image(dst_image)
        self._delete_image(dst_image2)

    def test_get_images_datastore_not_found(self):
        request = Host.GetImagesRequest("datastore_not_there")
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(
            GetImagesResultCode.DATASTORE_NOT_FOUND))

    def test_invalid_reservation(self):
        """Test that the agent rejects invalid reservations."""

        vm = VmWrapper(self.host_client)
        rc = Host.CreateVmResultCode.INVALID_RESERVATION

        # invalid reservation id
        request = vm.create_request(res_id="42")

        vm.create(request=request, expect=rc)

        # invalid flavor
        vm.set_flavor("vanilla")

        vm.create(request=request, expect=rc)

    def test_stale_generation(self):
        """Test that the agent rejects stale generations."""
        vm = VmWrapper(self.host_client)
        place_response = vm.place()
        for i in range(HostHandler.GENERATION_GAP + 1):
            logger.debug("reserve #%d" % i)
            vm.place_and_reserve()
        vm.reserve(place_response=place_response,
                   expect=Host.ReserveResultCode.STALE_GENERATION)

    def test_get_host_config(self):
        """Test that the agent responds with Host configuration"""
        request = Host.GetConfigRequest()
        response = self.host_client.get_host_config(request)
        assert_that(response.hostConfig.agent_id,
                    matches_regexp("[0-9a-f-]{36}"))
        datastores = response.hostConfig.datastores
        assert_that(datastores, has_length(len(self.get_all_datastores())))
        for datastore in datastores:
            assert_that(self.get_all_datastores(), has_item(datastore.name))
            assert_that(datastore.type, not_none())
        self._validate_datastore_id(datastores[0].id)
        assert_that(response.hostConfig.networks, not_none())
        vm_networks = [network for network in response.hostConfig.networks
                       if NetworkType.VM in network.types]
        assert_that(len(vm_networks), greater_than_or_equal_to(1))

    def test_get_mode(self):
        response = self.host_client.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        # Disable this test because the host could go to other mode. Will
        # enable it after exit_maintenance is implemented
        assert_that(response.mode, equal_to(HostMode.NORMAL))

    def test_enter_maintenance(self):
        vm_wrapper = VmWrapper(self.host_client)

        # Enter entering-maintenance
        self.set_host_mode(HostMode.ENTERING_MAINTENANCE)

        # Check mode. It should be ENTERING_MAINTENANCE.
        response = self.host_client.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        assert_that(response.mode, equal_to(HostMode.ENTERING_MAINTENANCE))

        # Test place. It should return INVALID_STATE.
        vm_wrapper.place(expect=PlaceResultCode.INVALID_STATE)

        # Enter maintenance
        self.set_host_mode(HostMode.MAINTENANCE)

        # Check mode. It should be MAINTENANCE.
        response = self.host_client.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        assert_that(response.mode, equal_to(HostMode.MAINTENANCE))

        # Test place. It should return INVALID_STATE.
        vm_wrapper.place(expect=PlaceResultCode.INVALID_STATE)

        # Exit maintenance
        self.set_host_mode(HostMode.NORMAL)

        # Check mode. It should be NORMAL.
        response = self.host_client.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        assert_that(response.mode, equal_to(HostMode.NORMAL))

        # Test place. Should be OK.
        vm_wrapper.place(expect=PlaceResultCode.OK)

    def test_get_resources_not_found(self):
        for locator in [Locator(vm=VmLocator("1234567")),
                        Locator(disk=DiskLocator("891"))]:
            request = GetResourcesRequest([locator])
            response = rpc_call(self.host_client.get_resources, request)
            assert_that(response.result, is_(GetResourcesResultCode.OK))
            assert_that(response.resources, is_([]))

    def test_default_network(self):
        vm_wrapper = VmWrapper(self.host_client)
        image = DiskImage("ttylinux", CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image, capacity_gb=1,
                 flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]

        # create disk
        reservation = vm_wrapper.place_and_reserve(vm_disks=disks).reservation

        # create vm without network info specified
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id
        result = vm_wrapper.get_network(vm_id=vm_id)
        assert_that(len(result.network_info), is_(1))
        assert_that(result.network_info[0].network == "VM Network" or
                    result.network_info[0].network == "Network-0000")

        # delete the disk and the vm
        vm_wrapper.delete(request=vm_wrapper.delete_request())

    def test_attach_detach_disks(self):
        vm_wrapper = VmWrapper(self.host_client)

        # create a vm without disk
        reservation = vm_wrapper.place_and_reserve().reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        # create 3 disks
        image = DiskImage("ttylinux", CloneType.FULL_COPY)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR)
        ]

        for disk in disks:
            reservation = vm_wrapper.place_and_reserve(disk=disk).reservation
            vm_wrapper.create_disk(disk, reservation, validate=True)

        # attach disks
        disk_ids = [disk.id for disk in disks]
        vm_wrapper.attach_disks(vm_id, disk_ids)

        # delete vm and disks
        vm_wrapper.delete(request=vm_wrapper.delete_request(),
                          expect=Host.DeleteVmResultCode.OPERATION_NOT_ALLOWED)
        vm_wrapper.detach_disks(vm_id, disk_ids)
        vm_wrapper.delete(request=vm_wrapper.delete_request())
        vm_wrapper.delete_disks([disk.id for disk in disks], validate=True)

    def test_attach_disks_vm_suspended(self):
        vm = VmWrapper(self.host_client)
        vm_id = vm.create().vm.id
        vm.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        vm.power(Host.PowerVmOp.SUSPEND, Host.PowerVmOpResultCode.OK)

        vm.attach_disks(vm_id, ["disk-1"],
                        expect=Host.VmDiskOpResultCode.INVALID_VM_POWER_STATE)
        vm.power(Host.PowerVmOp.RESUME, Host.PowerVmOpResultCode.OK)
        vm.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)
        vm.delete()

    def test_detach_disks_vm_suspended(self):
        vm = VmWrapper(self.host_client)
        vm_id = vm.create().vm.id
        vm.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        vm.power(Host.PowerVmOp.SUSPEND, Host.PowerVmOpResultCode.OK)

        vm.detach_disks(vm_id, ["disk-1"],
                        expect=Host.VmDiskOpResultCode.INVALID_VM_POWER_STATE)
        vm.power(Host.PowerVmOp.RESUME, Host.PowerVmOpResultCode.OK)
        vm.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)

        vm.delete()

    def test_create_vm_with_ephemeral_disks(self):
        self._test_create_vm_with_ephemeral_disks("ttylinux")

    def _update_agent_config(self):
        """
        Update the agent config using the bootstrap interface.
        Verifies that thrift requests after a config update return back
        SYSTEM_ERROR and the agent is stopped.
        :return the request we used to configure the host with.
        """
        req = ProvisionRequest()
        req.datastores = ["bootstrap_datastore"]
        req.networks = ["Bootstrap Network"]
        addr = ServerAddress(host="foobar", port=8835)
        req.address = addr
        req.memory_overcommit = 2.0
        res = self.control_client.provision(req)
        self.assertEqual(res.result, ProvisionResultCode.OK)

        # Now check that the agent went down and all requests fail until the
        # agent restarts.
        # Use a time greater than the restart poll thread.
        remaining_sleep_time = 15
        sleep_time = 0.5
        agent_restarted = False
        while 0 < remaining_sleep_time:
            try:
                res = self.control_client.get_agent_status()
                # Verify that the response is restarting
                self.assertEqual(res.status, AgentStatusCode.RESTARTING)
                time.sleep(sleep_time)
                remaining_sleep_time -= sleep_time
            except TTransport.TTransportException:
                agent_restarted = True
                break
            except:
                logger.debug("Caught exception, assuming restart: ",
                             exc_info=True)
                agent_restarted = True
                break
        self.assertTrue(agent_restarted)
        return req

    def _update_agent_invalid_config(self):
        """
        Negative test case to update the agent with an invalid config
        """
        req = ProvisionRequest()
        req.datastores = ["bootstrap_datastore"]
        req.networks = ["Bootstrap Network"]
        addr = ServerAddress(host="foobar", port=8835)
        req.address = addr
        req.memory_overcommit = 0.5
        res = self.control_client.provision(req)
        self.assertEqual(res.result, ProvisionResultCode.INVALID_CONFIG)

    def _validate_post_boostrap_config(self, req):
        """
        Validates that the post boostrap config is the same as the one we had
        requested for.
        """
        host_config_request = Host.GetConfigRequest()
        res = self.host_client.get_host_config(host_config_request)
        # XXX Fix me the host config should return more useful info
        datastores = req.datastores
        self.assertEqual([ds.name for ds in res.hostConfig.datastores], datastores)

    def test_ping(self):
        """ Test ping against the control service """
        # Test runs against both real and fake hosts
        ping_req = PingRequest()
        self.control_client.ping(ping_req)

    def test_version(self):
        req = VersionRequest()
        response = self.control_client.get_version(req)
        assert_that(response.result, equal_to(VersionResultCode.OK))
        assert_that(response.version, not_none())
        assert_that(response.revision, not_none())

    def get_image_datastore(self):
        return self.get_all_datastores()[0]

    def get_all_datastores(self):
        """Get the all datastore names configured in the tested agent
        :return: list of str, datastore name
        """
        assert_that(self._datastores, not_none())
        assert_that(self._datastores, is_not(empty()))
        return self._datastores

    def test_get_network_mac_address(self):
        vm_wrapper = VmWrapper(self.host_client)

        # create a vm
        reservation = vm_wrapper.place_and_reserve().reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id
        result = vm_wrapper.get_network(vm_id=vm_id)
        assert_that(len(result.network_info), is_(1))

        # power on the vm
        vm_wrapper.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        result = vm_wrapper.get_network(vm_id=vm_id)
        assert_that(len(result.network_info), is_(1))
        self.assertTrue(result.network_info[0].mac_address)

        # delete the vm
        vm_wrapper.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)
        vm_wrapper.delete()
