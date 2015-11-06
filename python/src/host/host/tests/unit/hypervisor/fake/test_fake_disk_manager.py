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

import unittest

from hamcrest import *  # noqa
from mock import MagicMock
from nose.tools import raises

from host.hypervisor.disk_manager import DatastoreOutOfSpaceException
from host.hypervisor.fake.disk_manager import FakeDiskManager as Fdm


class TestFakeDiskManager(unittest.TestCase):
    """Fake placement manager tests."""

    def test_used_storage(self):
        dm = Fdm(MagicMock())
        dm.create_disk('datastore1', 'name1', 1024)
        assert_that(dm.used_storage('datastore1'), is_(1024))
        dm.create_disk('datastore1', 'name2', 10)
        assert_that(dm.used_storage('datastore1'), is_(1034))

    @raises(DatastoreOutOfSpaceException)
    def test_capacity_check(self):
        dm = Fdm(MagicMock(), capacity_map={'datastore1': 1024})
        dm.create_disk('datastore1', 'name1', 1023)
        assert_that(dm.used_storage('datastore1'), is_(1023))
        dm.create_disk('datastore1', 'name2', 2)

    def test_disk_size(self):
        dm = Fdm(MagicMock(), capacity_map={'datastore1': 1024})
        dm.create_disk('datastore1', 'name1', 512)
        assert_that(dm.disk_size('datastore1', 'name1'), is_(512))
