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
import threading

from gen.host import Host
from gen.scheduler.ttypes import ConfigureResponse
from gen.scheduler.ttypes import ConfigureResultCode


class AgentHandler(Host.Iface):
    """handler to put config requests to a dict from host_id to request"""
    def __init__(self, num_agents, return_code=ConfigureResultCode.OK):
        self.received_all = threading.Event()
        self.configs = {}
        self.num_agents = num_agents
        self.return_code = return_code

    def reset(self):
        self.configs = {}
        self.received_all.clear()

    def configure(self, config):
        self.configs[config.host_id] = config
        if len(self.configs) == self.num_agents:
            self.received_all.set()
        return ConfigureResponse(self.return_code)
