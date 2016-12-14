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

from .agent_control_handler import AgentControlHandler
from common.service_name import ServiceName
from gen.agent import AgentControl


class AgentControlPlugin(common.plugin.Plugin):

    def __init__(self):
        super(AgentControlPlugin, self).__init__("AgentControl")

    def init(self):
        # Load agent config
        config = common.services.get(ServiceName.AGENT_CONFIG)

        # Load num_threads
        num_threads = config.control_service_threads

        # Create agent control handler
        agent_control_handler = AgentControlHandler()
        common.services.register(AgentControl.Iface, agent_control_handler)

        # Create plugin
        service = common.plugin.ThriftService(
            name="AgentControl",
            service=AgentControl,
            handler=agent_control_handler,
            num_threads=num_threads,
        )
        self.add_thrift_service(service)


plugin = AgentControlPlugin()
