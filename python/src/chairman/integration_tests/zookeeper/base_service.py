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

import abc

import kazoo.exceptions
import kazoo.protocol.states

from .service import Service


class BaseService(Service):
    """Abstract base service with a pluggable membership abstraction.

    Encapsulates connection state management and calls out to the simplified
    membership abstraction to join the service. Takes care of the service
    callbacks.
    """
    __metaclass__ = abc.ABCMeta

    NOT_JOINED = 1
    JOINING = 2
    JOINED = 3

    def __init__(self, zk, name, address, handler):
        """Initialize a base service.

        :param zk: zookeeper client
        :type zk: kazoo.client.KazooClient
        :param name: service name
        :type name: str
        :param address: service address; (host, port)
        :type address: tuple
        :param handler: service handler
        :type handler: common.zookeeper.ServiceHandler
        """
        self._logger = logging.getLogger(__name__)
        self._name = name
        self._address = address
        self._zk = zk

        super(BaseService, self).__init__(handler)

        self._lock = self._zk.handler.lock_object()
        self._state = BaseService.NOT_JOINED
        self._suspended = False
        self._membership = None

        self._zk.add_listener(self._session_watcher)

    def join(self):
        """Join the service.

        Must be in a NOT_JOINED state.
        :rtype [str]: service path
        """
        _service_path = None
        with self._lock:
            if self._state != BaseService.NOT_JOINED:
                raise ValueError("invalid state")

            self._state = BaseService.JOINING
            _service_path = self._inner_join()

        return _service_path

    def leave(self):
        """Leave the service.

        Must not be in a NOT_JOINED state.
        """
        with self._lock:
            if self._state == BaseService.JOINING:
                self._state = BaseService.NOT_JOINED
            elif self._state == BaseService.JOINED:
                self._state = BaseService.NOT_JOINED
                self._handler.on_left()
            else:
                raise ValueError("invalid state")

            self._release_membership()

    def cleanup(self):
        """Cleanup any resources for this service.

        Must be called after service node already left or has yet to join.
        """
        with self._lock:
            if self._state != BaseService.NOT_JOINED:
                raise ValueError("invalid state")

        self._zk.remove_listener(self._session_watcher)

    @abc.abstractmethod
    def _create_membership(self):
        """Creates a new membership for this service.

        :rtype: Membership
        """
        pass

    def _inner_join(self):
        self._membership = self._create_membership()
        self._zk.handler.spawn(self._acquire_membership, self._membership)
        return self._membership.service_path

    def _acquire_membership(self, membership):
        try:
            membership.acquire()
        except (kazoo.exceptions.CancelledError,
                kazoo.exceptions.ConnectionClosedError):
            self._logger.debug("Membership acquisition cancelled")
            return

        # Check if the service is still joining with this membership,
        # otherwise our membership is no longer needed and needs to be
        # released
        release_lease = False
        with self._lock:
            if self._membership is membership and \
                    self._state == BaseService.JOINING:
                self._state = BaseService.JOINED
                self._logger.debug("Membership acquired, joining service")
                self._handler.service_path = \
                    self._membership.service_path
                self._handler.on_joined()
            else:
                self._logger.debug("Membership acquired but no longer needed, "
                                   "releasing")
                release_lease = True

        if release_lease:
            try:
                membership.release()
            except kazoo.exceptions.ConnectionClosedError:
                pass

    def _release_membership(self):
        def helper(membership):
            try:
                membership.release()
            except kazoo.exceptions.ConnectionClosedError:
                pass

        if self._membership is not None:
            self._zk.handler.spawn(helper, self._membership)
            self._membership = None

    def _session_watcher(self, state):
        with self._lock:
            if state == kazoo.protocol.states.KazooState.SUSPENDED:
                if self._state == BaseService.JOINED:
                    # Notify the service that we left when the session was
                    # suspended after we already joined.
                    self._logger.debug("Session suspended, leaving service")
                    self._handler.on_left()
                elif self._state == BaseService.JOINING:
                    # Stop trying to join the service when the session was
                    # suspended, will resume on reconnect.
                    self._logger.debug(
                        "Session suspended, will retry join after reconnect")
            elif state == kazoo.protocol.states.KazooState.LOST:
                if self._state == BaseService.JOINED:
                    if not self._suspended:
                        # Notify the service that we left when the session
                        # was lost. Should have already happened when it was
                        # suspended, however it's possible for a
                        # CONNECTED->LOST state transition to occur for auth
                        # failures.
                        self._logger.debug("Session lost, leaving service")
                        self._handler.on_left()

                    # Need to reacquire membership, so changing the state to
                    # JOINING.
                    self._logger.debug(
                        "Session lost, will retry join after reconnect")
                    self._state = BaseService.JOINING
                elif self._state == BaseService.JOINING:
                    if not self._suspended:
                        # No need to do anything other than release
                        # membership below. Should happen with the exception
                        # of some auth corner cases.
                        self._logger.debug(
                            "Session lost, will retry join after reconnect")

                # Cleanup any resources with the previous membership
                self._release_membership()
            elif state == kazoo.protocol.states.KazooState.CONNECTED:
                if self._state == BaseService.JOINED:
                    if self._suspended:
                        # Rejoin the service if the session was suspended
                        # and we previously joined.
                        self._logger.debug(
                            "Suspended session reconnected, rejoining service")
                        self._handler.on_joined()
                elif self._state == BaseService.JOINING:
                    # Try to reacquire the membership if we were joining
                    # before the session interruption.
                    self._logger.debug(
                        "Session reconnected, trying to join service")
                    self._zk.handler.spawn(self._inner_join)

            # Mark the service as suspended so we can automatically rejoin
            # on a CONNECTED -> SUSPENDED -> CONNECTED transition.
            self._suspended = (
                state == kazoo.protocol.states.KazooState.SUSPENDED)


class Membership(object):
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def acquire(self):
        """Acquire membership.

        Will be called at most 1 times.
        """
        pass

    @abc.abstractmethod
    def release(self):
        """Release previously acquired membership.

        Will be called after acquire and at most 1 times.
        """
        pass

    @property
    def service_path(self):
        return self._service_path

    @service_path.setter
    def service_path(self, value):
        self._service_path = value
