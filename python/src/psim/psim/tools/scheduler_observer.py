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

import os
import signal


class SchedulerObserver(object):

    def __init__(self, a_num_of_hosts, a_extractor):
        """
        :param a_num_of_hosts: Number of pseduo agents
        :type: Integer

        :param a_extractor: Extract data received from chairman
        :type: Callable object
        """
        self._num_of_hosts = a_num_of_hosts
        self._extractor = a_extractor

        self._responses = []

    def configuration_changed(self, configuration):
        """
        Non-blocking call back function

        :param configuration:
        :type: HostConfiguration
            scheduler <= return from ConfigureRequest.scheduler
            roles <= return from ConfigureRequest.roles
        """
        # list.append is atomic since probably just adding a word of ptr into
        # the list container
        self._responses.append(configuration)

        if len(self._responses) == self._num_of_hosts:
            self._extractor(self._responses)
            # terminate current process after scheduler.yml generated
            os.kill(os.getpid(), signal.SIGTERM)
