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

from host.hypervisor.esx.host_client import HostClient
import attache


class AttacheClient(HostClient):
    def __init__(self, auto_sync):
        self._logger = logging.getLogger(__name__)
        self._logger.info("AttacheClient init")
        self._auto_sync = auto_sync

        pass

    """ Connect and setup
    """
    def connect_local(self):
        attache.AttacheInit()

    def connect_userpwd(self, host, user, pwd):
        pass

    def connect_ticket(self, host, ticket):
        pass

    def disconnect(self, wait=False):
        pass

    def add_update_listener(self, listener):
        pass

    def remove_update_listener(self, listener):
        pass

    def query_config(self):
        pass

    """ Vm operations
    """
    def create_vm(self, vm_id, create_spec):
        pass

    def export_vm(self, vm_id):
        pass

    def import_vm(self, spec):
        pass

    def get_vms(self):
        pass

    def get_vms_in_cache(self):
        pass

    def get_vm_in_cache(self, vm_id):
        pass

    def get_vm_resource_ids(self):
        pass

    def power_on_vm(self, vm_id):
        pass

    def power_off_vm(self, vm_id):
        pass

    def reset_vm(self, vm_id):
        pass

    def suspend_vm(self, vm_id):
        pass

    def resume_vm(self, vm_id):
        pass

    def attach_disk(self, vm_id, vmdk_file):
        pass

    def detach_disk(self, vm_id, disk_id):
        pass

    def attach_iso(self, vm_id, iso_file):
        pass

    def detach_iso(self, vm_id):
        pass

    def get_mks_ticket(self, vm_id):
        pass

    def unregister_vm(self, vm_id):
        pass

    def delete_vm(self, vm_id, force):
        pass

    """ Disk and file operations
    """
    def create_disk(self, path, size):
        pass

    def copy_disk(self, src, dst):
        pass

    def move_disk(self, src, dst):
        pass

    def delete_disk(self, path):
        pass

    def set_disk_uuid(self, path, uuid):
        pass

    def query_disk_uuid(self, path):
        pass

    def make_directory(self, path):
        pass

    def delete_file(self, path):
        pass

    def move_file(self, src, dest):
        pass

    """ Host management
    """
    def memory_usage_mb(self):
        pass

    def total_vmusable_memory_mb(self):
        pass

    def num_physical_cpus(self):
        pass

    def about(self):
        pass

    def get_nfc_ticket_by_ds_name(self, datastore):
        pass

    def acquire_clone_ticket(self):
        pass

    def set_large_page_support(self, disable=False):
        pass

    """ Datastore
    """
    def get_datastore(self, name):
        pass

    def get_all_datastores(self):
        pass

    """ Network
    """
    def get_networks(self):
        pass

    def get_network_configs(self):
        pass

    """ Stats
    """
    def query_stats(self, entity, metric_names, sampling_interval, start_time, end_time=None):
        pass
