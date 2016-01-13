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

import common

from . import version
from .agent_config import InvalidConfig
from common.photon_thrift.decorators import error_handler
from common.photon_thrift.decorators import log_request
from common.service_name import ServiceName
from gen.agent import AgentControl
from gen.agent.ttypes import ProvisionResponse
from gen.agent.ttypes import ProvisionResultCode
from gen.agent.ttypes import VersionResponse
from gen.agent.ttypes import VersionResultCode
from gen.agent.ttypes import SetAvailabilityZoneResponse
from gen.agent.ttypes import SetAvailabilityZoneResultCode
from gen.roles.ttypes import ChildInfo
from gen.roles.ttypes import SchedulerRole  # noqa needed for docstring
from gen.roles.ttypes import SchedulerEntry
from gen.roles.ttypes import GetSchedulersResponse
from gen.roles.ttypes import GetSchedulersResultCode
from gen.scheduler import Scheduler


class AgentControlHandler(AgentControl.Iface):

    def __init__(self):
        self._logger = logging.getLogger(__name__)

    def ping(self, request):
        """Handles a ping request

        :type request PingRequest
        :rtype None
        """
        pass

    @log_request
    @error_handler(ProvisionResponse, ProvisionResultCode)
    def provision(self, request):
        """
        Provision an agent for photon controller by providing its boostrapping
        configuration.
        :type request: ProvisionRequest
        :rtype: ProvisionResponse
        """
        try:
            agent_config = common.services.get(ServiceName.AGENT_CONFIG)
            agent_config.update_config(request)
        except InvalidConfig as e:
            return ProvisionResponse(ProvisionResultCode.INVALID_CONFIG,
                                     str(e))
        except Exception, e:
            self._logger.warning("Unexpected exception", exc_info=True)
            return ProvisionResponse(ProvisionResultCode.SYSTEM_ERROR,
                                     str(e))

        return ProvisionResponse(ProvisionResultCode.OK)

    @log_request
    @error_handler(GetSchedulersResponse, GetSchedulersResultCode)
    def get_schedulers(self, request):
        """Return the list of current schedulers.

        :type request: GetSchedulersRequest:
        :rtype: GetSchedulersResponse
        """
        scheduler_handler = common.services.get(Scheduler.Iface)
        _schedulers = scheduler_handler.get_schedulers
        response = GetSchedulersResponse()
        response.schedulers = []
        for schId in _schedulers:
            scheduler = _schedulers[schId]

            schEntry = SchedulerEntry()
            schRole = SchedulerRole()
            schRole.host_children = []
            schRole.id = schId

            for host in scheduler._get_hosts():
                childHost = ChildInfo()
                childHost.id = host.id
                childHost.address = host.address
                childHost.port = host.port
                schRole.host_children.append(childHost)

            schEntry.role = schRole
            response.schedulers.append(schEntry)

        response.result = GetSchedulersResultCode.OK
        return response

    @log_request
    @error_handler(VersionResponse, VersionResultCode)
    def get_version(self, request):
        return VersionResponse(VersionResultCode.OK,
                               version=version.version,
                               revision=version.revision)

    @log_request
    @error_handler(SetAvailabilityZoneResponse, SetAvailabilityZoneResultCode)
    def SetAvailabilityZone(self, request):
        """
        Sets/Updates availability zone of host.

        :type request: SetAvailabilityZoneRequest
        :rtype: SetAvailabilityZoneResponse
        """
        try:
            agent_config = common.services.get(ServiceName.AGENT_CONFIG)
            agent_config.set_availability_zone(request)
        except Exception, e:
            self._logger.warning("Unexpected exception", exc_info=True)
            return SetAvailabilityZoneResponse(
                SetAvailabilityZoneResultCode.SYSTEM_ERROR,
                str(e))

        return SetAvailabilityZoneResponse(SetAvailabilityZoneResultCode.OK)
