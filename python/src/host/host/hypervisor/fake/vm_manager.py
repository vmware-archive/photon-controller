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

"""Fake VM manager."""

import copy

from common.kind import Unit
from gen.host.ttypes import ConnectedStatus
from gen.host.ttypes import VmNetworkInfo
from gen.host.ttypes import Ipv4Address
from gen.resource.ttypes import MksTicket
from host.hypervisor.disk_manager import DatastoreOutOfSpaceException
from host.hypervisor.resources import State
from host.hypervisor.resources import Vm
from host.hypervisor.vm_manager import OperationNotAllowedException
from host.hypervisor.vm_manager import VmManager
from host.hypervisor.vm_manager import VmAlreadyExistException
from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.vm_manager import VmPowerStateException
from random import Random


class FakeVmManager(VmManager):
    def __init__(self, hypervisor):
        self._resources = {}
        self._hypervisor = hypervisor
        self.update_listeners = set()

    def add_update_listener(self, listener):
        listener.networks_updated()
        listener.virtual_machines_updated()
        self.update_listeners.add(listener)

    def remove_update_listener(self, listener):
        self.update_listeners.discard(listener)

    def _trigger_vm_listeners(self):
        for listener in self.update_listeners:
            listener.virtual_machines_updated()

    @staticmethod
    def _power_state_to_resource_state(power_state):
        return {
            "on": State.STARTED,
            "off": State.STOPPED,
            "suspended": State.SUSPENDED
        }[power_state]

    def power_on_vm(self, vm_id):
        self._assert_exists(vm_id)
        self._power_vm(vm_id, "off", "on")

    def power_off_vm(self, vm_id):
        self._assert_exists(vm_id)
        self._power_vm(vm_id, "on", "off")

    def reset_vm(self, vm_id):
        self._assert_exists(vm_id)
        self._power_vm(vm_id, "on", "on")

    def suspend_vm(self, vm_id):
        self._assert_exists(vm_id)
        self._power_vm(vm_id, "on", "suspended")

    def resume_vm(self, vm_id):
        self._assert_exists(vm_id)
        self._power_vm(vm_id, "suspended", "on")

    def delete_vm(self, vm_id, force=False):
        self._assert_exists(vm_id)
        vm = self._resources[vm_id]
        if vm.state != "off":
            raise VmPowerStateException()
        # fake_vm_spec.disks are all persistent disks.
        # TODO(agui): This looks hacky. FakeVm should have its own device
        # list, as opposed to play spec around.
        if not force and len(self._resources[vm_id].fake_vm_spec.disks) != 0:
            raise OperationNotAllowedException("persistent disks attached")
        fake_vm_spec = self._resources[vm_id].fake_vm_spec
        for disk_id in fake_vm_spec.vm_disks:
            self._disk_manager().delete_disk(fake_vm_spec.datastore, disk_id)
        del self._resources[vm_id]
        self._trigger_vm_listeners()

    def create_vm_spec(self, vm_id, datastore, flavor, metadata=None, env={},
                       vnc_enabled=False, vnc_port=None,
                       **kwargs):
        cpus = int(flavor.cost["vm.cpu"].convert(Unit.COUNT))
        memory = int(flavor.cost["vm.memory"].convert(Unit.MB))
        files = FakeFileInfo(self._vm_path(datastore, vm_id))
        if env is None:
            env = {}
        else:
            env = copy.copy(env)
        env["vm.id"] = vm_id
        spec = FakeVmSpec(vm_id, memory, cpus, datastore, kwargs['image_id'],
                          flavor, files, metadata, env, vnc_enabled, vnc_port)
        return spec

    def create_vm(self, vm_id, spec):
        if vm_id in self._resources:
            raise VmAlreadyExistException()

        self._assert_not_exists(vm_id)

        # the ephemeral disk in fake disk manager doesn't co-locate with VM
        # because there is no VM folder in fake hypervisor.
        size = 1024  # hard-coded size
        try:
            for disk_id in spec.vm_disks:
                self._disk_manager().create_disk(spec.datastore, disk_id, size)
        except DatastoreOutOfSpaceException as e:
            for disk_id in spec.vm_disks:
                try:
                    self._disk_manager().delete_disk(spec.datastore, disk_id)
                except:
                    pass
            raise e
        self._resources[vm_id] = FakeVm(spec)
        self._trigger_vm_listeners()

    def update_vm_spec(self):
        return FakeUpdateSpec()

    def update_vm(self, vm_id, spec):
        vm = self._get_vm(vm_id)
        vm.update(spec)

    def has_vm(self, vm_id):
        if vm_id in self._resources:
            return True
        return False

    def add_disk(self, spec, datastore, disk_id, _):
        spec.add_device(spec.disks, disk_id)

    def remove_disk(self, spec, datastore, disk_id, _):
        spec.remove_device(spec.disks, spec.removed_disks, disk_id)

    def create_empty_disk(self, cfg_spec, datastore, disk_id, size_mb):
        cfg_spec.add_device(cfg_spec.vm_disks, disk_id)

    def create_child_disk(self, cfg_spec, datastore, disk_id, parent_id):
        cfg_spec.add_device(cfg_spec.vm_disks, disk_id)

    def add_nic(self, spec, network_id="VM Network"):
        spec.add_nic(network_id)

    def get_resource(self, vm_id):
        vm = self._get_vm(vm_id)
        resource = Vm(vm_id)
        resource.flavor = vm.fake_vm_spec.flavor
        resource.state = self._power_state_to_resource_state(vm.state)
        resource.datastore = vm.fake_vm_spec.datastore
        resource.environment = vm.fake_vm_spec.env
        return resource

    def get_resource_ids(self):
        return self._resources.keys()

    def get_power_state(self, vm_id):
        self._assert_exists(vm_id)
        vm = self._get_vm(vm_id)
        return self._power_state_to_resource_state(vm.state)

    def get_vm_network(self, vm_id):
        vm = self._get_vm(vm_id)
        network_info = []
        for nic in vm.nics:
            ip = Ipv4Address(ip_address=nic.ip_address,
                             netmask=nic.netmask)
            info = VmNetworkInfo(mac_address=nic.mac_address,
                                 is_connected=nic.connected,
                                 network=nic.network_name,
                                 ip_address=ip)
            network_info.append(info)
        return network_info

    def attach_cdrom(self, spec, iso_file, vm_id):
        vm = self._get_vm(vm_id)
        vm.add_device(vm.fake_vm_spec.cdrom, iso_file)

    def disconnect_cdrom(self, spec, vm_id):
        pass

    def detach_cdrom(self, spec, vm_id):
        vm = self._get_vm(vm_id)
        vm.fake_vm_spec.cdrom = {}

    def remove_iso(self, iso_ds_path):
        pass

    def get_used_memory_mb(self):
        memory = 0
        for _, resource in self._resources.items():
            memory += resource.fake_vm_spec.memory
        return memory

    def get_configured_cpu_count(self):
        cpus = 0
        for _, resource in self._resources.items():
            cpus += resource.fake_vm_spec.cpus
        return cpus

    def _set_nic_ip(self, spec, nics, new_nics):
        found = False
        for nic in nics:
            if nic.network_name == spec.network_name:
                new_nic = FakeNic(spec.network_name)
                if spec.ip_address:
                    new_nic.ip_address = spec.ip_address.ip_address
                    new_nic.netmask = spec.ip_address.netmask
                new_nic.network_name = spec.network_name
                new_nics.append(new_nic)
                # Remove this nic as a candidate for future set_nic_ip calls.
                nics.remove(nic)
                found = True
                break
        assert(found)
        return nics

    def set_guestinfo_ip(self, cfg_spec, info, network_connection_spec):
        if (network_connection_spec is None):
            return
        cfg_spec.gateway = network_connection_spec.default_gateway
        # Just directly update the VM object we know an update_vm is coming
        # after this. Should improve this.
        nics = info._nics
        new_nics = []
        for spec in network_connection_spec.nic_spec:
            nics = self._set_nic_ip(spec, nics, new_nics)
        info._nics = new_nics
        return True

    def set_vminfo(self, spec, vminfo):
        spec.vminfo = vminfo

    def get_vminfo(self, vm_id):
        return self._get_vm(vm_id).fake_vm_spec.vminfo

    def _power_vm(self, vm_id, expected, state):
        vm = self._resources[vm_id]
        if vm.state != expected and vm.state != state:
            raise VmPowerStateException()
        vm.state = state

    def _assert_exists(self, name):
        if name not in self._resources:
            raise VmNotFoundException("ENOENT")

    def _assert_not_exists(self, name):
        if name in self._resources:
            raise VmExistsException("EEXIST")

    def _get_vm(self, vm_id):
        self._assert_exists(vm_id)
        return self._resources[vm_id]

    def _disk_manager(self):
        return self._hypervisor.disk_manager

    def _vm_path(self, datastore, vm_id):
        return "[%s] vm_%s/%s/%s.vmx" % (datastore, vm_id, vm_id, vm_id)

    def get_vm_config(self, vm_id):
        return self._get_vm(vm_id).fake_vm_spec

    def get_vm_path(self, config):
        return config.files.vmPathName

    def get_vm_datastore(self, config):
        return config.datastore

    def customize_vm(self, spec):
        # NO-OP
        pass

    # TODO(vui): Fix modelling of child root disk correctly, then
    # this can be made less arbitrary.
    def _get_root_child_disk_id(self, disks):
        if disks:
            for k, v in disks.iteritems():
                if v:
                    return k
        return None

    def get_linked_clone_path(self, vm_id):
        spec = self._get_vm(vm_id).fake_vm_spec
        datastore_id = spec.datastore
        image_id = spec.image_id
        if image_id is None:
            return None
        else:
            root_child_disk_id = self._get_root_child_disk_id(
                spec.disks)
            if root_child_disk_id:
                return "[%s] vms/%s/%s/%s.vmx" % (
                    datastore_id, vm_id[0:2], vm_id, root_child_disk_id)
            else:
                return None

    def get_linked_clone_image_path(self, vm_id):
        spec = self._get_vm(vm_id).fake_vm_spec
        datastore_id = spec.datastore
        image_id = spec.image_id
        if image_id is None:
            return None
        else:
            return self._hypervisor.image_manager.get_image_refcount_filename(
                datastore_id,
                image_id)

    def set_vnc_port(self, spec, port):
        spec.vnc_enabled = True
        spec.port = port
        return spec

    def get_vnc_port(self, vm_id):
        spec = self._get_vm(vm_id).fake_vm_spec
        if spec.vnc_enabled:
            return spec.port
        else:
            return None

    def get_occupied_vnc_ports(self):
        return set([vm.fake_vm_spec.port
                    for vm in self._resources.values()
                    if vm.fake_vm_spec.vnc_enabled])

    def get_mks_ticket(self, vm_id):
        if self._get_vm(vm_id).state != 'on':
            raise OperationNotAllowedException()

        return MksTicket(cfg_file='/vmfs/volumes/%s.vmdk' % vm_id,
                         ssl_thumbprint='33:F5:6F:FB:07:A9:EB:6E:5E:9B:E6:AF'
                                        ':00:BB:DA:69:FF:BA:20:34',
                         port=902,
                         ticket='52579d02-a80d-55eb-648b-f7ae461a7505')


