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
import json
import sys

from common.http import HttpClient
from gen.host.ttypes import CreateDisksResponse
from gen.host.ttypes import CreateDiskResultCode
from gen.host.ttypes import CreateVmResponse
from gen.host.ttypes import CreateVmResultCode
from gen.host.ttypes import DeleteDisksResponse
from gen.host.ttypes import DeleteDiskResultCode
from gen.host.ttypes import DeleteVmResponse
from gen.host.ttypes import DeleteVmResultCode
from gen.host.ttypes import ReserveResponse
from gen.host.ttypes import ReserveResultCode
from gen.psim.PlacementSimulator import Iface
from gen.psim.PlacementSimulator import InitializeResponse
from gen.psim.PlacementSimulator import InitializeResultCode
from gen.psim.PlacementSimulator import Processor
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from thrift.protocol import TCompactProtocol
from thrift.server.TNonblockingServer import TNonblockingServer
from thrift.transport import TSocket


class PlacementSimulator(Iface):

    def __Init__(self):
        pass

    def initialize(self, request):
        client = HttpClient(request.cloudstore_endpoint.host,
                            request.cloudstore_endpoint.port)
        _, body = client.get("/photon/cloudstore/hosts?expand", {})
        hosts = json.loads(body)["documents"]
        for host_id, host in hosts.iteritems():
            print host_id, host
        return InitializeResponse(InitializeResultCode.OK)

    def place(self, host_id, request):
        return PlaceResponse(PlaceResultCode.OK)

    def reserve(self, host_id, request):
        return ReserveResponse(ReserveResultCode.OK)

    def create_vm(self, host_id, request):
        return CreateVmResponse(CreateVmResultCode.OK)

    def delete_vm(self, host_id, request):
        return DeleteVmResponse(DeleteVmResultCode.OK)

    def create_disks(self, host_id, request):
        return CreateDisksResponse(CreateDiskResultCode.OK)

    def delete_disks(self, host_id, request):
        return DeleteDisksResponse(DeleteDiskResultCode.OK)


if __name__ == "__main__":
    port = sys.argv[1]
    handler = PlacementSimulator()
    processor = Processor(handler)
    transport = TSocket.TServerSocket(port=port)
    protocol_factory = TCompactProtocol.TCompactProtocolFactory()
    server = TNonblockingServer(processor, transport, protocol_factory)
    server.serve()
