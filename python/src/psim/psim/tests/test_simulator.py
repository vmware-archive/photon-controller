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
import unittest

from mock import MagicMock

from hamcrest import *  # noqa
from matchers import *  # noqa

import common
from common.service_name import ServiceName
from psim.command import parse_cmd
import psim.commands  # noqa
from psim.universe import Universe


class SimulatorTestCase(unittest.TestCase):

    def setUp(self):
        self.test_dir = os.path.join(os.path.dirname(__file__), 'test_files')
        Universe.reset()
        Universe.path_prefix = self.test_dir
        dstags = MagicMock()
        dstags.get.return_value = []
        common.services.register(ServiceName.DATASTORE_TAGS, dstags)

    def tearDown(self):
        pass

    def runSimulator(self, cmd_file):
        with open(cmd_file, 'r') as f:
            for line in f:
                cmd = parse_cmd(line.split())
                cmd.run()

    def test_no_constraints(self):
        cmd_file = os.path.join(self.test_dir, 'no_constraints/run.cmd')
        self.runSimulator(cmd_file)

    def test_simple_constraints(self):
        cmd_file = os.path.join(self.test_dir, 'simple_constraints/run.cmd')
        self.runSimulator(cmd_file)

    def test_simple_negative_constraints(self):
        cmd_file = os.path.join(self.test_dir, 'negative_constraints/run.cmd')
        self.runSimulator(cmd_file)

    def test_three_level_tree(self):
        cmd_file = os.path.join(self.test_dir,
                                'auto_tree_with_branch_sched/run.cmd')
        self.runSimulator(cmd_file)

    def test_two_level_tree(self):
        cmd_file = os.path.join(self.test_dir,
                                'auto_tree_with_leaf_sched/run.cmd')
        self.runSimulator(cmd_file)

    def test_varying_vm_flavors(self):
        cmd_file = os.path.join(self.test_dir,
                                'varying_vm_flavors/run.cmd')
        self.runSimulator(cmd_file)

    def test_vm_flavors_w_load(self):
        cmd_file = os.path.join(self.test_dir,
                                'vm_flavors_w_load/run.cmd')
        self.runSimulator(cmd_file)

if __name__ == '__main__':
    unittest.main()
