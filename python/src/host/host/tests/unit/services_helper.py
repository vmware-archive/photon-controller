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
import tempfile
from mock import MagicMock

import common
from common.mode import Mode
from common.service_name import ServiceName
from common.state import State
from gen.host import Host


class ServicesHelper:

    def __init__(self):
        self.setup()

    def setup(self):
        self.state_file = tempfile.mktemp()
        self._host_handler = MagicMock()
        common.services.register(Host.Iface, self._host_handler)
        common.services.register(ServiceName.MODE,
                                 Mode(State(self.state_file)))
        common.services.register(ServiceName.AGENT_CONFIG, MagicMock())

    def teardown(self):
        common.services.reset()
        try:
            os.unlink(self.state_file)
        except:
            pass
