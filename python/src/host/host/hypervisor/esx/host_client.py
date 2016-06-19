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


class HostdConnectionFailure(Exception):
    pass


class DatastoreNotFound(Exception):
    pass


class DeviceNotFoundException(Exception):
    pass


class DeviceBusyException(Exception):
    pass


class NfcLeaseInitiatizationTimeout(Exception):
    """ Timed out waiting for the HTTP NFC lease to initialize. """
    pass


class NfcLeaseInitiatizationError(Exception):
    """ Error waiting for the HTTP NFC lease to initialize. """
    pass


class UpdateListener(object):
    """
    Abstract base class for host update listener.

    IMPORTANT: The underlying hypervisor holds a lock while notifying
    listeners, so these callbacks should be reasonably light-weight.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def datastores_updated(self):
        """Gets called when tehre is a change in the list of datastores."""
        pass


class HostClient(object):
    __metaclass__ = abc.ABCMeta

    """ Connect and setup
    """
    @abc.abstractmethod
    def connect_local(self):
        pass

    @abc.abstractmethod
    def connect_userpwd(self, host, user, pwd):
        pass

    @abc.abstractmethod
    def connect_ticket(self, host, ticket):
        pass

    @abc.abstractmethod
    def disconnect(self):
        pass

    @abc.abstractmethod
    def add_update_listener(self, listener):
        pass

    @abc.abstractmethod
    def remove_update_listener(self, listener):
        pass

    @abc.abstractmethod
    def query_config(self):
        pass

    """ Vm operations
    """
    @abc.abstractmethod
    def create_vm_spec(self, vm_id, datastore, memoryMB, nCPU, metadata, env):
        pass

    @abc.abstractmethod
    def create_vm(self, vm_id, create_spec):
        pass

    @abc.abstractmethod
    def get_vms_in_cache(self):
        pass

    @abc.abstractmethod
    def get_vm_in_cache(self, vm_id):
        pass

    @abc.abstractmethod
    def get_vm_resource_ids(self):
        pass

    @abc.abstractmethod
    def power_on_vm(self, vm_id):
        pass

    @abc.abstractmethod
    def power_off_vm(self, vm_id):
        pass

    @abc.abstractmethod
    def reset_vm(self, vm_id):
        pass

    @abc.abstractmethod
    def suspend_vm(self, vm_id):
        pass

    @abc.abstractmethod
    def attach_disk(self, vm_id, vmdk_file):
        pass

    @abc.abstractmethod
    def detach_disk(self, vm_id, disk_id):
        pass

    @abc.abstractmethod
    def attach_iso(self, vm_id, iso_file):
        pass

    @abc.abstractmethod
    def detach_iso(self, vm_id):
        pass

    @abc.abstractmethod
    def attach_virtual_network(self, vm_id, network_id):
        pass

    @abc.abstractmethod
    def get_mks_ticket(self, vm_id):
        pass

    @abc.abstractmethod
    def get_vm_networks(self, vm_id):
        pass

    @abc.abstractmethod
    def unregister_vm(self, vm_id):
        pass

    @abc.abstractmethod
    def delete_vm(self, vm_id, force):
        pass

    """ Disk and file operations
    """
    @abc.abstractmethod
    def create_disk(self, path, size):
        pass

    @abc.abstractmethod
    def copy_disk(self, src, dst):
        pass

    @abc.abstractmethod
    def move_disk(self, src, dst):
        pass

    @abc.abstractmethod
    def delete_disk(self, path):
        pass

    @abc.abstractmethod
    def set_disk_uuid(self, path, uuid):
        pass

    @abc.abstractmethod
    def query_disk_uuid(self, path):
        pass

    @abc.abstractmethod
    def make_directory(self, path):
        pass

    @abc.abstractmethod
    def delete_file(self, path):
        pass

    @abc.abstractmethod
    def move_file(self, src, dest):
        pass

    """ Host management
    """
    @abc.abstractmethod
    def memory_usage_mb(self):
        pass

    @abc.abstractmethod
    def total_vmusable_memory_mb(self):
        pass

    @abc.abstractmethod
    def num_physical_cpus(self):
        pass

    @abc.abstractmethod
    def host_version(self):
        pass

    @abc.abstractmethod
    def set_large_page_support(self, disable=False):
        pass

    """ Datastore
    """
    @abc.abstractmethod
    def get_datastore_in_cache(self, name):
        pass

    @abc.abstractmethod
    def get_all_datastores(self):
        pass

    """ Network
    """
    @abc.abstractmethod
    def get_networks(self):
        pass

    """ Nfc
    """
    @abc.abstractmethod
    def get_nfc_ticket_by_ds_name(self, datastore):
        pass

    @abc.abstractmethod
    def nfc_copy(self, src_file_path, dst_host, dst_file_path, ssl_thumbprint, ticket):
        pass

    """ Stats
    """
    @abc.abstractmethod
    def query_stats(self, entity, metric_names, sampling_interval, start_time, end_time=None):
        pass


class VmConfigSpec(object):
    @abc.abstractmethod
    def create_empty_disk(self, disk_id, size_mb):
        pass

    @abc.abstractmethod
    def create_child_disk(self, disk_id, parent_vmdk_path):
        pass

    @abc.abstractmethod
    def add_nic(self, network):
        pass

    @abc.abstractmethod
    def set_extra_config(self, options):
        pass

    @abc.abstractmethod
    def get_metadata(self):
        pass
