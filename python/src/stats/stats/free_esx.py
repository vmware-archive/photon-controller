

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
import logging

from pyVmomi import SoapAdapter

logger = logging.getLogger("__hypervisor__")


class ConnWrapper(object):
    """Wraps the httplib.HTTPConnection."""

    _extra_headers = {"User-Agent": "test-user-agent"}

    def __init__(self, conn):
        self._conn = conn

    def request(self, method, url, body=None, headers={}):
        headers.update(ConnWrapper._extra_headers)
        logger.info("free_esx: %s" % headers)
        self._conn.request(method, url, body, headers)

    def getresponse(self):
        response = self._conn.getresponse()
        return response

    def __getattr__(self, name):
        return getattr(self._conn, name)


class SoapStubAdapterWrapper(SoapAdapter.SoapStubAdapter):
    """Wraps the SoapStubAdapter to hook the HTTP connection pool."""

    def GetConnection(self):
        conn = SoapAdapter.SoapStubAdapter.GetConnection(self)
        # avoid rewrapping already wrapped connections
        if not isinstance(conn, ConnWrapper):
            conn = ConnWrapper(conn)
        return conn
