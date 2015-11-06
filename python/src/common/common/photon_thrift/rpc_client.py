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

""" This contains code for sending RPCs to a host_handler."""
from contextlib import contextmanager

import logging

import common
from common.photon_thrift.direct_client import DirectClient
from common.service_name import ServiceName


DEFAULT_CLIENT_TIMEOUT = 10


class RpcClient(object):

    def __init__(self, iface_klass, client_klass, service_name,
                 handler=None):
        """RpcClient base class

        :type iface_klass: Thrift service Iface
        :type client_klass: Thrift service Client
        :type service_name: string
        :type handler: optional handler instance
        """

        if handler:
            self._handler = handler
        else:
            if iface_klass:
                self._handler = common.services.get(iface_klass)

        self._client_klass = client_klass
        self._service_name = service_name
        self._logger = logging.getLogger(__name__)

    @contextmanager
    def connect(self, host, port, server_id=None,
                client_timeout=DEFAULT_CLIENT_TIMEOUT):
        """Connect client for RPC to Thrift handler

        :rtype common.photon_thrift.Client: proxy client to remote or local
               handler
        """

        # HACK: remove once there is a task based API and thread pools per
        # service.
        # The issue is that we're trying to send a request to ourselves,
        # yet our request queue is backed up so we're blocking the pending
        # request until a child request to ourselves times out.
        # This works around the trivial issue, however the issue persists if
        # we send a child request to a different node who then as part of
        # that request needs to request something from us.
        # This happens in the scheduler hierarchy with a branch scheduler.
        # It will fan out a request to a leaf scheduler who will fan it out
        # back to the node who had the branch scheduler to get that
        # individual Host's response.
        local_agent_id = common.services.get(ServiceName.AGENT_CONFIG).host_id
        if server_id == local_agent_id:
            yield self._handler
        else:
            client = None
            try:
                client = DirectClient(self._service_name, self._client_klass,
                                      host, port,
                                      client_timeout=client_timeout)
                client.connect()
                yield client
            finally:
                if client:
                    client.close()
