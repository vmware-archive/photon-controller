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
import unittest

from hamcrest import *  # noqa
from matchers import *  # noqa

from common.datastore_tags import DatastoreTags
from common.state import State


class TestDatastoreTags(unittest.TestCase):

    def setUp(self):
        self.file_location = tempfile.mktemp()
        self.state = State(self.file_location)
        self.tags = DatastoreTags(self.state)

    def tearDown(self):
        try:
            os.remove(self.file_location)
        except:
            pass

    def test_empty_tags(self):
        assert_that(self.tags.get(), equal_to({}))

    def test_none_tags(self):
        self.tags.set(None)
        assert_that(self.tags.get(), equal_to({}))

    def test_get_set_tags(self):
        t1 = {
            "datastore1": set(["tag1", "tag2"]),
            "datastore2": set(["tag3", "tag2"]),
        }
        self.tags.set(t1)
        t2 = self.tags.get()

        assert_that(t1, equal_to(t2))

    def test_persistent(self):
        t1 = {
            "datastore1": set(["tag1", "tag2"]),
            "datastore2": set(["tag3", "tag2"]),
        }
        self.tags.set(t1)

        # Load from new State and DatastoreTags object. Verify the same result.
        state = State(self.file_location)
        tags = DatastoreTags(state)
        t2 = tags.get()
        assert_that(t1, equal_to(t2))

    def test_get_map(self):
        t1 = {
            "datastore1": set(["tag1", "tag2"]),
            "datastore2": set(["tag3", "tag2"]),
            "datastore3": set(),
        }
        self.tags.set(t1)
        map = self.tags.get()
        assert_that(map["datastore1"], has_length(2))
        assert_that(map["datastore1"], contains_inanyorder("tag1", "tag2"))
        assert_that(map["datastore2"], has_length(2))
        assert_that(map["datastore2"], contains_inanyorder("tag3", "tag2"))
        assert_that(map["datastore3"], has_length(0))
