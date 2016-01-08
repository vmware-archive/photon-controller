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

""" This contains code for sending RPCs to a scheduler_handler."""

from common.photon_thrift.rpc_client import RpcClient
from gen.scheduler import Scheduler


DEFAULT_CLIENT_TIMEOUT = 10


class SchedulerClient(RpcClient):

    def __init__(self):
        super(SchedulerClient, self).__init__(Scheduler.Iface,
                                              Scheduler.Client, "Scheduler")
