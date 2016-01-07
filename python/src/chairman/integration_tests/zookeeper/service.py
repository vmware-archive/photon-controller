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

import abc


class ServiceHandler:
    """Service handler for notifying the caller when the node
     joins and leaves the distributed service based on an explicit request
     or service registration interruption.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def on_joined(self):
        """Notified when the node joined the service.

        The node can now accept incoming requests.
        """
        pass

    @abc.abstractmethod
    def on_left(self):
        """Notified when the node left the service.

        The node should fail any new incoming requests because it might not
        be the authoritative source anymore in a partition or other situation.
        """
        pass

    @property
    def service_path(self):
        return self._service_path

    @service_path.setter
    def service_path(self, value):
        self._service_path = value


class Service(object):
    """Distributed service with membership notifications.

    Can be reused for joining and leaving the same service multiple times.
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, handler):
        """
        :type handler: ServiceHandler
        """
        self._handler = handler

    @abc.abstractmethod
    def join(self):
        """Join the service.

        Returns immediately, however the node only joined after the
        on_joined handler was called.
        """
        pass

    @abc.abstractmethod
    def leave(self):
        """Leave the service."""
        pass

    @abc.abstractmethod
    def cleanup(self):
        """Cleanup any resources when the service is no longer needed."""
        pass
