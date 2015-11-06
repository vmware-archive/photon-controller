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
import threading
import time
from agent.agent_control_client import AgentControlClient
from common.photon_thrift import StaticServerSet
from common.photon_thrift.client import Client
from gen.agent.ttypes import PingRequest
from gen.chairman import Chairman
from gen.chairman.ttypes import ReportMissingRequest
from gen.chairman.ttypes import ReportMissingResultCode
from gen.chairman.ttypes import ReportResurrectedRequest
from gen.chairman.ttypes import ReportResurrectedResultCode


class HealthChecker(object):
    """Monitors a set of children by periodically sending PingRequest.

    This class runs two threads, heartbeater and reporter. The heartbeater
    thread periodically sends PingRequest to children and updates sequence
    number and timestamp of the last successful ping for each child. The
    reporter thread periodically checks the timestamp/sequence number and
    reports resurrected and missing children to chairman.
    """

    def __init__(self, scheduler_id, children, agent_config):
        """
        Args:
            scheduler_id: id of this scheduler
            children: map from server id to ServerAddress
            agent_config: AgentConfig object
        """
        self._logger = logging.getLogger(__name__)
        self._stop = threading.Event()
        self._scheduler_id = scheduler_id
        self._children = children
        self._config = agent_config

        # keep track of reported children
        self._resurrected_children = set()
        self._missing_children = set()

        self._seqnum = 0
        self._last_update = {}
        for child in self._children:
            self._last_update[child] = (self._seqnum, time.time())
        self._heartbeater = threading.Thread(target=HealthChecker.heartbeat,
                                             args=(self,))
        self._reporter = threading.Thread(target=HealthChecker.report,
                                          args=(self,))

    @property
    def get_missing_hosts(self):
        return self._missing_children

    def start(self):
        self._heartbeater.start()
        self._reporter.start()

    def stop(self):
        self._stop.set()
        self._heartbeater.join()
        self._reporter.join()

    def _send_heartbeat(self):
        for id, address in self._children.iteritems():
            try:
                client = AgentControlClient()
                timeout_sec = self._config.thrift_timeout_sec
                with client.connect(address.host, address.port, id,
                                    timeout_sec) as thrift_client:
                    request = PingRequest()
                    request.scheduler_id = self._scheduler_id
                    request.sequence_number = self._seqnum + 1
                    thrift_client.request_log_level = logging.DEBUG
                    thrift_client.ping(request)
                    self._last_update[id] = (self._seqnum + 1, time.time())
            except Exception, ex:
                self._logger.warn("Failed to ping %s: %s" % (address, ex))

        self._seqnum += 1

    def heartbeat(self):
        """Ping children in a loop.

        Send ping requests to children serially with an interval specified
        by heartbeat_interval_sec, and maintain a timestamp of the last
        successful ping for each child.

        Note that the actual interval might be longer than the specified
        interval if it takes a long time to send ping to all the children.
        This method keeps track of the sequence number of the last successful
        ping for each host. The reporter thread does not consider a child
        dead if the sequence number of the last successful ping is the same
        as the current sequence number.
        """
        while not self._stop.is_set():
            # send ping to all the children
            start = time.time()
            self._send_heartbeat()
            duration = int(time.time() - start)
            if self._config.heartbeat_interval_sec > duration:
                self._stop.wait(self._config.heartbeat_interval_sec - duration)

    def _send_report(self):
        # split children into active and inactive according to
        # heartbeat_interval_sec * heartbeat_timeout_factor
        timeout = self._config.heartbeat_interval_sec * \
            self._config.heartbeat_timeout_factor
        now = time.time()
        active_children = set()
        inactive_children = set()
        for child, (seq, timestamp) in self._last_update.iteritems():
            if now - timestamp > timeout and seq < self._seqnum:
                inactive_children.add(child)
            else:
                active_children.add(child)

        # find active and inactive children that haven't been reported
        newly_active = active_children - self._resurrected_children
        newly_inactive = inactive_children - self._missing_children

        # report resurrected and missing children to chairman. Note that we
        # don't update {resurrected,missing}_children if we fail to report
        # them to chairman so that they get reported again in the next
        # iteration.
        if newly_active and self.report_resurrected(list(newly_active)):
            self._resurrected_children |= newly_active
            self._missing_children -= newly_active
        if newly_inactive and self.report_missing(list(newly_inactive)):
            self._resurrected_children -= newly_inactive
            self._missing_children |= newly_inactive

    def report(self):
        """Periodically report resurrected and missing children.
        """
        while not self._stop.is_set():
            self._send_report()

            # wait for the next period
            self._stop.wait(self._config.heartbeat_interval_sec)

    def report_resurrected(self, resurrected_children):
        """Report resurrected children.

        Returns:
            True if reporting was successful. False otherwise.
        """
        client = None
        try:
            request = ReportResurrectedRequest()
            request.scheduler_id = self._scheduler_id
            request.hosts = resurrected_children
            chairman_list = StaticServerSet(self._config.chairman_list)
            client_timeout = self._config.thrift_timeout_sec
            client = Client(Chairman.Client, "Chairman", chairman_list,
                            client_timeout=client_timeout)
            res = client.report_resurrected(request)
            if res.result != ReportResurrectedResultCode.OK:
                rc = ReportResurrectedResultCode._VALUES_TO_NAMES[res.result]
                self._logger.warn("Failed to send %s: %s" % (request, rc))
                return False
            return True
        except Exception:
            self._logger.warn("Failed to send %s" % request, exc_info=True)
            return False
        finally:
            if client:
                client.close()

    def report_missing(self, missing_children):
        """Report missing children.

        Returns:
            True if reporting was successful. False otherwise.
        """
        client = None
        try:
            request = ReportMissingRequest()
            request.scheduler_id = self._scheduler_id
            request.hosts = missing_children
            chairman_list = StaticServerSet(self._config.chairman_list)
            client_timeout = self._config.thrift_timeout_sec
            client = Client(Chairman.Client, "Chairman", chairman_list,
                            client_timeout=client_timeout)
            res = client.report_missing(request)
            if res.result != ReportMissingResultCode.OK:
                rc = ReportMissingResultCode._VALUES_TO_NAMES[res.result]
                self._logger.warn("Failed to send %s: %s" % (request, rc))
                return False
            return True
        except Exception:
            self._logger.warn("Failed to send %s" % request, exc_info=True)
            return False
        finally:
            if client:
                client.close()
