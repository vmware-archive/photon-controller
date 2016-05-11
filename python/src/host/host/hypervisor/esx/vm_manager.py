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

""" Contains the implementation code for ESX VM operations."""
import logging
import os
import socket
import struct
import threading

from common.kind import Flavor
from common.kind import Unit
from gen.agent.ttypes import PowerState
from gen.host.ttypes import ConnectedStatus
from gen.host.ttypes import VmNetworkInfo
from gen.host.ttypes import Ipv4Address
from gen.resource.ttypes import MksTicket
from host.hypervisor.resources import Disk
from host.hypervisor.resources import Resource
from host.hypervisor.resources import State
from host.hypervisor.resources import Vm
from host.hypervisor.vm_manager import OperationNotAllowedException
from host.hypervisor.vm_manager import VmManager
from host.hypervisor.vm_manager import IsoNotAttachedException
from host.hypervisor.vm_manager import VmNotFoundException
from host.hypervisor.vm_manager import VmPowerStateException
from host.hypervisor.esx.host_client import DeviceNotFoundException
from host.hypervisor.esx.path_util import compond_path_join
from host.hypervisor.esx.path_util import datastore_to_os_path
from host.hypervisor.esx.path_util import os_datastore_path
from host.hypervisor.esx.path_util import VM_FOLDER_NAME_PREFIX
from host.hypervisor.esx.path_util import SHADOW_VM_NAME_PREFIX
from host.hypervisor.esx.path_util import get_root_disk
from host.hypervisor.esx.path_util import is_persistent_disk
from host.hypervisor.esx.vm_config import EsxVmConfigSpec
from host.hypervisor.esx.vm_config import EsxVmInfo

from common.log import log_duration


class NetUtil(object):
    """ Network utility classes for dealing with vmomi dataobjects
        We don't have Ipv4Address python packages on esx.
    """

    @staticmethod
    def is_ipv4_address(ip_address):
        """Utility method to check if an ip address is ipv4
        :param ip_addres: string ip address
        :rtype: bool, return True if the ip_address is a v4 address
        """
        try:
            socket.inet_aton(ip_address)
        except socket.error:
            return False
        return ip_address.count('.') == 3

    @staticmethod
    def prefix_len_to_mask(prefix_len):
        """Utility method to convert prefix length to netmask
        IpV4address pkg is not available on esx.
        :param prefix_len: int prefix len
        :rtype: string, string representation of the netmask
        """
        if (prefix_len < 0 or prefix_len > 32):
            raise ValueError("Invalid prefix length")
        mask = (1L << 32) - (1L << 32 >> prefix_len)

        return socket.inet_ntoa(struct.pack('>L', mask))


