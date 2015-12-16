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
import logging

from gen.host import Host
from gen.scheduler import Scheduler


# TODO(mmutsuzaki) move HostHandler.{find,place} code here.
class SchedulerHandler(Scheduler.Iface):

    def __init__(self, ut_ratio=9):
        self._logger = logging.getLogger(__name__)

    def host_find(self, request):
        """Handles a host_find request.

        This is a wrapper to call HostHandler.find in the scheduler threadpool
        instead of the host threadpool.
        """
        host_handler = common.services.get(Host.Iface)
        return host_handler.find(request)

    def host_place(self, request):
        """Handles a host_place request.

        This is a wrapper to call HostHandler.place in the scheduler threadpool
        instead of the host threadpool.
        """
        host_handler = common.services.get(Host.Iface)
        return host_handler.place(request)
