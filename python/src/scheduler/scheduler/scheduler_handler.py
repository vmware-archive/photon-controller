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

from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from gen.host import Host
from gen.roles.ttypes import SchedulerRole  # noqa needed for docstring
from gen.scheduler import Scheduler
from gen.scheduler.ttypes import FindResponse
from gen.scheduler.ttypes import FindResultCode
from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import PlaceResultCode
from scheduler.base_scheduler import InvalidScheduler
from scheduler.branch_scheduler import BranchScheduler
from scheduler.leaf_scheduler import LeafScheduler


# TODO(mmutsuzaki) move HostHandler.{find,place} code here.
class SchedulerHandler(Scheduler.Iface):

    def __init__(self, ut_ratio=9):
        self._logger = logging.getLogger(__name__)
        self._schedulers = {}
        self._lock = threading.Lock()
        self.ut_ratio = ut_ratio

    def configure(self, scheduler_roles, enable_health_checker=True):
        """Configure the scheduler service.

        :type scheduler_roles: list of SchedulerRole
        :type enable_health_checker: bool
        """
        if scheduler_roles is None:
            raise ValueError("scheduler_roles can't be None")

        with self._lock:
            schedulers = {}
            for role in scheduler_roles:
                if role.host_children:
                    if role.scheduler_children:
                        raise ValueError("can't have schedulers and hosts")

                    # Leaf Scheduler
                    scheduler = self._create_scheduler(role.id, LeafScheduler,
                                                       enable_health_checker,
                                                       self.ut_ratio)
                    scheduler.configure(role.host_children)
                elif role.scheduler_children:
                    # Branch Scheduler
                    scheduler = self._create_scheduler(
                        role.id, BranchScheduler, enable_health_checker,
                        self.ut_ratio)
                    scheduler.configure(role.scheduler_children)
                else:
                    raise ValueError("scheduler without children")

                schedulers[role.id] = scheduler

            # Switch to the new schedulers
            old_schedulers = self._schedulers
            self._schedulers = schedulers

            # Cleanup any schedulers about to be swapped out
            for scheduler_id in old_schedulers:
                old_schedulers[scheduler_id].cleanup()

    @property
    def get_schedulers(self):
        return self._schedulers

    @log_request
    @error_handler(FindResponse, FindResultCode)
    def find(self, request):
        """Find the specified resource.

        :type request: FindRequest
        :rtype: FindResponse
        """
        try:
            scheduler = self._get_scheduler(request.scheduler_id)
            return scheduler.find(request)
        except InvalidScheduler:
            return FindResponse(
                FindResultCode.INVALID_SCHEDULER)

    @log_request
    @error_handler(PlaceResponse, PlaceResultCode)
    def place(self, request):
        """Place the specified resource(s).

        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        try:
            scheduler = self._get_scheduler(request.scheduler_id)
            return scheduler.place(request)
        except InvalidScheduler:
            return PlaceResponse(
                PlaceResultCode.INVALID_SCHEDULER)

    def host_find(self, request):
        """Handles a host_find request.

        This is a wrapper to call HostHandler.find in the scheduler threadpool
        instead of the host threadpool.
        """
        host_handler = common.services.get(Host.Iface)
        return host_handler.find(request)

    def host_place(self, request):
        """Handles a host_place request.

        This is a wrapper to call HostHandler.place in the scheduler threadpool
        instead of the host threadpool.
        """
        host_handler = common.services.get(Host.Iface)
        return host_handler.place(request)

    def _get_scheduler(self, scheduler_id):
        """Returns a scheduler for the given id.

        :type scheduler_id: str
        :rtype: LeafScheduler or BranchScheduler
        :raise: InvalidScheduler
        """
        if scheduler_id is None:
            raise InvalidScheduler()
        scheduler = self._schedulers.get(scheduler_id)
        if scheduler is None:
            raise InvalidScheduler()
        return scheduler

    def _create_scheduler(self, scheduler_id, scheduler_type,
                          enable_health_checker, ut_ratio):
        """ Create a new scheduler
        :type scheduler_id: str
        :type scheduler_type: class
        :type enable_health_checker: bool
        :rtype: BranchScheduler or LeafScheduler
        """
        return scheduler_type(scheduler_id, ut_ratio,
                              enable_health_checker)
