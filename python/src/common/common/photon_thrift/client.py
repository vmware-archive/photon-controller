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

import collections
import logging
import random
from threading import Condition
from time import time

from common.photon_thrift import ServerSetListener
from thrift.protocol import TCompactProtocol
from thrift.protocol import TMultiplexedProtocol
from thrift.transport import TSocket
from thrift.transport import TTransport


class TimeoutError(Exception):
    pass


class ClosedError(Exception):
    pass


class Client(ServerSetListener):
    """Thrift client proxy backed by a serverset

    DEPRECATED. Check DirectClient.

    Dynamically dispatches the thrift call to a client backed by a serverset.

    This is a blocking implementation. Calls will block until client is
    available, pool is closed, or timeout has been exceeded.
    """

    DEFAULT_ACQUISITION_TIMEOUT = 10.0  # seconds
    DEFAULT_CLIENT_TIMEOUT = 10.0  # seconds
    DEFAULT_MAX_CLIENTS = 10

    def __init__(self, client_class, service_name, server_set,
                 acquisition_timeout=DEFAULT_ACQUISITION_TIMEOUT,
                 client_timeout=DEFAULT_CLIENT_TIMEOUT,
                 max_clients=DEFAULT_MAX_CLIENTS):
        """
        :param client_class: Class used to instantiate clients
        :type client_class:

        :param service_name: Service name
        :type service_name: str

        :param server_set: Server set backing up the client pool
        :type server_set: ServerSet
        """
        self._logger = logging.getLogger(__name__)

        self._client_class = client_class
        self._service_name = service_name
        self._server_set = server_set
        self._acquisition_timeout = acquisition_timeout

        if client_timeout is not None:
            client_timeout *= 1000
        self._client_timeout = client_timeout
        self._max_clients = max_clients

        # Available server addresses
        self._servers = set()

        # Clients indexed by their address
        self._clients = collections.defaultdict(list)

        # Clients currently in use (keys are clients, values are addresses)
        self._acquired_clients = dict()

        # Currently opened transports (keys are clients, values are transports)
        self._transports = dict()

        # Currently opened sockets (keys are clients, values are TSocket)
        self._sockets = dict()

        # Condition variable guarding all state transitions
        self._lock = Condition()

        self._closed = False

        # This should be the last line, as it can start firing immediately
        self._server_set.add_change_listener(self)

        # Log level for request/response bodies.
        self._request_log_level = logging.INFO

    @property
    def client_timeout(self):
        return self._client_timeout

    @client_timeout.setter
    def client_timeout(self, value):
        if value is not None:
            value *= 1000
        self._client_timeout = value

    @property
    def request_log_level(self):
        return self._request_log_level

    @request_log_level.setter
    def request_log_level(self, value):
        """Sets log level for request/response bodies.

        By default, this class logs two messages at INFO level per request,
        one for request body and the other for response body. Use this method
        to change the log level of these two messages.
        """
        self._logger.debug("Setting request log level to %d" % value)
        self._request_log_level = value

    def __getattr__(self, name):
        """Dispatches method call to the actual client.

        :param name: name
        :type name: str
        """

        def _missing(*args, **kwargs):
            client = self._acquire()
            method = getattr(client, name)
            try:
                self._logger.log(self.request_log_level,
                                 "Sending request: %s to: %s", str(args),
                                 self._server_set)
                response = method(*args, **kwargs)
                self._logger.log(self.request_log_level,
                                 "Received response: %s from: %s",
                                 str(response), self._server_set)
                return response
            except:
                self._logger.warning("Error calling %s on: %s" %
                                     (str(args), self._server_set),
                                     exc_info=True)
                raise
            finally:
                self._release(client)

        return _missing

    def on_server_added(self, address):
        """Gets called by serverset when new server has been added.

        :param address: Newly added server address
        :type address: tuple (host, port)
        """
        with self._lock:
            self._logger.debug("New server added: %s" % (address,))
            self._servers.add(address)
            self._lock.notify()

    def on_server_removed(self, address):
        """Gets called by serverset when server has been removed.

        :param address: Removed server address
        :type address: tuple (host, port)
        """
        with self._lock:
            self._logger.debug("Server removed: %s" % (address,))
            self._servers.remove(address)
            if address in self._clients:
                del self._clients[address]
            self._lock.notify()

    def close(self):
        """Marks client as closed. Closed client can't be used anymore."""
        with self._lock:
            self._closed = True
            self._server_set.remove_change_listener(self)

            for client in self._clients:
                if client in self._transports:
                    self._transports[client].close()
                    del self._transports[client]

                if client in self._sockets:
                    del self._sockets[client]

            self._clients.clear()
            self._servers.clear()

            self._lock.notify()

    def _acquire(self):
        expires = time() + self._acquisition_timeout

        with self._lock:
            while True:
                if self._closed:
                    raise ClosedError("Client pool is closing")

                if len(self._clients) > 0:
                    address = random.choice(self._clients.keys())
                    client = self._clients[address].pop()
                    if len(self._clients[address]) == 0:
                        del self._clients[address]
                    self._acquired_clients[client] = address
                    return client

                if len(self._servers) > 0 and self._can_create_client():
                    address = random.choice(list(self._servers))
                    client = self._create_client(address)
                    self._acquired_clients[client] = address
                    return client

                timeout = expires - time()
                if timeout < 0:
                    raise TimeoutError("Timed out waiting for client")

                self._lock.wait(timeout)

    def _release(self, client):
        with self._lock:
            address = self._acquired_clients.pop(client)

            if not self._closed and address in self._servers:
                self._clients[address].append(client)

            self._lock.notify()

    def _can_create_client(self):
        return len(self._acquired_clients) <= self._max_clients

    def _create_client(self, address):
        host, port = address
        sock = TSocket.TSocket(host, port)
        sock.setTimeout(self._client_timeout)
        transport = TTransport.TFramedTransport(sock)

        protocol = TCompactProtocol.TCompactProtocol(transport)
        mp = TMultiplexedProtocol.TMultiplexedProtocol(
                protocol, self._service_name)

        client = self._client_class(mp)
        self._transports[client] = transport
        self._sockets[client] = sock

        transport.open()
        return client
