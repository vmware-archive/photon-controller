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

from hamcrest import *  # noqa
from matchers import *  # noqa

from psim.commands.load_flavors import LoadFlavorCmd
from common.kind import QuotaLineItem, Unit
from psim.universe import Universe


class LoadFlavorTestCase(unittest.TestCase):

    def setUp(self):
        Universe.reset()
        self.test_dir = os.path.join(os.path.dirname(__file__), 'test_files')

    def tearDown(self):
        pass

    def test_load_vm_flavors(self):
        file = os.path.join(self.test_dir, 'vm.yml')
        cmd = LoadFlavorCmd(file)
        cmd.run()

        assert_that(len(Universe.vm_flavors), equal_to(2))
        assert_that(Universe.vm_flavors['core-10'], not_none())
        assert_that(Universe.vm_flavors['core-100'], not_none())

        flavor = Universe.vm_flavors['core-10']
        assert_that(len(flavor.cost), equal_to(5))
        assert_that(flavor.cost['vm'],
                    equal_to(QuotaLineItem('vm', "1.0", Unit.COUNT)))
        assert_that(flavor.cost['vm.flavor.core-10'],
                    equal_to(QuotaLineItem('vm.flavor.core-10',
                                           "1.0", Unit.COUNT)))
        assert_that(flavor.cost['vm.cpu'],
                    equal_to(QuotaLineItem('vm.cpu', "1.0", Unit.COUNT)))
        assert_that(flavor.cost['vm.memory'],
                    equal_to(QuotaLineItem('vm.memory', "32.0", Unit.MB)))
        assert_that(flavor.cost['vm.cost'],
                    equal_to(QuotaLineItem('vm.cost', "0.025", Unit.COUNT)))

    def test_load_persistent_disk_flavors(self):
        file = os.path.join(self.test_dir, 'persistent-disk.yml')
        cmd = LoadFlavorCmd(file)
        cmd.run()

        assert_that(len(Universe.persistent_disk_flavors), equal_to(2))
        assert_that(Universe.persistent_disk_flavors['core-100'], not_none())
        assert_that(Universe.persistent_disk_flavors['core-200'], not_none())

        flavor = Universe.persistent_disk_flavors['core-100']
        assert_that(len(flavor.cost), equal_to(3))
        assert_that(flavor.cost['persistent-disk'],
                    equal_to(QuotaLineItem('persistent-disk',
                                           "1.0", Unit.COUNT)))
        assert_that(flavor.cost['persistent-disk.flavor.core-100'],
                    equal_to(QuotaLineItem('persistent-disk.flavor.core-100',
                                           "1.0", Unit.COUNT)))
        assert_that(flavor.cost['persistent-disk.cost'],
                    equal_to(QuotaLineItem('persistent-disk.cost', "1.0",
                                           Unit.COUNT)))

    def test_load_ephemeral_disk_flavors(self):
        file = os.path.join(self.test_dir, 'ephemeral-disk.yml')
        cmd = LoadFlavorCmd(file)
        cmd.run()

        assert_that(len(Universe.ephemeral_disk_flavors), equal_to(2))
        assert_that(Universe.ephemeral_disk_flavors['core-100'], not_none())
        assert_that(Universe.ephemeral_disk_flavors['core-200'], not_none())

        flavor = Universe.ephemeral_disk_flavors['core-100']
        assert_that(len(flavor.cost), equal_to(3))
        assert_that(flavor.cost['ephemeral-disk'],
                    equal_to(QuotaLineItem('ephemeral-disk',
                                           "1.0", Unit.COUNT)))
        assert_that(flavor.cost['ephemeral-disk.flavor.core-100'],
                    equal_to(QuotaLineItem('ephemeral-disk.flavor.core-100',
                                           "1.0", Unit.COUNT)))
        assert_that(flavor.cost['ephemeral-disk.cost'],
                    equal_to(QuotaLineItem('ephemeral-disk.cost', "1.0",
                                           Unit.COUNT)))


if __name__ == '__main__':
    unittest.main()
