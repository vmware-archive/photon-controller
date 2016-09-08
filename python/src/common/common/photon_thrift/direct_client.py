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
import ssl
import os

from thrift.protocol import TCompactProtocol
from thrift.protocol import TMultiplexedProtocol
from thrift.transport import TSocket
from thrift.transport import TSSLSocket
from thrift.transport import TTransport


class DirectClient(object):

    """A class for creating direct IP/port connections to HostHandler.

    Attributes:
        service_name: service name that is handled by multiplex processor
        client_cls: A client class.
        host: The host to connect to.
        port: The port to connect to.
        client_timeout: if specified, it is set as socket timeout.
    """
    def __init__(self, service_name, client_cls, host, port,
                 client_timeout=None, cert_file=None, validate=True):
        self._logger = logging.getLogger(__name__)
        self._service_name = service_name
        self._client_cls = client_cls
        self._host = host
        self._port = port
        self._transport = None
        self._client = None
        self._client_timeout = client_timeout
        self._request_log_level = logging.INFO
        self._cert_file = cert_file
        self._validate = validate

    def connect(self):
        """Connect to the HostHandler."""
        cert_file = "/etc/vmware/ssl/host.pem"
        # this file should only be present in the non-auth scenario and init
        # installation
        cert_file_non_auth = "/etc/vmware/ssl/non-auth.pem"

        if os.path.isfile(cert_file_non_auth) or not self._validate:
            # disable thrift based cert validation, this exists mainly since python didn't do cert validation
            # prior to 2.7.9
            if self._cert_file is not None:
                cert_file_non_auth = self._cert_file
            sock = TSSLSocket(host=self._host, port=self._port, validate=False, certfile=cert_file_non_auth)
            # disable cert validation on the python level if we are using python 2.7.9 or newer
            # thanks to https://dnaeon.github.io/disable-python-ssl-verification/
            try:
                _create_unverified_https_context = ssl._create_unverified_context
            except AttributeError:
                # Legacy Python that doesn't verify HTTPS certificates by default
                self._logger.info("Legacy Python that doesn't verify HTTPS certificates by default")
                pass
            else:
                # Handle target environment that doesn't support HTTPS verification
                self._logger.info("Handle target environment that doesn't support HTTPS verification")
                ssl._create_default_https_context = _create_unverified_https_context
        else:
            if self._cert_file is not None:
                cert_file = self._cert_file
            sock = TSSLSocket(host=self._host, port=self._port, certfile=cert_file)

        if self._client_timeout:
            sock.setTimeout(self._client_timeout * 1000)
        self._transport = TTransport.TFramedTransport(sock)
        protocol = TCompactProtocol.TCompactProtocol(self._transport)
        mux_protocol = TMultiplexedProtocol.TMultiplexedProtocol(
                protocol, self._service_name)
        self._client = self._client_cls(mux_protocol)
        self._transport.open()
        self._logger.info("Connected to %s:%s. for service %s"
                          % (self._host, self._port, self._service_name))

    def close(self):
        """Close the connection."""
        self._logger.info("closing connection to %s:%s." %
                          (self._host, self._port))
        self._transport.close()

    def __getattr__(self, name):
        def _missing(*args, **kwargs):
            method = getattr(self._client, name)
            try:
                self._logger.log(self._request_log_level,
                                 "Sending request: %s to: %s:%s", str(args),
                                 self._host, self._port)
                response = method(*args, **kwargs)
                self._logger.log(self._request_log_level,
                                 "Received response: %s from: %s:%s",
                                 str(response), self._host, self._port)
                return response
            except:
                self._logger.warning("Error calling %s on: %s:%s" %
                                     (str(args), self._host, self._port),
                                     exc_info=True)
                raise

        return _missing
