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

"""Agent integration tests via (remote) ESX hypervisor."""

import logging
import threading
import time
import unittest
import uuid

from agent.tests.common_helper_functions import RuntimeUtils
from common.photon_thrift.direct_client import DirectClient
from gen.agent import AgentControl
from gen.agent.ttypes import AgentStatusCode
from gen.agent.ttypes import ProvisionRequest
from gen.agent.ttypes import ProvisionResultCode
from gen.common.ttypes import ServerAddress
from gen.host import Host
from gen.host.ttypes import CopyImageResultCode
from gen.host.ttypes import FinalizeImageRequest
from gen.host.ttypes import FinalizeImageResultCode
from gen.host.ttypes import DeleteDirectoryRequest
from gen.host.ttypes import DeleteDirectoryResultCode
from gen.host.ttypes import DeleteVmResultCode
from gen.host.ttypes import GetConfigResultCode
from gen.host.ttypes import GetDatastoresRequest
from gen.host.ttypes import GetHostModeRequest
from gen.host.ttypes import GetHostModeResultCode
from gen.host.ttypes import GetImagesResultCode
from gen.host.ttypes import GetInactiveImagesRequest
from gen.host.ttypes import GetMonitoredImagesResultCode
from gen.host.ttypes import GetNetworksRequest
from gen.host.ttypes import GetResourcesRequest
from gen.host.ttypes import GetResourcesResultCode
from gen.host.ttypes import HostMode
from gen.host.ttypes import PowerVmOpResultCode
from gen.host.ttypes import ServiceTicketRequest
from gen.host.ttypes import ServiceTicketResultCode
from gen.host.ttypes import ServiceType
from gen.host.ttypes import StartImageOperationResultCode
from gen.host.ttypes import StartImageScanRequest
from gen.host.ttypes import StartImageSweepRequest
from gen.host.ttypes import TransferImageRequest
from gen.host.ttypes import TransferImageResultCode
from gen.resource.constants import LOCAL_VMFS_TAG
from gen.resource.constants import NFS_TAG
from gen.resource.constants import SHARED_VMFS_TAG
from gen.resource.ttypes import CloneType
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import Disk
from gen.resource.ttypes import DiskImage
from gen.resource.ttypes import Image
from gen.resource.ttypes import ImageDatastore
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType
from gen.scheduler.ttypes import PlaceResultCode
from hamcrest import assert_that
from hamcrest import equal_to
from hamcrest import has_item
from hamcrest import has_length
from hamcrest import is_
from hamcrest import is_in
from hamcrest import not_none
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX, datastore_path
from host.hypervisor.esx.vm_config import vmdk_path
from host.hypervisor.esx.vm_manager import EsxVmManager
from nose.plugins.skip import SkipTest
from pyVmomi import SoapStubAdapter, vim
from pysdk import connect
from pysdk import host
from pysdk import task
from thrift.transport import TTransport

from agent_common_tests import AgentCommonTests
from agent_common_tests import VmWrapper
from agent_common_tests import new_id
from agent_common_tests import rpc_call

logger = logging.getLogger(__name__)


