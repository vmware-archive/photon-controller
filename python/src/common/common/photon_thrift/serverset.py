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

# DEPRECATED!

import abc

from six import with_metaclass


class ServerSetListener(with_metaclass(abc.ABCMeta, object)):
    """This is the ServerSet listener interface for handling server
    added and removed notifications.
    """

    @abc.abstractmethod
    def on_server_added(self, address):
        pass

    @abc.abstractmethod
    def on_server_removed(self, address):
        pass


class ServerSet(with_metaclass(abc.ABCMeta, object)):
    """A ServerSet represents a managed set of servers with added/removed
    notifications.
    """

    @abc.abstractmethod
    def add_change_listener(self, listener):
        """Add new ServerSet change listener.

        :param listener: new listener
        :type listener: ServerSetListener
        """
        pass

    @abc.abstractmethod
    def remove_change_listener(self, listener):
        """Remove existing ServerSet change listener.

        :param listener: existing listener
        :type listener: ServerSetListener
        """
        pass


class StaticServerSet(ServerSet):
    """A ServerSet implementation with a list of static addresses. """

    def __init__(self, servers):
        """servers is a list of ServerAddress instances"""
        self.servers = servers

    def add_change_listener(self, listener):
        for server in self.servers:
            listener.on_server_added((server.host, server.port))

    def remove_change_listener(self, listener):
        pass

    def __str__(self):
        return str(self.servers)