class EsxVmManager(VmManager):

    """ESX VM Manager specific implementation.

    This will be used by host/vm_manager.py if the agent has selected to use
    the ESX hypervisor on boot. This class contains all methods for VM power
    operations.

    Attributes:
        vim_client: The VimClient instance.
        _logger: The global _logger to log messages to.

    """

    GUESTINFO_PREFIX = "guestinfo.esxcloud."
    VMINFO_PREFIX = "photon_controller.vminfo."
    METADATA_EXTRA_CONFIG_KEYS = (
        'bios.bootOrder', 'monitor.suspend_on_triplefault'
        # More TBA ...
    )

    def __init__(self, vim_client, ds_manager):
        self.vim_client = vim_client
        self._logger = logging.getLogger(__name__)
        self._ds_manager = ds_manager
        self._lock = threading.Lock()
        self._datastore_cache = {}

    @staticmethod
    def _power_state_to_resource_state(power_state):
        return {
            PowerState.poweredOn: State.STARTED,
            PowerState.poweredOff: State.STOPPED,
            PowerState.suspended: State.SUSPENDED
        }[power_state]

    def power_on_vm(self, vm_id):
        self.vim_client.power_on_vm(vm_id)

    def power_off_vm(self, vm_id):
        self.vim_client.power_off_vm(vm_id)

    def reset_vm(self, vm_id):
        self.vim_client.reset_vm(vm_id)

    def suspend_vm(self, vm_id):
        self.vim_client.suspend_vm(vm_id)

    def resume_vm(self, vm_id):
        self.vim_client.resume_vm(vm_id)

    def _get_extra_config_map(self, metadata):
        # this can be simplified if the metadata dictionary follows some
        # convention in describing extra config properties
        if metadata is None:
            return {}
        return dict((k, v) for (k, v) in metadata.items() if k in
                    self.METADATA_EXTRA_CONFIG_KEYS)

    @log_duration
    def create_vm_spec(self, vm_id, datastore, flavor, metadata=None, env={}):
        """Create a new Virtual Machine create spec.

        :param vm_id: Name of the VM
        :type vm_id: str
        :param datastore: Name of the VM's datastore
        :type datastore: str
        :param flavor: VM flavor
        :type flavor: Flavor
        :param metadata: VM creation metadata
        """

        # TODO(vspivak): long term introduce separate config (from cost) for
        # the hypervisor sizing meta
        cpus = int(flavor.cost["vm.cpu"].convert(Unit.COUNT))
        memory = int(flavor.cost["vm.memory"].convert(Unit.MB))
        spec = EsxVmConfigSpec(self.vim_client.query_config())
        spec.init_for_create(vm_id, datastore, memory, cpus, metadata, env)

        extra_config_map = self._get_extra_config_map(spec._metadata)
        # our one vm-identifying extra config
        extra_config_map[self.GUESTINFO_PREFIX + "vm.id"] = vm_id
        spec.set_extra_config(extra_config_map)

        spec.set_diskuuid_enabled(True)
        return spec

    @log_duration
    def _update_vm_spec(self):
        """ Return an empty update spec for a VM.
        """
        spec = EsxVmConfigSpec(self.vim_client.query_config())
        spec.init_for_update()
        return spec

    @log_duration
    def create_vm(self, vm_id, spec):
        """Create a new Virtual Maching given a VM create spec.

        :param vm_id: The Vm id
        :type vm_id: string
        :param spec: The VM spec builder
        :type ConfigSpec
        :raise: VmAlreadyExistException
        """
        self.vim_client.create_vm(vm_id, spec.get_spec())

    @log_duration
    def _update_vm(self, vm_id, spec):
        """ Update the VM using the given spec.
        :type spec: vim.vm.ConfigSpec
        """
        vm = self.vim_client.get_vm(vm_id)
        self.vim_client.reconfigure_vm(vm, spec.get_spec())

    def _ensure_directory_cleanup(self, vm_dir):
        # Upon successful destroy of VM, log any stray files still left in the
        # VM directory and delete the directory.
        if os.path.isdir(vm_dir):
            # log any stray files still left in the VM directory
            try:
                target_dir = vm_dir
                if os.path.islink(vm_dir):
                    target_dir = os.readlink(vm_dir)
                if os.path.isdir(target_dir):   # check link-target exists and is dir
                    files = os.listdir(target_dir)
                    for f in files:
                        if f.endswith(".vmdk"):
                            self._logger.info("Stray disk (possible data leak): %s" % f)
                        else:
                            self._logger.info("Stray file: %s" % f)
            except:
                pass

            # delete the directory
            self._logger.warning("Force delete vm directory %s" % vm_dir)
            self.vim_client.delete_file(vm_dir)

    @log_duration
    def delete_vm(self, vm_id, force=False):
        """Delete a Virtual Machine

        :param vm_id: Name of the VM
        :type vm_id: str
        :param force: Not to check persistent disk, forcefully delete vm.
        :type force: boolean
        :raise VmPowerStateException when vm is not powered off
        """
        vm = self.vim_client.get_vm(vm_id)
        if vm.runtime.powerState != 'poweredOff':
            raise VmPowerStateException("Can only delete vm in state %s" %
                                        vm.runtime.powerState)

        # Getting the path for the new dir structure if we have upgraded from older structure
        datastore_id = self._get_vm_datastore(vm.config)
        vm_path = os_datastore_path(datastore_id, compond_path_join(VM_FOLDER_NAME_PREFIX, vm_id))

        if not force:
            self._verify_disks(vm)

        self._logger.info("Destroy VM at %s" % vm_path)
        self.vim_client.destroy_vm(vm)

        self._ensure_directory_cleanup(vm_path)

        self.vim_client.wait_for_vm_delete(vm_id)

    @log_duration
    def has_vm(self, vm_id):
        try:
            self.vim_client.get_vm_in_cache(vm_id)
            return True
        except VmNotFoundException:
            return False

    @log_duration
    def attach_disk(self, vm_id, vmdk_file):
        """Add an existing disk to a VM
        :param vm_id: VM id
        :type vm_id: str
        :param vmdk_file: vmdk disk path
        :type vmdk_file: str
        """
        cfg_spec = self._update_vm_spec()
        cfg_info = self._get_vm_config(vm_id)
        cfg_spec.attach_disk(cfg_info, vmdk_file)
        self._update_vm(vm_id, cfg_spec)

    def detach_disk(self, vm_id, disk_id):
        """Remove an existing disk from a VM
        :param vm_id: Vm id
        :type vm_id: str
        :param disk_id: Disk id
        :type disk_id: str
        """
        cfg_spec = self._update_vm_spec()
        cfg_info = self._get_vm_config(vm_id)
        cfg_spec.detach_disk(cfg_info, disk_id)
        self._update_vm(vm_id, cfg_spec)

    def _get_datastore_uuid(self, name):
        try:
            return self._ds_manager.normalize(name)
        except:
            # The exception usually happens when the agent is not
            # provisioned with the right configurations, especially in
            # integration test.
            self._logger.exception("Failed to get uuid for %s" % name)
            return None

    @log_duration
    def get_resources(self):
        resources = []
        vms = self.vim_client.get_vms_in_cache()
        for vm in vms:
            vm_resource = self._get_resource_from_vmcache(vm)
            if vm_resource.datastore:
                resources.append(Resource(vm_resource, vm_resource.disks))
        return resources

    def get_power_state(self, vm_id):
        vm = self.vim_client.get_vm_in_cache(vm_id)
        return self._power_state_to_resource_state(vm.power_state)

    @log_duration
    def get_resource(self, vm_id):
        vmcache = self.vim_client.get_vm_in_cache(vm_id)
        return self._get_resource_from_vmcache(vmcache)

    def _get_resource_from_vmcache(self, vmcache):
        """Translate to vm resource from vm cache
        """
        vm_resource = Vm(vmcache.name)
        vm_resource.flavor = Flavor("default")  # TODO
        vm_resource.disks = []

        for disk in vmcache.disks:
            disk_id = os.path.splitext(os.path.basename(disk))[0]
            datastore_name = self._get_datastore_name_from_ds_path(disk)
            datastore_uuid = self._get_datastore_uuid(datastore_name)
            if datastore_uuid:
                disk_resource = Disk(disk_id, Flavor("default"), False,
                                     False, -1, None, datastore_uuid)
                vm_resource.disks.append(disk_resource)

        vm_resource.state = self._power_state_to_resource_state(
            vmcache.power_state)

        datastore_name = self._get_datastore_name_from_ds_path(vmcache.path)
        vm_resource.datastore = self._get_datastore_uuid(datastore_name)

        return vm_resource

    def get_resource_ids(self):
        return self.vim_client.get_vm_resource_ids()

    def get_used_memory_mb(self):
        vms = self.vim_client.get_vms_in_cache()
        if not vms:
            return 0

        memory = 0
        for vm in vms:
            # Vms in cache might include half updated record, e.g. with
            # None memory_mb, for a short time windows. Those Vms in cache
            # could be excluded from total used memory.
            if vm.name and vm.name.startswith(SHADOW_VM_NAME_PREFIX):
                # skip shadow vm, because we never power it on
                self._logger.info("skip shadow vm: %s" % vm.name)
            elif vm.memory_mb:
                memory += vm.memory_mb

        # This indicates that no values were retrieved from the cache.
        if memory == 0:
            raise VmNotFoundException("No valid VMs were found")

        return memory

    def get_configured_cpu_count(self):
        """
        Returns the total number of vCPUs across all VMs
        :return: number of vCPUs - int
        """
        vms = self.vim_client.get_vms_in_cache()
        if not vms:
            return 0

        cpu_count = 0
        for vm in vms:
            if vm.name and vm.name.startswith(SHADOW_VM_NAME_PREFIX):
                # skip shadow vm, because we never power it on
                self._logger.info("skip shadow vm: %s" % vm.name)
            elif vm.num_cpu:
                cpu_count += vm.num_cpu

        # This indicates that no values were retrieved from the cache.
        if cpu_count == 0:
            raise VmNotFoundException("No valid VMs were found")

        return cpu_count

    def _get_datastore_name_from_ds_path(self, vm_path):
        try:
            return vm_path[vm_path.index("[") + 1:vm_path.index("]")]
        except:
            self._logger.warning("vm_path %s is malformated" % vm_path)
            raise

    def _verify_disks(self, vm):
        persistent_disks = [
            disk for disk in vm.layout.disk
            if is_persistent_disk(disk.diskFile)
        ]

        if persistent_disks:
            raise OperationNotAllowedException("persistent disks attached")

    @log_duration
    def get_vm_network(self, vm_id):
        """ Get the vm's network information
        We only report ip info if vmware tools is running within the guest.
        If tools are not running we can only report back the mac address
        assigned by the vmx, the connected status of the device and the network
        attached to the device.
        The information for mac, networkname and connected status is available
        through two places, the ethernetCards backing info and through the
        guestInfo. Both of these codepaths are not using VimVigor and seem to
        be implemented in a similar manner in hostd, so they should agree with
        each other. Just read this from the guestInfo as well.

        :param vm_id: Name of the VM
        :rtype: VmNetworkInfo
        """
        network_info = []

        # Throws when VM is not found.
        vm = self.vim_client.get_vm(vm_id)

        if (vm.guest is None or not vm.guest.net):
            # No guest info so return the info from the config file
            return self._get_network_config(vm_id)

        guest_nic_info_list = vm.guest.net

        # vmomi list attrs are never None could be an empty list
        for guest_nic_info in guest_nic_info_list:
            if (guest_nic_info.macAddress is None):
                # No mac address no real guest info. Not possible to have mac
                # address not reporte but ip stack info available.
                continue
            info = VmNetworkInfo(mac_address=guest_nic_info.macAddress)

            # Fill in the connected status.
            if guest_nic_info.connected:
                info.is_connected = ConnectedStatus.CONNECTED
            else:
                info.is_connected = ConnectedStatus.DISCONNECTED

            # Fill in the network binding info
            if guest_nic_info.network is not None:
                info.network = guest_nic_info.network

            # See if the ip information is available.
            if guest_nic_info.ipConfig is not None:
                ip_addresses = guest_nic_info.ipConfig.ipAddress
                # This is an array due to ipv6 support
                for ip_address in ip_addresses:
                    if (NetUtil.is_ipv4_address(ip_address.ipAddress)):
                        ip = Ipv4Address(
                            ip_address=ip_address.ipAddress,
                            netmask=NetUtil.prefix_len_to_mask(
                                ip_address.prefixLength))
                        info.ip_address = ip
                        break
            network_info.append(info)

        return network_info

    def attach_cdrom(self, iso_file, vm_id):
        """ Attach an iso file to the VM after adding a CD-ROM device.

        :param spec: The VM config spec to update with the cdrom add
        :type spec: vim.vm.ConfigSpec
        :param iso_file: the file system path to the cdrom
        :type iso_file: str
        :param vm_id: The id of VM to attach iso from
        :type vm_id: str
        :rtype: bool. True if success, False if failure
        """
        vm = self.vim_client.get_vm(vm_id)
        if vm.config is None:
            raise Exception("Invalid VM config")

        cfg_spec = self._update_vm_spec()
        result = cfg_spec.add_iso_cdrom(iso_file, vm.config)
        if result:
            self._update_vm(vm_id, cfg_spec)
        return result

    def disconnect_cdrom(self, vm_id):
        """ Disconnect cdrom device from VM

        :param spec: The VM config spec to update with the cdrom change
        :type spec: vim.vm.ConfigSpec
        :param vm_id: The id of VM to detach iso from
        :type vm_id: str
        """
        vm = self.vim_client.get_vm(vm_id)
        if vm.config is None:
            raise Exception("Invalid VM config")

        try:
            cfg_spec = self._update_vm_spec()
            iso_path = cfg_spec.disconnect_iso_cdrom(vm.config)
            self._update_vm(vm_id, cfg_spec)
        except DeviceNotFoundException, e:
            raise IsoNotAttachedException(e)
        except TypeError, e:
            raise IsoNotAttachedException(e)

        return iso_path

    def remove_iso(self, iso_ds_path):
        try:
            os.remove(datastore_to_os_path(iso_ds_path))
        except:
            # The iso may not exist, so just catch and move on.
            pass

    @log_duration
    def _get_network_config(self, vm_id):
        """ Get the network backing of a VM by reading its configuration.

        This is different from the get_vm_network above which gets the network
        information from tools.
        Only the mac address and the corresponding network name is going to be
        populated in this model.
        :type vm_id: VM str
        :rtype VMNetworkInfo list.
        """

        network_info = []
        vm_info = EsxVmInfo(self.vim_client.get_vm(vm_id))
        networks = vm_info.get_networks()

        for idx, mac, network, _ in networks:
            # We don't set MAC address when VM gets created, so MAC address
            # won't be set until the VM gets powered on.
            info = VmNetworkInfo(mac_address=mac, network=network)
            network_info.append(info)
        return network_info

    def _get_vm_config(self, vm_id):
        """ Get the config info of a VM. """
        vm = self.vim_client.get_vm(vm_id)
        return vm.config

    def _get_vm_datastore(self, config):
        """ Get the datastore id to the VM's config file.

        The VM can have file components residing on other datastores as well,
        but this call is implemented by design to only return the datastore
        in which the config file resides.
        """
        vmx = config.files.vmPathName
        datastore_name = self._get_datastore_name_from_ds_path(vmx)
        if self._ds_manager is not None:
            return self._ds_manager.normalize(datastore_name)
        return datastore_name

    @log_duration
    def get_linked_clone_path(self, vm_id):
        """Get the absolute path of a VM linked clone disk

        :param vm_id: VM ID as a string.
        :return: absolute path to the linked clone disk, or None if the VM
                 doesn't exist in the cache or was created with full clone.
        """
        vm = self.vim_client.get_vm_in_cache(vm_id)
        if not vm or not vm.disks:
            self._logger.debug("Image disk not found for %s: %s" % (vm_id, vm))
            return None
        return get_root_disk(vm.disks)

    def get_mks_ticket(self, vm_id):
        vm = self.vim_client.get_vm(vm_id)
        if vm.runtime.powerState != 'poweredOn':
            raise OperationNotAllowedException('Not allowed on vm that is '
                                               'not powered on.')
        mks = vm.AcquireMksTicket()
        return MksTicket(cfg_file=mks.cfgFile,
                         host=mks.host,
                         port=mks.port,
                         ssl_thumbprint=mks.sslThumbprint,
                         ticket=mks.ticket)