class FakeNic(object):
    """
    Fake Nic object to capture network information.
    """
    _rnd = Random()
    _next_ip = 2
    _next_mac = 0

    @staticmethod
    def seed(seed):
        FakeNic._rnd.seed(seed)

    @staticmethod
    def generate_ip(addr_family="IPv4"):
        """Method to generate a v4 or v6 ip address
        """
        if addr_family == "IPv4":
            addr = "127.%d.%d.%d" % ((FakeNic._next_ip >> 16) & 0xff,
                                     (FakeNic._next_ip >> 8) & 0xff,
                                     FakeNic._next_ip & 0xff)
            FakeNic._next_ip += 1
            return addr
        else:
            ipv6 = [0x0000, 0x000a, 0x000b, 0x000c,
                    FakeNic._rnd.randint(0x0000, 0xffff),
                    FakeNic._rnd.randint(0x0000, 0xffff),
                    FakeNic._rnd.randint(0x0000, 0xffff),
                    FakeNic._rnd.randint(0x0000, 0xffff)]
            return ':'.join(map(lambda x: "%04x" % x, ipv6))

    @staticmethod
    def generate_netmask(addr_family="IPv4"):
        """Returns the netmask for the generated ip addresses.
        """
        if addr_family == "IPv4":
            return "255.0.0.0"
        else:
            # Return prefix length
            return "64"

    @staticmethod
    def generate_mac(prefix=None):
        """Utility method to generate a mac address.
        """
        prefix = prefix or [0x00, 0x0c, 0x29]  # Assigned to VMware
        FakeNic._next_mac += 1
        n = FakeNic._next_mac
        assert n < (1 << 24)
        mac = prefix + [(n >> 16) % 256, (n >> 8) % 256, n % 256]
        FakeNic._next_mac += 1
        return ':'.join(map(lambda x: "%02x" % x, mac))

    def __init__(self, network_name):
        FakeNic.seed(0)
        self.network_name = network_name
        self.ip_address = FakeNic.generate_ip()
        self.netmask = FakeNic.generate_netmask()
        self.mac_address = FakeNic.generate_mac()
        self.connected = ConnectedStatus.CONNECTED


