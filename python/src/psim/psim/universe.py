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
import uuid
import os
from psim.tree import SchedulerTree


class NotFound(Exception):
    pass


class Results:
    def __init__(self, results, reserve_failures, create_failures):
        self.results = results
        self.reserve_failures = reserve_failures
        self.create_failures = create_failures


class Universe(object):
    """Universe keeps overall simulator state
    """

    vm_flavors = {}
    persistent_disk_flavors = {}
    ephemeral_disk_flavors = {}
    datastores = {}
    results = None

    path_prefix = ""

    tree = SchedulerTree()

    @staticmethod
    def reset():
        Universe.vm_flavors = {}
        Universe.persistent_disk_flavors = {}
        Universe.ephemeral_disk_flavors = {}
        Universe.datastores = {}
        Universe.tree = SchedulerTree()
        Universe.results = None

    @staticmethod
    def get_tree():
        return Universe.tree

    @staticmethod
    def get_path(file_path):
        if file_path:
            return os.path.join(Universe.path_prefix, file_path)
        return None

    @staticmethod
    def add_local_ds(id, capacity):
        if not capacity:
            return
        ds_name = "local_datastore_" + str(id)
        ds_uuid = Universe.get_ds_uuid(ds_name)
        ds = {'id': ds_name, 'capacity': capacity, 'used': 0}
        Universe.datastores[ds_uuid] = ds
        return ds_name

    @staticmethod
    def get_capacity_map():
        ds_map = {}
        for (id, ds) in Universe.datastores.items():
            ds_map[id] = ds['capacity']
        return ds_map

    @staticmethod
    def get_ds_uuid(ds_name):
        return str(uuid.uuid5(uuid.NAMESPACE_DNS, ds_name))
