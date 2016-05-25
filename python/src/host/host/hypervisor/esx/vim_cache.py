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
import copy
import logging
import threading
import weakref
import time
import sys

from common.lock import lock_with
from common.blocking_dict import BlockingDict
from gen.agent.ttypes import VmCache, TaskCache, PowerState, TaskState
from host.hypervisor.vm_manager import VmNotFoundException

from pyVmomi import vim
from pyVmomi import vmodl


class VimCache:
    """ Initialization
    """
    def __init__(self, vim_client):
        self._logger = logging.getLogger(__name__)

        self._filter = None
        self._current_version = None
        self._vm_cache = {}
        self._vm_name_to_ref = BlockingDict()
        self._ds_name_properties = {}
        self._lock = threading.RLock()
        self._task_cache = BlockingDict()

    """ Create filter
    """
    def _datastore_filter_spec(self, vim_client):
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(name="folderTraversalSpec", type=vim.Folder, path="childEntity", skip=False)
        property_spec = PC.PropertySpec(type=vim.Datastore, pathSet=["name"])
        from host.hypervisor.esx.vim_client import DATASTORE_FOLDER_NAME
        object_spec = PC.ObjectSpec(obj=vim_client._find_by_inventory_path(DATASTORE_FOLDER_NAME),
                                    selectSet=[traversal_spec])
        return PC.FilterSpec(propSet=[property_spec], objectSet=[object_spec])

    def _vm_filter_spec(self, vim_client):
        PC = vmodl.query.PropertyCollector
        traversal_spec = PC.TraversalSpec(name="folderTraversalSpec", type=vim.Folder, path="childEntity", skip=False)
        property_spec = PC.PropertySpec(type=vim.VirtualMachine,
                                        pathSet=["name", "runtime.powerState", "layout.disk", "config"])
        object_spec = PC.ObjectSpec(obj=vim_client._vm_folder(), selectSet=[traversal_spec])
        return PC.FilterSpec(propSet=[property_spec], objectSet=[object_spec])

    def _task_filter_spec(self, vim_client):
        PC = vmodl.query.PropertyCollector
        task_property_spec = PC.PropertySpec(type=vim.Task, pathSet=["info.error", "info.state"])
        task_traversal_spec = PC.TraversalSpec(name="taskSpec", type=vim.TaskManager, path="recentTask", skip=False)
        task_object_spec = PC.ObjectSpec(obj=vim_client._content.taskManager, selectSet=[task_traversal_spec])
        return PC.FilterSpec(propSet=[task_property_spec], objectSet=[task_object_spec])

    def _build_filter_spec(self, vim_client):
        PC = vmodl.query.PropertyCollector
        ds_spec = self._datastore_filter_spec(vim_client)
        task_spec = self._task_filter_spec(vim_client)
        vm_spec = self._vm_filter_spec(vim_client)
        propSet = ds_spec.propSet + task_spec.propSet + vm_spec.propSet
        objectSet = ds_spec.objectSet + task_spec.objectSet + vm_spec.objectSet
        return PC.FilterSpec(propSet=propSet, objectSet=objectSet)

    """ Poll updates
    """
    def poll_updates(self, vim_client, timeout=10):
        """Polling on VM updates on host. This call will block caller until
        update of VM is available or timeout.
        :param timeout: timeout in seconds
        """
        if not self._filter:
            filter_spec = self._build_filter_spec(vim_client)
            self._filter = vim_client._property_collector.CreateFilter(filter_spec, partialUpdates=False)

        wait_options = vmodl.query.PropertyCollector.WaitOptions()
        wait_options.maxWaitSeconds = timeout
        update = vim_client._property_collector.WaitForUpdatesEx(self._current_version, wait_options)
        self._update_cache(vim_client, update)
        if update:
            self._current_version = update.version
        return update

    def _apply_ds_update(self, obj_update):
        ds_key = str(obj_update.obj)
        updated = False
        if obj_update.kind == "enter" or obj_update.kind == "modify":
            for change in obj_update.changeSet:
                if change.name == "name":
                    ds_name = change.val
                    self._logger.debug("cache update: %s ds name %s" % (obj_update.kind, ds_name))
                    if ds_key not in self._ds_name_properties or self._ds_name_properties[ds_key] != ds_name:
                        updated = True
                    self._ds_name_properties[ds_key] = ds_name
        elif obj_update.kind == "leave":
            self._logger.debug("cache update: remove ds ref %s" % ds_key)
            if ds_key in self._ds_name_properties:
                del self._ds_name_properties[ds_key]
                updated = True
        return updated

    @lock_with("_lock")
    def _update_cache(self, vim_client, update):
        if not update or not update.filterSet:
            return

        ds_updated = False
        for filter in update.filterSet:
            for object in filter.objectSet:
                # Update Vm cache
                if isinstance(object.obj, vim.VirtualMachine):
                    if object.kind == "enter":
                        # Possible to have 2 enters for one object
                        self._add_or_modify_vm_cache(object)
                    elif object.kind == "leave":
                        assert str(object.obj) in self._vm_cache, "%s not in cache for kind leave" % object.obj
                        self._remove_vm_cache(object)
                    elif object.kind == "modify":
                        assert str(object.obj) in self._vm_cache, "%s not in cache for kind modify" % object.obj
                        self._add_or_modify_vm_cache(object)
                # Update task cache
                elif isinstance(object.obj, vim.Task):
                    if object.kind == "enter":
                        self._update_task_cache(object)
                    elif object.kind == "leave":
                        self._remove_task_cache(object)
                    elif object.kind == "modify":
                        self._update_task_cache(object)
                elif isinstance(object.obj, vim.Datastore):
                    self._logger.debug("Datastore update: %s" % object)
                    updated = self._apply_ds_update(object)
                    ds_updated = ds_updated or updated

        # Notify listeners.
        if ds_updated:
            for listener in vim_client.update_listeners:
                self._logger.debug("datastores updated for listener: %s" % listener.__class__.__name__)
                listener.datastores_updated()

    def _add_or_modify_vm_cache(self, object):
        # Type of object.obj is vim.VirtualMachine. str(object.obj) is moref
        # id, something like 'vim.VirtualMachine:1227'. moref id is the unique
        # representation of all objects in esx.
        if str(object.obj) not in self._vm_cache:
            vm = VmCache()
        else:
            vm = self._vm_cache[str(object.obj)]

        for change in object.changeSet:
            # We are not interested in ops other than assign.
            # Other operations e.g. add/remove/indirectRemove, only appears
            # when a property is added/removed. However the changes we
            # watched are all static.
            if change.op != "assign":
                continue

            # None value is not updated
            if change.val is None:
                continue

            if change.name == "name":
                vm.name = change.val
                self._logger.debug("cache update: add vm name %s" % vm.name)
                self._vm_name_to_ref[change.val] = str(object.obj)
            elif change.name == "runtime.powerState":
                vm.power_state = PowerState._NAMES_TO_VALUES[change.val]
            elif change.name == "config":
                vm.memory_mb = change.val.hardware.memoryMB
                vm.num_cpu = change.val.hardware.numCPU
                vm.location_id = change.val.locationId
                # files is an optional field, which could be None.
                if change.val.files:
                    vm.path = change.val.files.vmPathName
            elif change.name == "layout.disk":
                disks = []
                for disk in change.val:
                    if disk.diskFile:
                        for disk_file in disk.diskFile:
                            disks.append(disk_file)
                vm.disks = disks

        self._logger.debug("cache update: update vm [%s] => %s" % (str(object.obj), vm))
        if str(object.obj) not in self._vm_cache:
            self._vm_cache[str(object.obj)] = vm

    def _remove_vm_cache(self, object):
        ref_id = str(object.obj)
        assert ref_id in self._vm_cache, "%s not in cache" % ref_id

        vm_cache = self._vm_cache[ref_id]
        self._logger.debug("cache update: delete vm [%s] => %s" % (ref_id, vm_cache))
        del self._vm_cache[ref_id]
        if vm_cache.name in self._vm_name_to_ref:
            # Only delete map in _vm_name_to_ref when it points to the right
            # ref_id. If it points to another ref_id, it means a new VM is
            # created with the same name, which cannot be deleted.
            if self._vm_name_to_ref[vm_cache.name] == ref_id:
                del self._vm_name_to_ref[vm_cache.name]

    def _update_task_cache(self, object):
        task_cache = TaskCache()
        for change in object.changeSet:
            if change.op != "assign":
                continue
            if change.val is None:
                continue

            if change.name == "info.error":
                task_cache.error = change.val
            elif change.name == "info.state":
                task_cache.state = TaskState._NAMES_TO_VALUES[change.val]

        self._logger.debug("task cache update: update task [%s] => %s" % (str(object.obj), task_cache))

        self._task_cache[str(object.obj)] = task_cache

    def _remove_task_cache(self, object):
        assert str(object.obj) in self._task_cache, "%s not in cache" % str(object.obj)

        self._logger.debug("task cache update: remove task [%s] => %s" %
                           (str(object.obj), self._task_cache[str(object.obj)]))

        del self._task_cache[str(object.obj)]

    """ Accessors
    """
    @lock_with("_lock")
    def get_vm_ids_in_cache(self):
        """ Get information of all VMs from cache.
        :return: list of VmId strings
        """
        return [vm.name for vm in self._vm_cache.values()
                if self._validate_vm(vm)]

    @lock_with("_lock")
    def get_vms_in_cache(self):
        """ Get information of all VMs from cache.
        :return: list of VmCache
        """
        return [copy.copy(vm) for vm in self._vm_cache.values()
                if self._validate_vm(vm)]

    @lock_with("_lock")
    def get_vm_in_cache(self, vm_id):
        """ Get information of a VM from cache. The vm state is not
        guaranteed to be up-to-date. Also only name and power_state is
        guaranteed to be not None.

        :return: VmCache for the vm that is found
        :raise VmNotFoundException when vm is not found
        """
        if vm_id not in self._vm_name_to_ref:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

        ref = self._vm_name_to_ref[vm_id]
        vm = self._vm_cache[ref]
        if self._validate_vm(vm):
            return copy.copy(vm)
        else:
            raise VmNotFoundException("VM '%s' not found on host." % vm_id)

    def wait_for_task(self, vim_task, timeout):
        return self._task_cache.wait_until(str(vim_task), self._verify_task_done, timeout=timeout)

    def wait_for_vm_create(self, vm_id):
        self._vm_name_to_ref.wait_until(vm_id, lambda x: x is not None)

    def wait_for_vm_delete(self, vm_id):
        self._vm_name_to_ref.wait_until(vm_id, None)

    """ Helpers
    """
    @staticmethod
    def _validate_vm(vm):
        try:
            vm.validate()
            return True
        except:
            return False

    @staticmethod
    def _verify_task_done(task_cache):
        if not task_cache:
            return False

        state = task_cache.state
        if state == TaskState.error or state == TaskState.success:
            return True
        else:
            return False


