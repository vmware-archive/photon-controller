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

import uuid

import kazoo.exceptions
import kazoo.protocol.states

from common.photon_thrift.address import create_address

from .base_service import BaseService
from .base_service import Membership


class LeaderElectedService(BaseService):
    """
    Leader Elected Service that allows only a single node to be a member
    at any given point in time. Uses Zookeeper based leader election to
    identify the single node with the membership.
    """

    def __init__(self, zk, name, address, handler):
        """
        :param zk [kazoo.client.KazooClient]: zookeeper client
        :param name [str]: service name
        :param node_id [str]: znode id
        :param address [tuple]: service address; (host, port)
        :param handler [ServiceHandler]: ServiceHandler
        """
        super(LeaderElectedService, self).__init__(zk, name, address, handler)

    def _create_membership(self):
        return Membership(self._zk, self._name, self._address)


class Membership(Membership):
    """
    Membership helper class that encloses single use state for the leader
        elected service.

    Service membership is created by the service join method and it's
    lifetime is bounded by the service leave method or a zookeeper
    connection interruption.
    """

    def __init__(self, zk, name, address):
        """
        Initialize a leader elected service membership.

        :param zk [kazoo.client.KazooClient]: zookeeper client
        :param name [str]: service name
        :param address [tuple]: service address; (host, port)
        """
        self._zk = zk
        self._name = name
        self._address = address
        self._zk_lock = self._zk.Lock(
            "/elections/%s" % name, "%s:%s" % address)
        self.service_path = "/services/{0}/node-{1}".format(
            self._name, uuid.uuid4())

    def acquire(self):
        server_address = create_address(*self._address)

        self._zk_lock.acquire()
        self._zk.retry(self._zk.create, self.service_path, server_address,
                       ephemeral=True, makepath=True)

    def release(self):
        if not self._zk_lock.is_acquired:
            if not self._zk_lock.cancelled:
                self._zk_lock.cancel()
        else:
            self._zk_lock.release()

        try:
            self._zk.retry(self._zk.delete, self.service_path)
        except kazoo.exceptions.NoNodeError:
            pass
