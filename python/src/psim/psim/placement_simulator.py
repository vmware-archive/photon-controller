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
from pthrift.multiplex import TMultiplexedProcessor
from thrift.protocol import TCompactProtocol
from thrift.transport import TSocket
from tserver.thrift_server import TNonblockingServer


class PlacementSimulator(Iface):

    def __Init__(self):
        self._initialized = False
        self._hosts = {}
        self._datastores = {}

    def initialize(self, request):
        client = HttpClient(request.cloudstore_endpoint.host,
                            request.cloudstore_endpoint.port)
        _, body = client.get("/photon/cloudstore/hosts?expand", {})
        hosts = json.loads(body)["documents"]
        for host_id, host in hosts.iteritems():
            print host_id, host
        self._initialized = True
        return InitializeResponse(InitializeResultCode.OK)

    def place(self, host_id, request):
        if not self._initialized:
            return PlaceResponse(PlaceResultCode.SYSTEM_ERROR,
                                 "Call initialize() first")
        return PlaceResponse(PlaceResultCode.OK)

    def reserve(self, host_id, request):
        if not self._initialized:
            return ReserveResponse(ReserveResultCode.SYSTEM_ERROR,
                                   "Call initialize() first")
        return ReserveResponse(ReserveResultCode.OK)

    def create_vm(self, host_id, request):
        if not self._initialized:
            return CreateVmResponse(CreateVmResultCode.SYSTEM_ERROR,
                                    "Call initialize() first")
        return CreateVmResponse(CreateVmResultCode.OK)

    def delete_vm(self, host_id, request):
        if not self._initialized:
            return DeleteVmResponse(DeleteVmResultCode.SYSTEM_ERROR,
                                    "Call initialize() first")
        return DeleteVmResponse(DeleteDiskResultCode.OK)

    def create_disks(self, host_id, request):
        if not self._initialized:
            return CreateDisksResponse(CreateDiskResultCode.SYSTEM_ERROR,
                                       "Call initialize() first")
        return CreateDisksResponse(CreateDiskResultCode.OK)

    def delete_disks(self, host_id, request):
        if not self._initialized:
            return DeleteDisksResponse(DeleteDiskResultCode.SYSTEM_ERROR,
                                       "Call initialize() first")
        return DeleteDisksResponse(DeleteDiskResultCode.OK)


if __name__ == "__main__":
    port = sys.argv[1]
    mux_processor = TMultiplexedProcessor()
    handler = PlacementSimulator()
    processor = Processor(handler)
    mux_processor.registerProcessor("psim", processor, 16, 1000)
    transport = TSocket.TServerSocket(port=port)
    protocol_factory = TCompactProtocol.TCompactProtocolFactory()
    server = TNonblockingServer(mux_processor, transport, protocol_factory,
                                protocol_factory)
    server.serve()