class SyncVimCacheThread(threading.Thread):
    """ Periodically sync vm cache with remote esx server
    """
    def __init__(self, vim_client, vim_cache, wait_timeout=10, min_interval=1, errback=None):
        super(SyncVimCacheThread, self).__init__()
        self._logger = logging.getLogger(__name__)
        self.setDaemon(True)
        self.errback = errback
        self.vim_client = weakref.ref(vim_client)
        self.vim_cache = vim_cache
        self.wait_timeout = wait_timeout
        self.min_interval = min_interval
        self.active = True
        self.fail_count = 0
        self.last_updated = time.time()

    def run(self):
        while True:
            if not self.active:
                self._logger.info("Exit vmcache sync thread.")
                break
            client = self.vim_client()
            if not client:
                self._logger.info("Exit vmcache sync thread. vim client is None")
                break

            try:
                self.vim_cache.poll_updates(client, self.wait_timeout)
                self._success_update()
                self._wait_between_updates()
            except:
                self._logger.warning("Failed to poll update %d: %s" % (self.fail_count, str(sys.exc_info()[1])))
                self.fail_count += 1
                if self.fail_count == 5:
                    self._logger.warning("Failed to poll update 5 times")
                    if self.errback:
                        self._logger.warning("Call errback")
                        self.errback()
                    else:
                        self._logger.warning("No errback, vim_client disconnect")
                        client.disconnect()
                self._wait_between_failures()

    def stop(self):
        self._logger.info("Stop syncing vm cache thread")
        self.active = False

    def _success_update(self):
        self.fail_count = 0
        self.last_elapsed = time.time() - self.last_updated
        self.last_updated = time.time()

    def _wait_between_updates(self):
        if self.last_elapsed < self.min_interval:
            time.sleep(self.min_interval - self.last_elapsed)

    def _wait_between_failures(self):
        wait_seconds = 1 << (self.fail_count - 1)
        self._logger.info("Wait %d second(s) to retry update cache" % wait_seconds)
        time.sleep(wait_seconds)