class TestRemoteAgent(unittest.TestCase, AgentCommonTests):
    def shortDescription(self):
        return None

    def get_service_instance(self):
        # create a connection to hostd.
        request = ServiceTicketRequest(ServiceType.VIM)
        response = self.host_client.get_service_ticket(request)
        self.assertEqual(response.result, ServiceTicketResultCode.OK)

        hostd_port = 443
        vim_namespace = "vim25/5.0"
        stub = SoapStubAdapter(self.server, hostd_port, vim_namespace)
        si = vim.ServiceInstance("ServiceInstance", stub)
        si.RetrieveContent().sessionManager.CloneSession(response.vim_ticket)
        connect.SetSi(si)
        return si

    def connect_client(self, service, cls, server):
        """ Utility method to connect to a remote agent """
        max_sleep_time = 32
        sleep_time = 0.1
        while sleep_time < max_sleep_time:
            try:
                client = DirectClient(service, cls, server, 8835)
                client.connect()
                return client
            except TTransport.TTransportException:
                time.sleep(sleep_time)
                sleep_time *= 2
        self.fail("Cannot connect to agent %s" % server)

    def create_client(self):
        return self.connect_client("Host", Host.Client, self.server)

    def client_connections(self):
        self.host_client = self.create_client()
        self.control_client = self.connect_client("AgentControl",
                                                  AgentControl.Client,
                                                  self.server)

    def provision_hosts(self, mem_overcommit=2.0,
                        vm_networks=None, datastores=None, used_for_vms=True,
                        image_ds=None, host_id=None,
                        deployment_id="test-deployment"):
        """ Provisions the agents on the remote hosts """
        if datastores is None:
            datastores = self.get_all_datastores()
            image_datastore = self.get_image_datastore()
        elif image_ds:
            image_datastore = image_ds
        else:
            image_datastore = datastores[0]

        req = ProvisionRequest()
        req.datastores = datastores
        if vm_networks is None:
            vm_networks = [self._vm_network]
        req.networks = vm_networks
        req.address = ServerAddress(host=self.server, port=8835)
        req.memory_overcommit = mem_overcommit
        req.image_datastore_info = ImageDatastore(
            name=image_datastore,
            used_for_vms=used_for_vms)
        req.image_datastores = set([req.image_datastore_info])
        req.management_only = True
        if host_id:
            req.host_id = host_id
        else:
            req.host_id = self.host_id

        if deployment_id:
            req.deployment_id = deployment_id
        else:
            req.deployment_id = self.deployment_id

        res = self.control_client.provision(req)

        # This will trigger a restart if the agent config changes, which
        # will happen the first time provision_hosts is called.
        self.assertEqual(res.result, ProvisionResultCode.OK)

        # Wait the agent to shutdown
        time.sleep(1)

        count = 0
        while count < 15:
            try:
                res = self.control_client.get_agent_status()
                if res.status == AgentStatusCode.OK:
                    # Agent is up
                    return
            except:
                logger.exception("Can't connect to agent")
            # Sleep 15 s for the agent to reboot.
            count += 1
            time.sleep(1)
            # Reconnect the clients
            self._close_agent_connections()
            self.client_connections()
        self.fail("Cannot connect to agent %s after provisioning" %
                  self.server)
        return host_id

    def setUp(self):
        from testconfig import config
        if "agent_remote_test" not in config:
            raise SkipTest()

        self.runtime = RuntimeUtils()

        # Set the default netork name and datastore name
        self._vm_network = "VM Network"
        self._datastores = None

        if "vm_network" in config["agent_remote_test"]:
            self._vm_network = config["agent_remote_test"]["vm_network"]

        if "datastores" in config["agent_remote_test"]:
            datastores = config["agent_remote_test"]["datastores"]
            self._datastores = [d.strip() for d in datastores.split(",")]
        else:
            self.fail("datastores not provided for test setUp")

        # Optionally update the specification of a remote iso file. The file
        # needs to exist on the remote esx server for this test to succeed.
        self._remote_iso_file = None
        self._second_remote_iso_file = None
        if ("iso_file" in config["agent_remote_test"]):
            self._remote_iso_file = \
                config["agent_remote_test"]["iso_file"]

        if ("second_iso_file" in config["agent_remote_test"]):
            self._second_remote_iso_file = \
                config["agent_remote_test"]["second_iso_file"]

        server = config["agent_remote_test"]["server"]
        self.server = server

        self.generation = int(time.time())

        # Connect to server and configure vim_client
        self.client_connections()
        self.vim_client = VimClient()
        self.vim_client.connect_ticket(self.server, self._get_vim_ticket())
        connect.SetSi(self.vim_client._si)

        # Set host mode to normal
        self.set_host_mode(HostMode.NORMAL)

        # The first time setup is called the agent will restart.
        self.provision_hosts()
        # Reconnect to account for the restart
        self.client_connections()
        self.clear()

    @classmethod
    def setUpClass(cls):
        cls.host_id = str(uuid.uuid4())
        cls.deployment_id = "test-deployment"

    def _close_agent_connections(self):
        self.host_client.close()
        self.control_client.close()

    def tearDown(self):
        self.runtime.cleanup()
        self._close_agent_connections()
        self.vim_client.disconnect()

    def vim_delete_vm(self, vm_id):
        """ Delete a VM using the vim client """
        try:
            vim_client = VimClient()
            vim_client.connect_ticket(self.server, self._get_vim_ticket())
            vim_vm = vim_client.get_vm(vm_id)
            if vim_vm.runtime.powerState != 'poweredOff':
                try:
                    vim_task = vim_vm.PowerOff()
                    vim_client.wait_for_task(vim_task)
                except:
                    logger.info("Cannot power off vm", exc_info=True)
            vim_task = vim_vm.Destroy()
            vim_client.wait_for_task(vim_task)
        finally:
            if vim_client:
                vim_client.disconnect()

    def clear(self):
        """Remove all the VMs, disks and images """
        request = GetResourcesRequest()
        response = rpc_call(self.host_client.get_resources, request)
        assert_that(response.result, is_(GetResourcesResultCode.OK))
        for resource in response.resources:
            delete_request = Host.DeleteVmRequest(vm_id=resource.vm.id,
                                                  force=True)
            response = rpc_call(self.host_client.delete_vm, delete_request)

            if response.result == DeleteVmResultCode.VM_NOT_POWERED_OFF:
                poweroff_request = Host.PowerVmOpRequest(vm_id=resource.vm.id,
                                                         op=Host.PowerVmOp.OFF)
                response = rpc_call(self.host_client.power_vm_op,
                                    poweroff_request)
                assert_that(response.result, is_(PowerVmOpResultCode.OK))
                response = rpc_call(self.host_client.delete_vm, delete_request)

            if response.result != DeleteVmResultCode.OK:
                logger.info("Cannot delete vm %s trying vim_client"
                            % resource.vm.id)
                self.vim_delete_vm(resource.vm.id)
        self.clean_images()

    def clean_images(self):
        """ Clean up images if there are any """
        datastore = self._find_configured_datastore_in_host_config()
        request = Host.GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        if response.result == GetImagesResultCode.OK:
            for image_id in response.image_ids:
                if image_id == "ttylinux":
                    continue  # To be removed when we remove ttylinux.
                logging.info("Cleaning up stray image %s " % image_id)
                self._delete_image(Image(image_id, datastore))
        else:
            logger.warning("Failed to obtain the list of images to cleanup")

    def test_send_image_to_host(self):
        image_id = new_id() + "_test_xfer_image"
        image_id_2 = "%s_xfered" % image_id

        dst_image, _ = self._create_test_image(image_id)

        datastore = self._find_configured_datastore_in_host_config()
        transfer_image_request = TransferImageRequest(
            source_image_id=image_id,
            source_datastore_id=datastore.id,
            destination_host=ServerAddress(host="localhost", port=8835),
            destination_datastore_id=datastore.id,
            destination_image_id=image_id_2)
        res = self.host_client.transfer_image(transfer_image_request)
        self.assertEqual(res.result, TransferImageResultCode.OK)

        # clean up images created in test
        self._delete_image(dst_image)
        xfered_image = Image(image_id_2, datastore)
        self._delete_image(xfered_image)

    def test_host_config_after_provision(self):
        """
        Test if the agent returns the correct HostConfig
        after being provisioned
        """
        host_config_request = Host.GetConfigRequest()
        res = self.host_client.get_host_config(host_config_request)
        self.assertEqual(res.result, GetConfigResultCode.OK)

        hostConfig = res.hostConfig
        datastores = [ds.name for ds in hostConfig.datastores]
        containsDs = [ds for ds in self.get_all_datastores()
                      if ds in datastores]
        self.assertEqual(containsDs, self.get_all_datastores())
        networks = [net.id for net in hostConfig.networks]
        self.assertTrue(self._vm_network in networks)
        self.assertEqual(hostConfig.address, ServerAddress(host=self.server,
                                                           port=8835))
        self.assertTrue(hostConfig.management_only)
        # get_host_config reports datastore id for image datastore  even if it
        # was provisioned with a datastore name.
        image_datastore_name = self.get_image_datastore()
        image_datastore_id = None
        for ds in hostConfig.datastores:
            if ds.name == image_datastore_name:
                image_datastore_id = ds.id
        self.assertEqual(list(hostConfig.image_datastore_ids)[0],
                         image_datastore_id)

    def _generate_new_iso_ds_path(self):
        if (self._remote_iso_file.lower().rfind(".iso") !=
                len(self._remote_iso_file) - 4):
            raise ValueError()

        return "%s-%s.iso" % (self._remote_iso_file[:-4], str(uuid.uuid4()))

    def _make_new_iso_copy(self, file_manager, new_iso_path):
        copy_task = file_manager.CopyFile(self._remote_iso_file, None,
                                          new_iso_path, None)
        task.WaitForTask(copy_task)

    def test_attach_cdrom(self):
        """
        Tests attach iso code path.
        1. Attach an iso to a non existent VM. Check correct error
        2. Attach a non existent iso file to a valid VM. Check correct error
        3. Attach a real iso if specified to a VM. Verify it succeeds.
        Test should pass the iso path as [datastore_name]/path/to/iso.iso
        """

        if not self._remote_iso_file:
            raise SkipTest("ISO file on server not provided")

        si = self.get_service_instance()
        file_manager = si.RetrieveContent().fileManager
        iso_path = self._generate_new_iso_ds_path()
        iso_path_2 = self._generate_new_iso_ds_path()

        vm_wrapper = VmWrapper(self.host_client)
        image = DiskImage("ttylinux", CloneType.FULL_COPY)
        disks = [
            Disk(new_id(), "default", False, True, image=image, capacity_gb=1,
                 flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]

        # Create disk and VM.
        reservation = \
            vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        # Verify the result when the VM is not found.
        fake_id = str(uuid.uuid4())
        vm_wrapper.attach_iso(fake_id, "/tmp/foo.iso",
                              Host.AttachISOResultCode.VM_NOT_FOUND)

        # Verify the result when the the iso doesn't exist.
        vm_wrapper.attach_iso(vm_id, "/tmp/foo.iso",
                              Host.AttachISOResultCode.SYSTEM_ERROR)

        self._make_new_iso_copy(file_manager, iso_path)
        self._make_new_iso_copy(file_manager, iso_path_2)

        # Doing enough attaches will indirectly verify that we do not grow the
        # device list on reattach.
        for i in xrange(3):
            # verify attach works
            vm_wrapper.attach_iso(vm_id, iso_path)
            # verify re-attach to another iso works
            vm_wrapper.attach_iso(vm_id, iso_path_2)

        vm_wrapper.power(Host.PowerVmOp.ON)
        # Verify reattach fails when vm is powered on.
        vm_wrapper.attach_iso(vm_id, iso_path,
                              Host.AttachISOResultCode.ISO_ATTACHED_ERROR)
        vm_wrapper.power(Host.PowerVmOp.OFF)

        vm_wrapper.detach_iso(vm_id, True)
        vm_wrapper.attach_iso(vm_id, iso_path)
        vm_wrapper.detach_iso(vm_id, True)

        self.clear()

    def test_detach_cdrom_failure(self):
        """ Tests failures of detach iso from VM. """
        vm_wrapper = VmWrapper(self.host_client)
        reservation = vm_wrapper.place_and_reserve().reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        # no prior attach of iso
        vm_wrapper.detach_iso(vm_id, True,
                              Host.DetachISOResultCode.ISO_NOT_ATTACHED)

        # nonexistent VM id
        fake_id = str(uuid.uuid4())
        vm_wrapper.detach_iso(fake_id, True,
                              Host.DetachISOResultCode.VM_NOT_FOUND)

        # Attaching nonexistent iso path still should succeed as long
        # as a valid datastore path format is used
        random = str(uuid.uuid4())
        vm_wrapper.attach_iso(vm_id, "[] /tmp/%s_nonexistent_.iso" % random,
                              Host.AttachISOResultCode.OK)
        # Not supporting detach without delete yet.
        vm_wrapper.detach_iso(vm_id, False,
                              Host.DetachISOResultCode.SYSTEM_ERROR)
        # But detach a non-exist iso should work.
        vm_wrapper.detach_iso(vm_id, True,
                              Host.DetachISOResultCode.OK)

        vm_wrapper.delete(request=vm_wrapper.delete_request())

    def test_detach_cdrom(self):
        """
        Tests detach iso from VM.
        Verify Detaching a real iso from a VM.
        """
        if not self._remote_iso_file:
            raise SkipTest("ISO file on server not provided")

        si = self.get_service_instance()
        file_manager = si.RetrieveContent().fileManager
        iso_path = self._generate_new_iso_ds_path()
        self._make_new_iso_copy(file_manager, iso_path)
        iso_path_2 = self._generate_new_iso_ds_path()
        self._make_new_iso_copy(file_manager, iso_path_2)

        vm_wrapper = VmWrapper(self.host_client)
        reservation = vm_wrapper.place_and_reserve().reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        vm_wrapper.attach_iso(vm_id, iso_path,
                              Host.AttachISOResultCode.OK)
        vm_wrapper.detach_iso(vm_id, True,
                              Host.DetachISOResultCode.OK)

        vm_wrapper.attach_iso(vm_id, iso_path_2,
                              Host.AttachISOResultCode.OK)
        # verify detach works when powered on
        vm_wrapper.power(Host.PowerVmOp.ON)
        vm_wrapper.detach_iso(vm_id, True,
                              Host.DetachISOResultCode.OK)
        vm_wrapper.power(Host.PowerVmOp.OFF)

        vm_wrapper.delete(request=vm_wrapper.delete_request())

    def test_remote_boostrap(self):
        """ Tests boostrapping of an agent against a real host """
        # We need to be able to read the config from the host to set it
        # correctly.
        # https://www.pivotaltracker.com/story/show/83243144
        raise SkipTest()
        req = self._update_agent_config()

        # Try connecting to the client in a loop.

        # Back off on failure to connect to agent
        max_sleep_time = 32
        sleep_time = 0.1
        while sleep_time < max_sleep_time:
            try:
                self.host_client.connect()
                break
            except TTransport.TTransportException:
                time.sleep(sleep_time)
                sleep_time *= 2

        self._validate_post_boostrap_config(req)

    def test_get_nfc_ticket_with_ds_id(self):
        datastores = self.vim_client.get_all_datastores()
        image_datastore = [ds for ds in datastores
                           if ds.name == self.get_image_datastore()][0]
        datastore_id = image_datastore.info.url.rsplit("/", 1)[1]

        request = ServiceTicketRequest(service_type=ServiceType.NFC,
                                       datastore_name=datastore_id)
        response = self.host_client.get_service_ticket(request)
        assert_that(response.result, is_(ServiceTicketResultCode.OK))

        ticket = response.ticket
        assert_that(ticket, not_none())
        assert_that(ticket.port, is_(902))
        assert_that(ticket.service_type, is_("nfc"))
        assert_that(ticket.session_id, not_none())
        assert_that(ticket.ssl_thumbprint, not_none())

    def test_persist_mode(self):
        # Enter maintenance
        self.set_host_mode(HostMode.MAINTENANCE)

        # Restart agent by provisioning with different configuration
        self.provision_hosts(mem_overcommit=2.1)
        self.client_connections()

        # Check mode. It should still be MAINTENANCE.
        response = self.host_client.get_host_mode(GetHostModeRequest())
        assert_that(response.result, equal_to(GetHostModeResultCode.OK))
        assert_that(response.mode, equal_to(HostMode.MAINTENANCE))

    def _create_test_image(self, name):
        """ Create an test image for tests to use on a datastore """
        datastore = self._find_configured_datastore_in_host_config()

        # ttylinux is the default image that is copied to datastore
        # when agent starts
        src_image = Image("ttylinux", datastore)
        dst_image = Image(name, datastore)

        # Copy image
        request = Host.CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)
        assert_that(response.result, is_in([CopyImageResultCode.OK, CopyImageResultCode.DESTINATION_ALREADY_EXIST]))

        return dst_image, datastore

    def _get_vim_ticket(self):
        request = ServiceTicketRequest(ServiceType.VIM)
        response = self.host_client.get_service_ticket(request)
        assert_that(response.result, is_(ServiceTicketResultCode.OK))
        return response.vim_ticket

    def test_disable_large_pages(self):
        def _update_host_config(self, mem_overcommit):
            """Helper that actually updates the host config"""
            self.provision_hosts(mem_overcommit=mem_overcommit)

            # Make client connections again
            self.client_connections()

        # create a connection to hostd.
        si = self.get_service_instance()

        # Get the host MO.
        host_system = host.GetHostSystem(si)
        advOption = host_system.configManager.advancedOption
        values = advOption.QueryOptions('Mem.AllocGuestLargePage')
        self.assertEqual(values[0].key, 'Mem.AllocGuestLargePage')

        # No over commit, allow large pages.
        _update_host_config(self, 1.0)
        values = advOption.QueryOptions('Mem.AllocGuestLargePage')
        self.assertEqual(values[0].value, 1)

        # Set memory overcommit and check if large page is disabled.
        _update_host_config(self, 2.0)
        values = advOption.QueryOptions('Mem.AllocGuestLargePage')
        self.assertEqual(values[0].value, 0)

        # Set memory overcommit and check if large page is enabled.
        _update_host_config(self, 1.0)
        values = advOption.QueryOptions('Mem.AllocGuestLargePage')
        self.assertEqual(values[0].value, 1)

        # Reprovision using defaults for other tests.
        _update_host_config(self, 2.0)

    def test_create_vm_with_ephemeral_disks_concurrent(self):
        concurrency = 5
        atmoic_lock = threading.Lock()
        results = {"count": 0}

        def _thread():
            self._test_create_vm_with_ephemeral_disks("ttylinux",
                                                      concurrent=True,
                                                      new_client=True)
            with atmoic_lock:
                results["count"] += 1

        threads = []
        for i in range(concurrency):
            thread = threading.Thread(target=_thread)
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

        assert_that(results["count"], is_(concurrency))

    def test_concurrent_copy_image(self):
        concurrency = 3
        atomic_lock = threading.Lock()
        results = {"ok": 0, "existed": 0}

        datastore = self._find_configured_datastore_in_host_config()
        new_image_id = "concurrent-copy-%s" % str(uuid.uuid4())

        src_image = Image("ttylinux", datastore)
        dst_image = Image(new_image_id, datastore)

        # verify destination_id is not in datastore
        request = Host.GetImagesRequest(datastore.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item("ttylinux"))
        assert_that(response.image_ids, not(has_item(new_image_id)))
        image_number = len(response.image_ids)

        def _thread():
            client = self.create_client()
            request = Host.CopyImageRequest(src_image, dst_image)
            response = client.copy_image(request)
            ok = response.result == CopyImageResultCode.OK
            existed = response.result == CopyImageResultCode.\
                DESTINATION_ALREADY_EXIST

            # Verify destination_id is in datastore
            request = Host.GetImagesRequest(datastore.id)
            response = client.get_images(request)
            assert_that(response.result, is_(GetImagesResultCode.OK))
            assert_that(response.image_ids, has_item("ttylinux"))
            assert_that(response.image_ids, has_item(new_image_id))
            assert_that(response.image_ids, has_length(image_number + 1))
            with atomic_lock:
                if ok:
                    results["ok"] += 1
                if existed:
                    results["existed"] += 1

        threads = []
        for i in range(concurrency):
            thread = threading.Thread(target=_thread)
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

        # Clean destination image
        self._delete_image(dst_image)

        # Only one copy is successful, all others return
        # DESTINATION_ALREADY_EXIST
        assert_that(results["ok"], is_(1))
        assert_that(results["existed"], is_(concurrency - 1))

    def test_force_delete_vm(self):
        vm_wrapper = VmWrapper(self.host_client)

        # create a vm without disk
        reservation = vm_wrapper.place_and_reserve().reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        # create 2 disks
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR)
        ]

        reservation = vm_wrapper.place_and_reserve(disks=disks).reservation
        vm_wrapper.create_disks(disks, reservation, validate=True)

        # attach disks
        disk_ids = [disk.id for disk in disks]
        vm_wrapper.attach_disks(vm_id, disk_ids)

        # delete vm fails without force
        vm_wrapper.delete(request=vm_wrapper.delete_request(),
                          expect=Host.DeleteVmResultCode.OPERATION_NOT_ALLOWED)

        # delete vm with force succeeds
        vm_wrapper.delete(request=vm_wrapper.delete_request(force=True))
        for disk_id in disk_ids:
            vm_wrapper.get_disk(disk_id, expect_found=False)

    def test_vminfo(self):
        vm_wrapper = VmWrapper(self.host_client)
        vm_id = vm_wrapper.create().vm.id
        vm_manager = EsxVmManager(self.vim_client, None)
        vminfo = vm_manager.get_vminfo(vm_id)
        # p1/t1 is the default project/tenant
        assert_that(vminfo, equal_to({"project": "p1", "tenant": "t1"}))

        # Test vm with empty project and tenant
        vm_wrapper = VmWrapper(self.host_client)
        vm_wrapper.vm.project_id = None
        vm_wrapper.vm.tenant_id = None
        vm_id = vm_wrapper.create().vm.id
        vminfo = vm_manager.get_vminfo(vm_id)
        assert_that(vminfo, equal_to({}))

    def test_disk_uuids(self):
        # Create a vm without a root disk and blank disk then attach another
        # persistent disk. Then verify that only the uuids of the
        # ephemeral and persistent disks are updated to match their cloud ids.

        vm_wrapper = VmWrapper(self.host_client)

        disk_id_root = new_id()
        disk_id_ephemeral = new_id()
        disk_id_persistent = new_id()

        image = DiskImage("ttylinux", CloneType.COPY_ON_WRITE)
        disks = [
            Disk(disk_id_root, self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(disk_id_ephemeral, self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]

        reservation = \
            vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id

        # create one persistent disk
        disks = [
            Disk(disk_id_persistent, self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]
        reservation = vm_wrapper.place_and_reserve(disks=disks).reservation
        vm_wrapper.create_disks(disks, reservation, validate=True)
        vm_wrapper.attach_disks(vm_id, [disk_id_persistent])

        vim_vm = self.vim_client.get_vm(vm_id)

        disk_uuid_map = dict([(dev.backing.uuid, dev.backing.fileName)
                              for dev in vim_vm.config.hardware.device
                              if isinstance(dev, vim.vm.device.VirtualDisk)])

        # Assert that the UUID assigned to the ephemeral and persistent disks
        # matches their ids
        for disk_id in (disk_id_ephemeral, disk_id_persistent):
            self.assertTrue(disk_id in disk_uuid_map and
                            disk_id in disk_uuid_map[disk_id])
        # Assert that no such assignment is done for link-clone root disk.
        self.assertFalse(disk_id_root in disk_uuid_map)

        vm_wrapper.detach_disks(vm_id, [disk_id_persistent])
        vm_wrapper.delete(request=vm_wrapper.delete_request())
        vm_wrapper.delete_disks([disk_id_persistent], validate=True)

    def test_network_validation(self):
        """ Test the creation of VMs with the right default networks"""

        # Provision a host with no network specified and verify that the VM
        # created has a valid network backing
        self.provision_hosts(mem_overcommit=1.0, vm_networks=[])
        # Make client connections again
        self.client_connections()

        vm_wrapper = VmWrapper(self.host_client)
        image = DiskImage("ttylinux", CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image, capacity_gb=1,
                 flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]

        # create disk
        reservation = \
            vm_wrapper.place_and_reserve(vm_disks=disks).reservation

        # create vm without network info specified
        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id
        result = vm_wrapper.get_network(vm_id=vm_id)
        assert_that(len(result.network_info), is_(1))
        vm_wrapper.delete()

        # Provision a host with one invalid and one valid network and verify
        # that the VM gets created with the valid network backing.
        self.provision_hosts(mem_overcommit=1.0,
                             vm_networks=["invalid_net_name",
                                          self._vm_network])
        # Make client connections again
        self.client_connections()
        vm_wrapper = VmWrapper(self.host_client)
        reservation = \
            vm_wrapper.place_and_reserve(vm_disks=disks).reservation

        request = vm_wrapper.create_request(res_id=reservation)
        vm_id = vm_wrapper.create(request=request).vm.id
        result = vm_wrapper.get_network(vm_id=vm_id)
        assert_that(len(result.network_info), is_(1))
        assert_that(result.network_info[0].network == self._vm_network)
        vm_wrapper.delete()

    def test_place_on_multiple_datastores(self):
        """ Test placement can actually place vm to datastores without image.
        """
        host_datastores = self.vim_client.get_all_datastores()
        image_datastore = self._find_configured_datastore_in_host_config()
        dest_datastore = None

        for ds in host_datastores:
            if ds.info.url.rsplit("/", 1)[1] != image_datastore.id:
                dest_datastore = ds
                break

        if not dest_datastore:
            raise SkipTest()

        # Test only 2 datastores, with one image datastore and another
        # datastore.
        self.provision_hosts(datastores=[image_datastore.name,
                                         dest_datastore.name],
                             used_for_vms=False)
        self.client_connections()

        concurrency = 3
        atmoic_lock = threading.Lock()
        results = {"count": 0}

        # Only copy image to datastore[0]
        new_image_id = str(uuid.uuid4())
        datastore = Datastore(id=image_datastore.name)
        src_image = Image("ttylinux", datastore)
        dst_image = Image(new_image_id, datastore)
        request = Host.CopyImageRequest(src_image, dst_image)
        response = self.host_client.copy_image(request)
        assert_that(response.result, is_(CopyImageResultCode.OK))

        def _thread():
            self._test_create_vm_with_ephemeral_disks(new_image_id,
                                                      concurrent=True,
                                                      new_client=True)
            with atmoic_lock:
                results["count"] += 1

        threads = []
        for i in range(concurrency):
            thread = threading.Thread(target=_thread)
            threads.append(thread)
            thread.start()

        for thread in threads:
            thread.join()

        # Make sure the new image is copied to both datastores, and clean
        # them up.
        for ds in (image_datastore, dest_datastore):
            image = Image(datastore=Datastore(id=ds.name), id=new_image_id)
            self._delete_image(image)

        assert_that(results["count"], is_(concurrency))

    def test_place_on_datastore_tag(self):
        host_config_request = Host.GetConfigRequest()
        res = self.host_client.get_host_config(host_config_request)
        self.assertEqual(res.result, GetConfigResultCode.OK)

        datastores = res.hostConfig.datastores
        for datastore in datastores:
            tag = self._type_to_tag(datastore.type)
            if not tag:
                continue

            vm_wrapper = VmWrapper(self.host_client)

            # Test place disks with only datastore constraint
            disk = Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                        capacity_gb=0,
                        resource_constraints=self._create_constraints(
                            [datastore.id],
                            []))
            vm_wrapper.place(vm_disks=[disk])

            # Test place disks with datastore and datastore tag constraint
            disk = Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                        capacity_gb=0,
                        resource_constraints=self._create_constraints(
                            [datastore.id],
                            [tag]))
            vm_wrapper.place(vm_disks=[disk], expect=PlaceResultCode.OK)

            # Test place disks with the wrong datastore tag
            for other_tag in self._other_tags(tag):
                disk = Disk(
                    new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                    capacity_gb=0,
                    resource_constraints=self._create_constraints(
                        [datastore.id],
                        [other_tag]))
                vm_wrapper.place(vm_disks=[disk],
                                 expect=PlaceResultCode.NO_SUCH_RESOURCE)

    def test_provision_without_datastores(self):
        """
        Test that the host uses all the datastores when it gets provisioned
        without any datastores specified.
        """
        # provision the host without datastores
        datastores = self.get_all_datastores()
        self.provision_hosts(datastores=[], image_ds=datastores[0])

        # verify that the host configuration contains all the datastores.
        req = Host.GetConfigRequest()
        res = self.create_client().get_host_config(req)
        self.assertEqual(len(res.hostConfig.datastores),
                         len(self.vim_client.get_all_datastores()))

        # verify that get_datastores return all the datastores.
        req = GetDatastoresRequest()
        host_client = self.create_client()
        res = host_client.get_datastores(req)
        self.assertTrue(len(res.datastores) > 0)

    def test_get_networks(self):
        # provision the host without networks
        self.provision_hosts(vm_networks=[])

        # verify that the get_networks contains at least one network.
        req = GetNetworksRequest()
        host_client = self.create_client()
        res = host_client.get_networks(req)
        self.assertTrue(len(res.networks) > 0)

    def _manage_disk(self, op, **kwargs):
        task = op(self.vim_client._content.virtualDiskManager, **kwargs)
        self.vim_client.wait_for_task(task)

    def _gen_vd_spec(self):
        spec = vim.VirtualDiskManager.VirtualDiskSpec()
        spec.disk_type = str(vim.VirtualDiskManager.VirtualDiskType.thin)
        spec.adapterType = \
            str(vim.VirtualDiskManager.VirtualDiskAdapterType.lsiLogic)
        return spec

    def test_finalize_image(self):
        """ Integration test for atomic image create """
        img_id = "test-create-image"
        tmp_img_id = "-tmp-" + img_id
        tmp_image, ds = self._create_test_image(tmp_img_id)
        tmp_image_path = datastore_path(ds.name, "image_" + tmp_img_id)
        src_vmdk = vmdk_path(ds.id, tmp_img_id, IMAGE_FOLDER_NAME_PREFIX)
        dst_vmdk = "%s/%s.vmdk" % (tmp_image_path, img_id)

        try:
            self._manage_disk(
                vim.VirtualDiskManager.MoveVirtualDisk_Task,
                sourceName=src_vmdk, destName=dst_vmdk, force=True)
        except:
            logger.error("Error moving vmdk %s" % src_vmdk,
                         exc_info=True)
            self._manage_disk(
                vim.VirtualDiskManager.DeleteVirtualDisk_Task,
                name=src_vmdk)
            raise
        dst_image = Image(img_id, ds)
        req = FinalizeImageRequest(image_id=img_id,
                                   datastore=ds.id,
                                   tmp_image_path=tmp_image_path)
        response = self.host_client.finalize_image(req)
        self.assertEqual(response.result, FinalizeImageResultCode.OK)
        request = Host.GetImagesRequest(ds.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item(img_id))

        # Issue another create call and it should fail as the source doesn't
        # exist.
        req = FinalizeImageRequest(image_id=img_id,
                                   datastore=ds.id,
                                   tmp_image_path=tmp_image_path)
        response = self.host_client.finalize_image(req)
        self.assertEqual(response.result,
                         FinalizeImageResultCode.IMAGE_NOT_FOUND)

        # Verify that we fail if the destination already exists.
        tmp_image, ds = self._create_test_image(tmp_img_id)
        req = FinalizeImageRequest(image_id=img_id,
                                   datastore=ds.id,
                                   tmp_image_path=tmp_image_path)
        response = self.host_client.finalize_image(req)
        self.assertEqual(response.result,
                         FinalizeImageResultCode.DESTINATION_ALREADY_EXIST)

        # cleanup
        self._delete_image(dst_image)

    def test_start_image_scanner(self):
        """
        Test image scanner. Make sure the idle images are reported correctly.
        """
        datastore = self._find_configured_datastore_in_host_config()

        image_id_1 = new_id()
        dst_image_1, _ = self._create_test_image(image_id_1)

        image_id_2 = new_id()
        dst_image_2, _ = self._create_test_image(image_id_2)
        disk_image_2 = DiskImage(image_id_2, CloneType.COPY_ON_WRITE)

        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=disk_image_2,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=2, flavor_info=self.DEFAULT_DISK_FLAVOR)
        ]
        vm_wrapper = VmWrapper(self.host_client)
        reservation = vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_wrapper.create(request=request)
        logger.info("Image scan, Vm id: %s" % vm_wrapper.id)

        # Start image scanner
        start_scan_request = StartImageScanRequest()
        start_scan_request.datastore_id = datastore.id
        start_scan_request.scan_rate = 600
        start_scan_request.timeout = 30
        start_scan_response = self.host_client.start_image_scan(start_scan_request)
        self.assertEqual(start_scan_response.result, StartImageOperationResultCode.OK)
        self._get_and_check_inactive_images(datastore.id, image_id_1, True)
        get_inactive_images_response = self._get_and_check_inactive_images(datastore.id, image_id_2, False)

        # Start image sweeper
        start_sweep_request = StartImageSweepRequest()
        start_sweep_request.datastore_id = datastore.id
        start_sweep_request.image_descs = get_inactive_images_response.image_descs
        start_sweep_request.sweep_rate = 600
        start_sweep_request.timeout = 30
        start_sweep_request.grace_period = 0
        start_sweep_response = self.host_client.start_image_sweep(start_sweep_request)
        self.assertEqual(start_sweep_response.result, StartImageOperationResultCode.OK)

        self._get_and_check_deleted_images(datastore.id, image_id_1, True)
        # cleanup
        vm_wrapper.delete()
        self._delete_image(dst_image_1, DeleteDirectoryResultCode.DIRECTORY_NOT_FOUND)
        self._delete_image(dst_image_2)

    def _get_and_check_inactive_images(self, datastore_id, image_id, found):
        get_inactive_images_request = GetInactiveImagesRequest()
        get_inactive_images_request.datastore_id = datastore_id
        for counter in range(1, 30):
            time.sleep(1)
            get_inactive_images_response = self.host_client.get_inactive_images(get_inactive_images_request)
            if get_inactive_images_response.result is GetMonitoredImagesResultCode.OK:
                break

        self.assertEqual(get_inactive_images_response.result, GetMonitoredImagesResultCode.OK)
        image_descriptors = get_inactive_images_response.image_descs
        image_found = False
        logger.info("Image Descriptors: %s" % image_descriptors)
        logger.info("Target Image Id: %s" % image_id)
        for image_descriptor in image_descriptors:
            if image_descriptor.image_id == image_id:
                image_found = True

        self.assertEqual(image_found, found)
        return get_inactive_images_response

    def _get_and_check_deleted_images(self, datastore_id, image_id, found):
        get_deleted_images_request = GetInactiveImagesRequest()
        get_deleted_images_request.datastore_id = datastore_id
        for counter in range(1, 30):
            time.sleep(1)
            get_inactive_deleted_response = self.host_client.get_deleted_images(get_deleted_images_request)
            if get_inactive_deleted_response.result is GetMonitoredImagesResultCode.OK:
                break

        self.assertEqual(get_inactive_deleted_response.result, GetMonitoredImagesResultCode.OK)
        image_descriptors = get_inactive_deleted_response.image_descs
        image_found = False
        logger.info("Image Descriptors: %s" % image_descriptors)
        logger.info("Target Image Id: %s" % image_id)
        for image_descriptor in image_descriptors:
            if image_descriptor.image_id == image_id:
                image_found = True

        self.assertEqual(image_found, found)
        return get_inactive_deleted_response

    def _type_to_tag(self, type):
        type_to_tag = {
            DatastoreType.NFS_3: NFS_TAG,
            DatastoreType.NFS_41: NFS_TAG,
            DatastoreType.SHARED_VMFS: SHARED_VMFS_TAG,
            DatastoreType.LOCAL_VMFS: LOCAL_VMFS_TAG,
        }

        if type in type_to_tag:
            return type_to_tag[type]
        else:
            return None

    def _other_tags(self, tag):
        tags = [NFS_TAG, SHARED_VMFS_TAG, LOCAL_VMFS_TAG]
        tags.remove(tag)
        return tags

    def _create_constraints(self, datastores, tags):
        constraints = []
        for datastore in datastores:
            constraints.append(ResourceConstraint(
                type=ResourceConstraintType.DATASTORE,
                values=[datastore]))
        for tag in tags:
            constraints.append(ResourceConstraint(
                type=ResourceConstraintType.DATASTORE_TAG,
                values=[tag]))
        return constraints

    def test_create_image_from_vm(self):
        """ Integration test for creating an image from a VM """
        img_id = "test-new-im-from-vm-%s" % new_id()
        tmp_img_id = "-tmp-" + img_id
        tmp_image, ds = self._create_test_image(tmp_img_id)

        tmp_image_path = datastore_path(ds.id, "image_" + tmp_img_id)
        src_vmdk = vmdk_path(ds.id, tmp_img_id, IMAGE_FOLDER_NAME_PREFIX)
        vm_wrapper = VmWrapper(self.host_client)

        try:
            self._manage_disk(
                vim.VirtualDiskManager.DeleteVirtualDisk_Task,
                name=src_vmdk)
        except:
            logger.error(
                "Error deleting vmdk when setting up tmp image %s" % src_vmdk,
                exc_info=True)
            raise

        dst_image = Image(img_id, ds)

        image = DiskImage("ttylinux", CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=1, flavor_info=self.DEFAULT_DISK_FLAVOR),
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, True, True,
                 capacity_gb=2, flavor_info=self.DEFAULT_DISK_FLAVOR)
        ]
        reservation = \
            vm_wrapper.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper.create_request(res_id=reservation)
        vm_wrapper.create(request=request)

        # VM in wrong state
        vm_wrapper.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        vm_wrapper.create_image_from_vm(
            image_id=img_id,
            datastore=ds.id,
            tmp_image_path=tmp_image_path,
            expect=Host.CreateImageFromVmResultCode.INVALID_VM_POWER_STATE)

        vm_wrapper.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)

        # Happy case
        vm_wrapper.create_image_from_vm(
            image_id=img_id,
            datastore=ds.id,
            tmp_image_path=tmp_image_path,
            expect=Host.CreateImageFromVmResultCode.OK)

        request = Host.GetImagesRequest(ds.id)
        response = self.host_client.get_images(request)
        assert_that(response.result, is_(GetImagesResultCode.OK))
        assert_that(response.image_ids, has_item(img_id))

        # Issue another create call and it should fail as the source doesn't
        # exist.
        req = FinalizeImageRequest(image_id=img_id,
                                   datastore=ds.id,
                                   tmp_image_path=tmp_image_path)
        response = self.host_client.finalize_image(req)
        self.assertEqual(response.result,
                         FinalizeImageResultCode.IMAGE_NOT_FOUND)

        # Verify that we fail if the destination already exists.
        tmp_image, ds = self._create_test_image(tmp_img_id)
        vm_wrapper.create_image_from_vm(
            image_id=tmp_img_id,
            datastore=ds.id,
            tmp_image_path=tmp_image_path,
            expect=Host.CreateImageFromVmResultCode.IMAGE_ALREADY_EXIST)

        vm_wrapper.delete()

        # VM to create image from is gone.
        vm_wrapper.create_image_from_vm(
            image_id=img_id,
            datastore=ds.id,
            tmp_image_path=tmp_image_path,
            expect=Host.CreateImageFromVmResultCode.VM_NOT_FOUND)

        # Create a VM using the new image created
        vm_wrapper2 = VmWrapper(self.host_client)
        image = DiskImage(img_id, CloneType.COPY_ON_WRITE)
        disks = [
            Disk(new_id(), self.DEFAULT_DISK_FLAVOR.name, False, True,
                 image=image,
                 capacity_gb=0, flavor_info=self.DEFAULT_DISK_FLAVOR),
        ]
        reservation = \
            vm_wrapper2.place_and_reserve(vm_disks=disks).reservation
        request = vm_wrapper2.create_request(res_id=reservation)
        vm_wrapper2.create(request=request)
        vm_wrapper2.power(Host.PowerVmOp.ON, Host.PowerVmOpResultCode.OK)
        vm_wrapper2.power(Host.PowerVmOp.OFF, Host.PowerVmOpResultCode.OK)
        vm_wrapper2.delete()

        # cleanup
        self._delete_image(dst_image)

    def test_delete_tmp_image(self):
        """ Integration test for deleting temp image directory """
        img_id = "test-delete-tmp-image"
        tmp_iamge, ds = self._create_test_image(img_id)
        tmp_image_path = "image_" + img_id
        req = DeleteDirectoryRequest(datastore=ds.id,
                                     directory_path=tmp_image_path)
        res = self.host_client.delete_directory(req)
        self.assertEqual(res.result, DeleteDirectoryResultCode.OK)

        req = DeleteDirectoryRequest(datastore=ds.id,
                                     directory_path=tmp_image_path)
        res = self.host_client.delete_directory(req)
        self.assertEqual(res.result,
                         DeleteDirectoryResultCode.DIRECTORY_NOT_FOUND)

        req = DeleteDirectoryRequest(datastore="foo_bar",
                                     directory_path=tmp_image_path)
        res = self.host_client.delete_directory(req)
        self.assertEqual(res.result,
                         DeleteDirectoryResultCode.DATASTORE_NOT_FOUND)

    def _get_agent_id(self):
        host_config_request = Host.GetConfigRequest()
        res = self.host_client.get_host_config(host_config_request)
        return res.hostConfig.agent_id


if __name__ == "__main__":
    unittest.main()
