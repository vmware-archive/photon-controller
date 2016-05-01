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

import common

from common.service_name import ServiceName
from gen.host import Host
from host.host_handler import HostHandler
from host.hypervisor import hypervisor
from host.upgrade.upgrade import HostUpgrade


class HostPlugin(common.plugin.Plugin):

    def __init__(self):
        super(HostPlugin, self).__init__("Host")

    def init(self):
        # Load agent config
        config = common.services.get(ServiceName.AGENT_CONFIG)

        # Create the hypervisor object
        hv = hypervisor.Hypervisor(config)

        # When configuration changes, notify hypervisor
        config.on_config_change(config.CPU_OVERCOMMIT,
                                hv.set_cpu_overcommit)
        config.on_config_change(config.MEMORY_OVERCOMMIT,
                                hv.set_memory_overcommit)

        # Register hypervisor in services
        common.services.register(ServiceName.HYPERVISOR, hv)

        # Create the upgrade object
        upgrade = HostUpgrade(hv.datastore_manager)
        common.services.register(ServiceName.UPGRADE, upgrade)

        # Create host handler
        host_handler = HostHandler(hv)
        common.services.register(Host.Iface, host_handler)
        common.services.register(ServiceName.HOST_CLIENT, hv.hypervisor.host_client)

        # Load num_threads
        num_threads = config.host_service_threads

        # Define and add thrift service
        service = common.plugin.ThriftService(
            name="Host",
            service=Host,
            handler=host_handler,
            num_threads=num_threads,
        )
        self.add_thrift_service(service)

    def start(self):
        hv = common.services.get(ServiceName.HYPERVISOR)
        hv.set_memory_overcommit(self.agent_config().memory_overcommit)

plugin = HostPlugin()
