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
import uuid
from mock import MagicMock

from agent.agent_config import AgentConfig
import common
from common.file_util import mkdtemp
from common.mode import Mode
from common.service_name import ServiceName
from common.state import State
from host.host_handler import HostHandler
from host.hypervisor import hypervisor
from gen.resource.ttypes import ResourceConstraint
from gen.resource.ttypes import ResourceConstraintType


class Host(HostHandler):
    """Simple fake host for resource placement and reservation
    """
    ROLE = 'host'

    def __init__(self, id, networks, datastores, cpu, mem, disk, overcommit):
        self.id = id
        self.cpu = cpu
        self.mem = mem
        self.disk = disk
        self.parent = ""
        self.constraints = set()
        host_constraint = ResourceConstraint(ResourceConstraintType.HOST,
                                             ["host-" + str(id)])
        self.constraints.add(host_constraint)
        [self.constraints.add(net) for net in networks]
        [self.constraints.add(ds) for ds in datastores]
        self.address = ""
        self.port = ""
        conf_dir = mkdtemp(delete=True)
        state = State(os.path.join(conf_dir, "state.json"))
        common.services.register(ServiceName.MODE, Mode(state))
        self.hv = self._get_hypervisor_instance(id,
                                                cpu,
                                                mem,
                                                disk,
                                                [ds.values[0] for ds in
                                                 datastores],
                                                [network.values[0] for
                                                 network in networks],
                                                overcommit)
        super(Host, self).__init__(self.hv)

    def get_info(self):
        mem_info = self.hv.system.memory_info()
        mem_consumed = self.hv.system.host_consumed_memory_mb()
        total_datastore_info = self.hv.hypervisor.total_datastore_info()
        vm_count = len(self.hv.vm_manager._resources)
        return (self.id, vm_count,
                mem_info.total, mem_info.used, mem_consumed,
                total_datastore_info.total, total_datastore_info.used,
                ','.join(['|'.join(constraint.values) for constraint
                          in self.constraints]))

    def _get_hypervisor_instance(self, id, cpu, mem,
                                 disk, datastores, networks, overcommit):
        """
        This method has a lot of hacks to avoid modifying the "fake" classes
        since the fake classes are shared by the integration tests.
        TODO: Cleanup
        """
        config = MagicMock(AgentConfig.__class__)
        config.hypervisor = "fake"
        config.hostname = "localhost"
        config.port = 1234
        config.availability_zone = "az1"
        config.host_id = self.id
        config.datastores = datastores
        config.image_datastores = datastores[0]
        config.networks = networks
        config.memory_overcommit = overcommit["mem"]
        config.cpu_overcommit = overcommit["cpu"]
        config.image_datastore = datastores[0]
        config.image_datastore_for_vm = True
        config.image_datastores = [{"name": datastores[0],
                                    "used_for_vms": True}]
        config.multi_agent_id = None
        config.host_port = "localhost:1234"
        hv = hypervisor.Hypervisor(config)
        hv.hypervisor._uuid = str(uuid.uuid5(uuid.NAMESPACE_DNS,
                                             str(id)))
        hv.hypervisor.disk_manager.capacity_map = self._get_capacity_map()
        for ds_id in hv.datastore_manager.get_datastore_ids():
            hv.hypervisor.image_manager.deploy_image("None",
                                                     "__DUMMY_IMAGE_ID__",
                                                     ds_id)
        hv.hypervisor.system.vm_usable_memory = mem
        hv.hypervisor.system.total_cpus = cpu
        return hv

    def _get_capacity_map(self):
        from psim.universe import Universe
        return Universe.get_capacity_map()

    def __str__(self):
        mem_info = self.hv.system.memory_info()
        ds_info = self.hv.hypervisor.total_datastore_info()
        return "{id: %d, mem: %d, used_mem: %d, disk: %d, used_disk: %d, " \
               "constraints: [%s]}" % \
               (self.id, mem_info.total, mem_info.used,
                ds_info.total, ds_info.used,
                ','.join(['|'.join(constraint.values) for constraint
                          in self.constraints]))

    __repr__ = __str__
