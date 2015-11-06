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

from common.state import State


class TestState(unittest.TestCase):
    def setUp(self):
        self.file_location = tempfile.mkstemp()[1]

    def tearDown(self):
        os.remove(self.file_location)

    def test_persist_state(self):
        normal_state = "normal"
        maintenance_state = "maintenance"

        # test retrieving persisted value
        state = State(self.file_location)
        state.set("state", normal_state)
        s = state.get("state")
        assert_that(s, equal_to(normal_state))

        # test retrieving updated persisted value
        state.set("state", maintenance_state)
        s = state.get("state")
        assert_that(s, equal_to(maintenance_state))

        # test retrieving non-existing key/value pair
        s = state.get("non_state")
        assert_that(s, is_(None))

        # test read from persisted state
        state = State(self.file_location)
        s = state.get("state")
        assert_that(s, equal_to(maintenance_state))


if __name__ == '__main__':
    unittest.main()
