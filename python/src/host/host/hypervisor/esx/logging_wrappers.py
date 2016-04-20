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
import time

from pyVmomi import SoapAdapter

SoapStubAdapter = SoapAdapter.SoapStubAdapter
SoapResponseDeserializer = SoapAdapter.SoapResponseDeserializer

logger = logging.getLogger("__hypervisor__")


class SoapResponseDeserializerWrapper(SoapResponseDeserializer):
    """Wraps the SoapResponseDeserializer to log responses."""

    def Deserialize(self, response, resultType, nsMap=None):
        # reads the entire response into memory instead of streaming,
        # could come up with a streaming approach if it becomes an issue
        body = response.read()
        logger.debug(body)

        # Deserialize accepts strings and file like objects
        return SoapResponseDeserializer.Deserialize(self, body, resultType,
                                                    nsMap)


class ConnWrapper(object):
    """Wraps the httplib connection to log the requests."""

    extra_headers = {}

    def __init__(self, conn):
        """Creates a new connection wrapper.

        :type conn: httplib.HTTPConnection
        """
        self._conn = conn

    def request(self, method, url, body=None, headers={}):
        headers.update(self.extra_headers)
        formatted_headers = "\n".join(
            ["%s: %s" % (key, value) for (key, value) in headers.items()])
        logger.debug("%s %s\n%s\n\n%s" % (method, url, formatted_headers, body))
        self._conn.request(method, url, body, headers)

    def getresponse(self):
        # Python 2.6 only has getresponse(), 2.7 added optional buffering
        start = time.time()
        response = self._conn.getresponse()
        end = time.time()
        logger.debug("[Duration:%f] HTTP/%s %s %s\n%s" %
                     (end - start, response.version, response.status,
                      response.reason, response.msg))
        return response

    def __getattr__(self, name):
        return getattr(self._conn, name)


class SoapStubAdapterWrapper(SoapStubAdapter):
    """Wraps the SoapStubAdapter to hook the HTTP connection pool."""

    def GetConnection(self):
        conn = SoapStubAdapter.GetConnection(self)
        # avoid rewrapping already wrapped connections
        if not isinstance(conn, ConnWrapper):
            conn = ConnWrapper(conn)
        return conn

SoapAdapter.SoapResponseDeserializer = SoapResponseDeserializerWrapper