class FakeUpdateSpec(object):
    def __init__(self):
        self.disks = {}
        self._nics = []
        self.removed_disks = {}
        self.gateway = ""
        self.cdrom = {}

    def add_device(self, devices, device_id):
        if device_id in devices:
            raise ValueError("EEXIST")
        devices[device_id] = True

    def add_nic(self, network_name):
        self._nics.append(FakeNic(network_name))

    def remove_device(self, devices, removed_devices, device_id):
        if device_id not in devices:
            removed_devices[device_id] = True
            return
        del devices[device_id]


class FakeVmSpec(FakeUpdateSpec):
    def __init__(self, name, memory, cpus, datastore, image_id, flavor, files,
                 metadata=None, env={}, vnc_enabled=False, vnc_port=None,
                 vminfo={}):
        super(FakeVmSpec, self).__init__()
        self.name = name
        self.memory = memory
        self.cpus = cpus
        self.datastore = datastore
        self.image_id = image_id
        self.flavor = flavor
        self.vm_disks = {}
        self.files = files
        self.metadata = metadata
        self.env = env
        self.vnc_enabled = vnc_enabled
        self.vnc_port = vnc_port
        self.vminfo = vminfo

    def update(self, spec):
        assert(isinstance(spec, FakeUpdateSpec))
        new_disks = self.disks
        for device in spec.disks:
            self.add_device(new_disks, device)

        removed_device = {}
        for device in spec.removed_disks:
            self.remove_device(new_disks, self.removed_disks, device)

        # Check that we are not removing a device that doesn't exist.
        if (removed_device):
            raise ValueError("ENOENT")

        self.gateway = spec.gateway
        self.cdrom = spec.cdrom
        self._nics.extend(spec._nics)


class FakeFileInfo(object):
    def __init__(self, vmPathName):
        self.vmPathName = vmPathName


class FakeVm(object):
    def __init__(self, fake_vm_spec):
        # We can't remove a device when we don't have a VM yet
        if (fake_vm_spec.removed_disks):
            raise ValueError("ENOENT")
        # Simulate the creation of the VM with the new spec.
        self.fake_vm_spec = fake_vm_spec
        self.state = "off"

    def update(self, spec):
        self.fake_vm_spec.update(spec)

    @property
    def nics(self):
        return self.fake_vm_spec._nics


class VmExistsException(Exception):
    pass
