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
from mock import MagicMock
from nose.tools import raises

from common.mode import MODE, ModeTransitionError, Mode
from common.state import State


class TestState(unittest.TestCase):
    def setUp(self):
        self.file_location = tempfile.mkstemp()[1]
        self.state = State(self.file_location)
        self.mode = Mode(self.state)

    def tearDown(self):
        os.remove(self.file_location)

    def test_get_set_mode(self):
        self.mode.set_mode(MODE.NORMAL)
        assert_that(self.mode.get_mode(), equal_to(MODE.NORMAL))

    def test_get_default_mode(self):
        assert_that(self.mode.get_mode(), equal_to(MODE.NORMAL))

    @raises(AssertionError)
    def test_set_invalid_type(self):
        self.mode.set_mode("NORMAL")

    @raises(ValueError)
    def test_get_invalid_type(self):
        # State.set_mode() will check the type of parameter. State.set() does
        # not check parameter. The value could be corrupted. State.get_mode()
        # will raise ValueError.
        self.state.set(Mode.MODE_KEY, "123")
        self.mode.get_mode()

    @raises(ModeTransitionError)
    def test_invalid_transition(self):
        try:
            self.mode.set_mode(MODE.MAINTENANCE, [MODE.ENTERING_MAINTENANCE])
        except ModeTransitionError as e:
            assert_that(e.from_mode, equal_to(MODE.NORMAL))
            raise

    def test_transition_to_self(self):
        # Should allow to change mode from A to A (e.g. NORMAL to NORMAL)
        assert_that(self.mode.get_mode(), equal_to(MODE.NORMAL))
        self.mode.set_mode(MODE.NORMAL, [MODE.MAINTENANCE])
        assert_that(self.mode.get_mode(), equal_to(MODE.NORMAL))

    def test_callbacks(self):
        enter_maintenance = MagicMock()
        exit_normal = MagicMock()
        change = MagicMock()
        self.mode.on_enter_mode(MODE.MAINTENANCE, enter_maintenance)
        self.mode.on_exit_mode(MODE.NORMAL, exit_normal)
        self.mode.on_change(change)

        self.mode.set_mode(MODE.ENTERING_MAINTENANCE)
        assert_that(enter_maintenance.called, equal_to(False))
        assert_that(exit_normal.called, equal_to(True))
        assert_that(change.call_count, equal_to(1))

        self.mode.set_mode(MODE.MAINTENANCE)
        assert_that(enter_maintenance.called, equal_to(True))
        assert_that(change.call_count, equal_to(2))

if __name__ == '__main__':
    unittest.main()
