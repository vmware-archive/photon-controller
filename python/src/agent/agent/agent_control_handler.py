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
from gen.agent.ttypes import AgentStatusResponse
from gen.agent.ttypes import AgentStatusCode
from gen.agent.ttypes import ProvisionResponse
from gen.agent.ttypes import ProvisionResultCode
from gen.agent.ttypes import UpgradeResponse
from gen.agent.ttypes import UpgradeResultCode
from gen.agent.ttypes import VersionResponse
from gen.agent.ttypes import VersionResultCode


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
    @error_handler(UpgradeResponse, UpgradeResultCode)
    def upgrade(self, request):
        """
        Upgrade an agent.
        :type request: UpgradeRequest
        :rtype: UpgradeResponse
        """
        try:
            upgrade = common.services.get(ServiceName.UPGRADE)
            upgrade.start()
        except Exception as e:
            self._logger.warning("Unexpected exception", exc_info=True)
            return UpgradeResponse(UpgradeResultCode.SYSTEM_ERROR, str(e))

        return UpgradeResponse(UpgradeResultCode.OK)

    def get_agent_status(self):
        """
        Get the current status of the agent
        """
        agent_config = common.services.get(ServiceName.AGENT_CONFIG)
        if agent_config.reboot_required:
            # agent needs to reboot after provisioning
            return AgentStatusResponse(AgentStatusCode.RESTARTING)

        upgrade = common.services.get(ServiceName.UPGRADE)
        if upgrade.in_progress():
            # agent is performing upgrade during first launch after provisioning
            return AgentStatusResponse(AgentStatusCode.UPGRADING)

        # agent is ready
        return AgentStatusResponse(AgentStatusCode.OK)

    @log_request
    @error_handler(VersionResponse, VersionResultCode)
    def get_version(self, request):
        return VersionResponse(VersionResultCode.OK,
                               version=version.version,
                               revision=version.revision)
