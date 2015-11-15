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

from gen.roles.ttypes import Roles
from host.host_configuration import HostConfiguration as BaseHostConfiguration
from host.host_handler import HostHandler as BaseHostHandler


class HostConfiguration(BaseHostConfiguration):
    """
    Introduce host id to HostConfiguration
    """
    def __init__(self,
                 a_host_id,
                 a_availability_zone=None,
                 a_scheduler=None,
                 a_roles=None):
        """
        :param a_host_id: preserve host id
        :type a_host_id: string
        """

        #  Base HostConfiguration is old type, thus can't use super()
        BaseHostConfiguration.__init__(
            self,
            a_availability_zone,
            a_scheduler,
            a_roles)

        self.host_id = a_host_id


class HostHandler(BaseHostHandler):
    def __init__(self, a_host_id, a_pseudo_config):
        super(HostHandler, self).__init__(
            a_host_id,
            a_pseudo_config.availability_zone,
            a_pseudo_config.networks,
            False,
            a_pseudo_config)

        self._pseudo_config = a_pseudo_config

    def configure_host(self, leaf_scheduler, roles=Roles()):
        config = HostConfiguration(
            self._agent_id,
            self._availability_zone_id,
            leaf_scheduler,
            roles)

        for observer in self._configuration_observers:
            observer(config)

    @property
    def pseudo_config(self):
        return self._pseudo_config
