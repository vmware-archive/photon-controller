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
import random
import time
import unittest

from hamcrest import *  # noqa
from mock import MagicMock
from nose.plugins.skip import SkipTest
from testconfig import config

from gen.agent.ttypes import PowerState
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import EsxVmConfigSpec
from host.hypervisor.vm_manager import VmNotFoundException

from pyVmomi import vim


class TestVimClient(unittest.TestCase):
    def setUp(self):
        if "host_remote_test" not in config:
            raise SkipTest()

        self.host = config["host_remote_test"]["server"]
        self.pwd = config["host_remote_test"]["esx_pwd"]

        if self.host is None or self.pwd is None:
            raise SkipTest()

        self.vim_client = VimClient(auto_sync=True)
        self.vim_client.connect_userpwd(self.host, "root", self.pwd)
        self._logger = logging.getLogger(__name__)

    def tearDown(self):
        self.vim_client.disconnect(wait=True)

    def test_memory_usage(self):
        used_memory = self.vim_client.memory_usage_mb
        assert_that(used_memory > 0, is_(True))

    def test_total_memory(self):
        total_memory = self.vim_client.total_vmusable_memory_mb
        assert_that(total_memory > 0, is_(True))

    def test_total_cpus(self):
        num_cpus = self.vim_client.num_physical_cpus
        assert_that(num_cpus > 0, is_(True))

    def _create_test_vm(self, suffix="host-integ"):
        # Create VM
        vm_id = "vm_%s-%s-%s" % (
            time.strftime("%Y-%m-%d-%H%M%S", time.localtime()),
            str(random.randint(100000, 1000000)),
            suffix)

        datastore = self.vim_client.get_all_datastores()[0].name
        disk_path = "[%s] %s/disk.vmdk" % (datastore, vm_id)
        create_spec = self.get_create_spec(datastore, vm_id, disk_path)
        self.vim_client.create_vm(vm_id, create_spec)
        vm = self.vim_client.get_vm(vm_id)
        return (vm_id, vm, datastore, disk_path)

    def test_get_cached_vm(self):
        vm_id, vm, datastore, disk_path = self._create_test_vm("vm-cache-test")

        # Verify VM is in cache
        vms = self.vim_client.get_vms_in_cache()
        found_vms = [v for v in vms if v.name == vm_id]
        assert_that(len(found_vms), is_(1))
        assert_that(found_vms[0].name, is_(vm_id))
        assert_that(found_vms[0].power_state, is_(PowerState.poweredOff))
        assert_that(found_vms[0].memory_mb, is_(64))
        assert_that(found_vms[0].path, starts_with("[%s]" % datastore))
        assert_that(len(found_vms[0].disks), is_(1))
        assert_that(found_vms[0].disks[0], is_(disk_path))

        # Make sure get_vm_in_cache works
        vm_from_cache = self.vim_client.get_vm_in_cache(vm_id)
        assert_that(vm_from_cache.name, is_(vm_id))
        self.assertRaises(VmNotFoundException,
                          self.vim_client.get_vm_in_cache, "missing")

        # Add disk
        disk2_path = "[%s] %s/disk2.vmdk" % (datastore, vm_id)
        update_spec = self.get_update_spec(vm, disk2_path)
        task = vm.ReconfigVM_Task(update_spec)
        self.vim_client.wait_for_task(task)

        # For the ReconfigVM task to remove disk, the hostd could update
        # task status to success before updating VM status. Thus when
        # wait_for_task returns, the vm_cache is possible to be still in old
        # state, though eventually it converges to consistent state. It only
        # happens in this task AFAIK. It should be fine for this task, because
        # rarely there is other operation that depends on this task.
        self._wait_vm_has_disk(vm_id, 2)

        # Verify disk added
        vms = self.vim_client.get_vms_in_cache()
        found_vms = [v for v in vms if v.name == vm_id]
        assert_that(len(found_vms[0].disks), is_(2))
        assert_that(found_vms[0].disks,
                    contains_inanyorder(disk_path, disk2_path))

        # Remove disk
        vm = self.vim_client.get_vm(vm_id)
        remove_spec = self.get_remove_spec(vm, disk2_path)
        task = vm.ReconfigVM_Task(remove_spec)
        self.vim_client.wait_for_task(task)

        # Same as before when disk is added
        self._wait_vm_has_disk(vm_id, 1)

        # Verify disk removed
        vms = self.vim_client.get_vms_in_cache()
        found_vms = [v for v in vms if v.name == vm_id]
        assert_that(len(found_vms), is_(1))
        assert_that(len(found_vms[0].disks), is_(1), "disk2 in " +
                                                     str(found_vms[0].disks))
        assert_that(found_vms[0].disks,
                    contains_inanyorder(disk_path))

        # Power on vm
        task = vm.PowerOn()
        self.vim_client.wait_for_task(task)

        # Wait until it disappears from the cache
        self._wait_vm_power_status(vm_id, PowerState.poweredOn)

        # Verify VM state in cache is updated
        vms = self.vim_client.get_vms_in_cache()
        found_vms = [v for v in vms if v.name == vm_id]
        assert_that(len(found_vms), is_(1))
        assert_that(found_vms[0].power_state, is_(PowerState.poweredOn))
        assert_that(found_vms[0].name, is_(vm_id))
        assert_that(found_vms[0].memory_mb, is_(64))
        assert_that(found_vms[0].path, starts_with("[%s]" % datastore))
        assert_that(len(found_vms[0].disks), is_(1))
        assert_that(found_vms[0].disks[0], is_(disk_path))

        # Destroy VM
        task = vm.PowerOff()
        self.vim_client.wait_for_task(task)
        task = vm.Destroy()
        self.vim_client.wait_for_task(task)

        # Verify VM is deleted from cache
        vms = self.vim_client.get_vms_in_cache()
        found_vms = [v for v in vms if v.name == vm_id]
        assert_that(len(found_vms), is_(0))

    def test_no_datastore_update(self):
        """ Test datastore update is no longer triggered on VM creates/deletes
        """

        class UpdateListener(object):
            def __init__(self):
                self._ds_update_count = 0

            def datastores_updated(self):
                self._ds_update_count += 1

            def networks_updated(self):
                pass

            def virtual_machines_updated(self):
                pass

        listener = UpdateListener()
        self.vim_client.add_update_listener(listener)
        # listener always gets updated once on add
        assert_that(listener._ds_update_count, is_(1))

        mock_apply = MagicMock(wraps=self.vim_client._apply_ds_update)
        self.vim_client._apply_ds_update = mock_apply

        _, vm, _, _ = self._create_test_vm("ds-update-test")
        task = vm.Destroy()
        self.vim_client.wait_for_task(task)

        # expect to get a datastore property update (unfortunately) ...
        for _ in xrange(50):
            if mock_apply.call_count > 0:
                break
            time.sleep(0.1)
        # ... but that additional datastore updated notifications are sent out
        # as a result
        assert_that(listener._ds_update_count, is_(1))

    def get_create_spec(self, datastore, vm_id, disk_path):
        create_spec = EsxVmConfigSpec()
        create_spec.init_for_create(vm_id, datastore, 64, 2)
        controller = vim.vm.device.VirtualLsiLogicController(
            key=1,
            sharedBus=vim.vm.device.VirtualSCSIController.Sharing.noSharing,
            busNumber=2,
            unitNumber=-1)
        create_spec._add_device(controller)
        backing = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(
            fileName=disk_path,
            diskMode=vim.vm.device.VirtualDiskOption.DiskMode.persistent
        )
        disk = vim.vm.device.VirtualDisk(
            controllerKey=1,
            key=-1,
            unitNumber=-1,
            backing=backing,
            capacityInKB=1024,
        )
        create_spec._create_device(disk)
        return create_spec.get_spec()

    def get_update_spec(self, vm_info, disk_path):
        update_spec = EsxVmConfigSpec()
        update_spec.init_for_update()
        backing = vim.vm.device.VirtualDisk.FlatVer2BackingInfo(
            fileName=disk_path,
            diskMode=vim.vm.device.VirtualDiskOption.DiskMode.persistent
        )
        controller = update_spec._find_scsi_controller(vm_info.config)
        disk = vim.vm.device.VirtualDisk(
            controllerKey=controller.key,
            key=-1,
            unitNumber=-1,
            backing=backing,
            capacityInKB=1024,
        )
        update_spec._create_device(disk)
        return update_spec.get_spec()

    def get_remove_spec(self, vm_info, disk_path):
        remove_spec = EsxVmConfigSpec()
        remove_spec.init_for_update()
        devices = remove_spec._get_devices_from_config(vm_info.config)
        found_device = None
        for device in devices:
            if isinstance(device, vim.vm.device.VirtualDisk) and device.backing.fileName.endswith(disk_path):
                found_device = device
        remove_spec._remove_device(found_device)
        return remove_spec.get_spec()

    def test_clone_ticket(self):
        ticket = self.vim_client.acquire_clone_ticket()
        vim_client2 = VimClient()
        vim_client2.connect_ticket(self.host, ticket)
        vim_client2.host_system

    def _wait_vm_has_disk(self, vm_id, disk_num):
        """Wait until the vm has disk number of the vm becomes disk_num
        """
        now = time.time()
        for _ in xrange(50):
            vm_in_cache = self.vim_client.get_vm_in_cache(vm_id)
            if len(vm_in_cache.disks) == disk_num:
                self._logger.info("VmCache disk number synced in %.2f second" %
                                  (time.time() - now))
                break
            time.sleep(0.1)

    def _wait_vm_power_status(self, vm_id, power_state):
        """Wait until the vm has power_state
        """
        now = time.time()
        for _ in xrange(50):
            vm_in_cache = self.vim_client.get_vm_in_cache(vm_id)
            if vm_in_cache.power_state == power_state:
                self._logger.info("VmCache power_state synced in %.2f second" %
                                  (time.time() - now))
                break
            time.sleep(0.1)


if __name__ == "__main__":
    unittest.main()
