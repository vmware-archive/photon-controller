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

import re
import unittest

from hamcrest import *  # noqa

from integration_tests.event_generator.dfa import BadState
from integration_tests.event_generator.dfa import IllegalTransition
from integration_tests.event_generator.dfa import State
from integration_tests.event_generator.dfa import StateMachine
from integration_tests.event_generator.dfa import Transition


class TestStateMachine(unittest.TestCase):
    def test_simple_state_machine(self):
        # This state machine will generate even length strings of the form
        # (AB|AC)* or odd length strings of the form (AB|AC)* | (A|)
        S1 = State("S1")
        S2 = State("S2")

        regex = re.compile("^(AB|AC|)+(A|)$")

        self._str = ""

        def appendA():
            self._str += "A"

        def appendB():
            self._str += "B"

        def appendC():
            self._str += "C"

        S1.add_transition(Transition("S1->S2", appendA, S2, 100))
        S2.add_transition(Transition("S2->S1", appendB, S1, 50))
        S2.add_transition(Transition("S2->S1", appendC, S1, 50))
        S1.build_pdf()
        S2.build_pdf()

        generator = StateMachine("Gen", S1)
        for x in xrange(1000):
            generator.transition()

        # Verify that the State machine generates a correct regex
        assert_that(regex.match(self._str), not_none())
        # Verify that there isnt a transition from B to C
        assert_that("BC" in self._str, is_(False))
        assert_that("CB" in self._str, is_(False))

    def test_bad_state(self):
        S1 = State("S1")
        S2 = State("S2")
        S1.add_transition(Transition("S1->S2", None, S2, 100))
        S1.add_transition(Transition("S1->S2", None, S2, 50))
        self.assertRaises(BadState, S1.build_pdf)

    def test_illegal_transition(self):
        generator = StateMachine("Gen", None)
        self.assertRaises(IllegalTransition, generator.transition)
