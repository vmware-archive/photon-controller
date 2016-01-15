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

import common
import logging
import threading

from common.photon_thrift import StaticServerSet
from common.photon_thrift.client import Client
from common.mode import MODE
from common.service_name import ServiceName
from gen.chairman import Chairman
from gen.chairman.ttypes import RegisterHostRequest
from gen.chairman.ttypes import RegisterHostResultCode
from gen.chairman.ttypes import UnregisterHostRequest
from gen.chairman.ttypes import UnregisterHostResultCode
from gen.host import Host
from gen.host.ttypes import GetConfigRequest
from host.hypervisor.hypervisor import UpdateListener
from thrift.transport.TTransport import TTransportException

REGISTRATION_TIMEOUT = 2.0
MAX_TIMEOUT = 60


class ChairmanRegistrant(UpdateListener):
    """Registers the host with the Chairman.

    Listens for any chairman updates and re-registers with the new instances.
    """

    # Stop logging the registration error if chairman registration fails
    # consecutively.
    MAX_LOGGING_RETRIES = 5

    def __init__(self, chairman_list):
        """Create a new chairman registrant.
        :chairman_list is a list of ServerAddress() objects
        """
        self._logger = logging.getLogger(__name__)
        self.chairman_list = chairman_list
        self._trigger_condition = threading.Condition()
        self._pending_update = True
        self._tries = 0
        self.thread = None
        self.stop_thread = False

    def update_chairman_list(self, chairman_list):
        self.chairman_list = chairman_list

    def trigger_chairman_update(self):
        """Trigger register/unregister"""
        with self._trigger_condition:
            self._pending_update = True
            self._trigger_condition.notify_all()

    def wait_for_update_or_retry(self):
        """It blocks on a condition variable until one of the 2 things happen:
        1) A new update comes or it came between 2 calls of this function.
        2) Last update failed. It's time to retry (timeout has reached).
        """
        with self._trigger_condition:
            if not self._pending_update:
                if self._need_retrying():
                    self._trigger_condition.wait(self._timeout())
                else:
                    self._trigger_condition.wait()

    def start_register(self):
        """Start the chairman registration workflow."""
        self.thread = threading.Thread(target=self._loop)
        self.thread.daemon = True
        self.thread.start()

    def stop_register(self):
        self.stop_thread = True
        with self._trigger_condition:
            self._trigger_condition.notify_all()
        self.thread.join()

    def _loop(self):
        while not self.stop_thread:
            self.wait_for_update_or_retry()

            # Reset _pending_update, so new update is captured
            self._pending_update = False

            try:
                if self._need_to_register():
                    self._register()
                else:
                    self._unregister()

                # reset the counter if registration succeeds
                self._tries = 0
            except Exception as ex:
                # back off exponentially before retrying
                self._tries += 1
                # stop logging if it keeps failing
                if self._tries <= self.MAX_LOGGING_RETRIES:
                    # don't log trace if it's a common error.
                    log_trace = not isinstance(ex, TTransportException)
                    self._logger.warning(str(ex), exc_info=log_trace)

    def _need_to_register(self):
        """Judge if register is needed, or unregister is.
        :return: True if need to register, False if need to unregister
        """
        mode = common.services.get(ServiceName.MODE).get_mode()
        if mode == MODE.MAINTENANCE or mode == MODE.DEPROVISIONED:
            return False
        else:
            return True

    def _timeout(self):
        return min(2 ** self._tries, MAX_TIMEOUT)

    def _need_retrying(self):
        return self._tries != 0

    def _register(self):
        """Register the host with the chairman.

        :raise: ErrorRegistering if there was an error from the server
        :raise: socket.timeout if there was a timeout making the request
        :raise: common.photon_thrift.client.TimeoutError if there was a
                timeout acquiring a client
        """
        host_handler = common.services.get(Host.Iface)
        res = host_handler.get_host_config_no_logging(GetConfigRequest())
        request = RegisterHostRequest(res.hostConfig.agent_id,
                                      res.hostConfig)
        server_set = StaticServerSet(self.chairman_list)
        client = Client(Chairman.Client, "Chairman", server_set,
                        client_timeout=REGISTRATION_TIMEOUT)
        try:
            response = client.register_host(request)
            if response.result != RegisterHostResultCode.OK:
                result = RegisterHostResultCode._VALUES_TO_NAMES[
                    response.result]
                raise Exception("Failed to register: %s" % result)
            self._logger.info("Registered with Chairman")
        finally:
            client.close()

    def _unregister(self):
        """Unregister the host with the chairman.

        :raise: ErrorUnregistering if there was an error from the server
        :raise: socket.timeout if there was a timeout making the request
        :raise: common.photon_thrift.client.TimeoutError if there was a
                timeout acquiring a client
        """
        server_set = StaticServerSet(self.chairman_list)
        client = Client(Chairman.Client, "Chairman", server_set,
                        client_timeout=REGISTRATION_TIMEOUT)
        try:
            host_handler = common.services.get(Host.Iface)
            res = host_handler.get_host_config_no_logging(GetConfigRequest())
            response = client.unregister_host(UnregisterHostRequest(
                res.hostConfig.agent_id))
            if response.result != UnregisterHostResultCode.OK:
                result = UnregisterHostResultCode._VALUES_TO_NAMES[
                    response.result]
                raise Exception("Failed to unregister: %s" % result)
            self._logger.info("Unregistered with Chairman")
        finally:
            client.close()

    def datastores_updated(self):
        """Update listener callback for datastore change"""
        self.trigger_chairman_update()

    def networks_updated(self):
        """Update listener callback for network change"""
        self.trigger_chairman_update()

    def virtual_machines_updated(self):
        """Update listener callback for vm change"""
        pass
