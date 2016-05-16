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

from common.photon_thrift.direct_client import DirectClient
from gen.agent import AgentControl
from gen.host import Host
from gen.scheduler import Scheduler


def get_client(host, ns="Host"):
    port = 8835
    print("Connecting %s:%d ..." % (host, port))
    if ns == "Host":
        client = DirectClient("Host", Host.Client, host, port)
    elif ns == "AgentControl":
        client = DirectClient("AgentControl", AgentControl.Client, host, port)
    elif ns == "Scheduler":
        client = DirectClient("Scheduler", Scheduler.Client, host, port)
    client.connect()
    return client
