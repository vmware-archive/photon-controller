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

"""Agent integration tests via (local) fake hypervisor."""

import errno
import os
import shutil
import tempfile
import time
import unittest
import uuid

from hamcrest import *  # noqa

from agent.tests.common_helper_functions import RuntimeUtils
from common.file_util import mkdir_p, rm_rf
from gen.host import Host
from gen.host.ttypes import HostMode
from host.hypervisor.fake.hypervisor import FakeHypervisor
from agent_common_tests import AgentCommonTests


class TestAgent(unittest.TestCase, AgentCommonTests):
    def shortDescription(self):
        return None

    def test_bootstrap(self):
        # Verify the negative case first as it has no sideeffect.
        self._update_agent_invalid_config()
        self.runtime.stop_agent(self.proc)
        new_config = self.config.copy()
        # Don't set datastores since
        # self._update_agent_config() sets it.
        del new_config["--datastores"]
        res = self.runtime.start_agent(new_config)
        self.proc, self.host_client, self.control_client = res
        req = self._update_agent_config()

        # Start back the agent and verify that the config seen by the agent is
        # the same we requested for.
        res = self.runtime.start_agent(new_config)
        self.proc, self.host_client, self.control_client = res
        self._validate_post_boostrap_config(req)

    def setUp(self):
        self.runtime = RuntimeUtils()
        self.config = self.runtime.get_default_agent_config()
        self._datastores = ["datastore1", "datastore2"]
        self.config["--datastores"] = ",".join(self._datastores)
        res = self.runtime.start_agent(self.config)
        self.proc, self.host_client, self.control_client = res
        self.set_host_mode(HostMode.NORMAL)

    def tearDown(self):
        self.runtime.cleanup()

    def test_agent_with_invalid_vsi(self):
        self.runtime.stop_agent(self.proc)
        # Since the config file takes precedence over the command line
        # options we need to remove the directory that was created
        # from the start_agent in the setup method.
        shutil.rmtree(self.config["--config-path"])
        new_config = self.config.copy()
        new_config["--hypervisor"] = "esx"
        res = self.runtime.start_agent(new_config)
        self.proc, self.host_client, self.control_client = res
        time_waited = 0
        while self._agent_running() and time_waited < 5:
            time.sleep(0.2)
            time_waited += 0.2
        self.assertFalse(self._agent_running())

    def test_datastore_parse(self):
        """Test that the agent parses datastore args"""
        self.runtime.stop_agent(self.proc)
        new_config = self.config.copy()
        new_config["--datastores"] = " ds1,ds2, ds3, ds4 "
        res = self.runtime.start_agent(new_config)
        self.proc, self.host_client, self.control_client = res

        request = Host.GetConfigRequest()
        response = self.host_client.get_host_config(request)

        datastore_ids = [ds.id for ds in response.hostConfig.datastores]
        expected_ids = [str(uuid.uuid5(uuid.NAMESPACE_DNS, name))
                        for name in ["ds1", "ds2", "ds3", "ds4"]]
        assert_that(datastore_ids, equal_to(expected_ids))

    def test_management_only_parse(self):
        # Default option for management_only is False
        request = Host.GetConfigRequest()
        response = self.host_client.get_host_config(request)

        assert_that(response.result, equal_to(Host.GetConfigResultCode.OK))
        assert_that(response.hostConfig.management_only, equal_to(False))

        # Restart agent with --management-only option, test the flag is set
        # to True
        self.runtime.stop_agent(self.proc)
        new_config = self.config.copy()
        new_config["--management-only"] = None
        res = self.runtime.start_agent(new_config)
        self.proc, self.host_client, self.control_client = res

        request = Host.GetConfigRequest()
        response = self.host_client.get_host_config(request)

        assert_that(response.result, equal_to(Host.GetConfigResultCode.OK))
        assert_that(response.hostConfig.management_only, equal_to(True))

    def test_create_vm_with_ephemeral_disks_ttylinux(self):
        self._test_create_vm_with_ephemeral_disks("ttylinux")

    def test_create_vm_with_ephemeral_disks(self):
        image_dir = os.path.join(
            "/tmp/images",
            FakeHypervisor.datastore_id(self.get_image_datastore()))

        try:
            mkdir_p(image_dir)
            with tempfile.NamedTemporaryFile(dir=image_dir,
                                             suffix=".vmdk") as f:
                # The temp file name created is
                # "/tmp/image/<ds>/<uniquepart>.vmdk".
                # This simulates an image being present on the agent,
                # The file is deleted on leaving the context.
                image_id = f.name[f.name.rfind("/") + 1:-5]
                self._test_create_vm_with_ephemeral_disks(image_id)
        finally:
            rm_rf(image_dir)

    def _agent_running(self):
        if not self.proc:
            return False

        try:
            os.kill(self.proc.pid, 0)
            return True
        except OSError as e:
            if e.errno == errno.ESRCH:
                return False
            elif e.errno == errno.EPERM:
                return True
            else:
                raise e


if __name__ == "__main__":
    unittest.main()
