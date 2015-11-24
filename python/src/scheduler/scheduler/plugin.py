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
from gen.scheduler import Scheduler
from scheduler.scheduler_handler import SchedulerHandler


class SchedulerPlugin(common.plugin.Plugin):

    def __init__(self):
        super(SchedulerPlugin, self).__init__("Scheduler")

    def init(self):
        # Load agent config
        config = common.services.get(ServiceName.AGENT_CONFIG)

        # Create scheduler handler
        scheduler_handler = SchedulerHandler(config.utilization_transfer_ratio)
        common.services.register(Scheduler.Iface, scheduler_handler)

        # Load num_threads
        num_threads = config.scheduler_service_threads

        # Define and add thrift service
        service = common.plugin.ThriftService(
            name="Scheduler",
            service=Scheduler,
            handler=scheduler_handler,
            num_threads=num_threads,
        )

        self.add_thrift_service(service)


plugin = SchedulerPlugin()
