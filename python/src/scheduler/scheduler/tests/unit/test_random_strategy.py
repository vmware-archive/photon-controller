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


from host.hypervisor.resources import ChildInfo
from gen.scheduler.ttypes import PlaceRequest
from gen.resource.ttypes import ResourceConstraint, ResourceConstraintType
from scheduler.strategy.random_subset_strategy import RandomSubsetStrategy


class RandomStrategyTestCase(unittest.TestCase):

    def setUp(self):
        self.child_1 = ChildInfo("child_1", "1.1.1.1", 8835)
        self.child_2 = ChildInfo("child_2", "1.1.1.2", 8835)
        self.child_3 = ChildInfo("child_3", "1.1.1.3", 8835)
        self.child_4 = ChildInfo("child_4", "1.1.1.3", 8835)
        self.child_5 = ChildInfo("child_5", "1.1.1.3", 8835)
        self.child_6 = ChildInfo("child_5", "1.1.1.3", 8835)
        self.child_7 = ChildInfo("child_5", "1.1.1.3", 8835)
        self.request = PlaceRequest()

    def _get_constraints(self, child):
        child_constraints = {}

        if child == self.child_1:
            child_constraints[ResourceConstraintType.DATASTORE] = \
                ['datastore1']
        elif child == self.child_2:
            child_constraints[ResourceConstraintType.DATASTORE] = \
                ['datastore1', 'datastore2']
        elif child == self.child_4:
            child_constraints[ResourceConstraintType.HOST] = \
                ['host1', 'host2', 'host3']
        elif child == self.child_5:
            child_constraints[ResourceConstraintType.HOST] = \
                ['host4']
        elif child == self.child_6:
            child_constraints[ResourceConstraintType.AVAILABILITY_ZONE] = \
                ['zone1']
            child_constraints[ResourceConstraintType.DATASTORE] = \
                ['datastore1']
        elif child == self.child_7:
            child_constraints[ResourceConstraintType.AVAILABILITY_ZONE] = \
                ['zone2']
            child_constraints[ResourceConstraintType.DATASTORE] = \
                ['datastore1']

        return child_constraints

    def test_resource_constraints_one_constraints(self):
        strategy = RandomSubsetStrategy(0.5, 2)

        strategy._get_constraints = self._get_constraints

        result = strategy.filter_child(
            [self.child_1, self.child_2, self.child_3],
            self.request,
            [ResourceConstraint(ResourceConstraintType.DATASTORE,
                                ['datastore1'])])
        assert_that(result, has_length(2))
        assert_that(result, contains_inanyorder(self.child_1, self.child_2))

    def test_resource_constraints_two_constraints(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [
            ResourceConstraint(ResourceConstraintType.DATASTORE,
                               ['datastore1']),
            ResourceConstraint(ResourceConstraintType.DATASTORE,
                               ['datastore2'])]

        result = strategy.filter_child(
            [self.child_1, self.child_2, self.child_3],
            self.request, constraints)
        assert_that(result, has_length(1))
        assert_that(result, contains_inanyorder(self.child_2))

    def test_resource_constraints_availability_zone(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [
            ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE,
                               ['zone1'], False),
            ResourceConstraint(ResourceConstraintType.DATASTORE,
                               ['datastore1'], False)]
        result = strategy.filter_child(
            [self.child_1, self.child_6, self.child_7],
            self.request, constraints)
        assert_that(result, has_length(1))
        assert_that(result, contains_inanyorder(self.child_6))

    def test_resource_constraints_availability_zone_no_match(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [
            ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE,
                               ['zone1'], False),
            ResourceConstraint(ResourceConstraintType.DATASTORE,
                               ['datastore2'], False)]
        result = strategy.filter_child(
            [self.child_1, self.child_6, self.child_7],
            self.request, constraints)
        assert_that(result, has_length(0))

    def test_resource_constraints_negative_two_select_two(self):
        # Test that both children are picked as
        # child 4 has more than just "host1"
        # while child 5 has "host4" which is
        # not included in the negative constraints
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host1'], True),
                       ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host7'], True)]

        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(2))
        assert_that(result, contains_inanyorder(self.child_4, self.child_5))

        # Now try with a single constraint
        constraints = [ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host1', 'host7'], True)]
        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(2))
        assert_that(result, contains_inanyorder(self.child_4, self.child_5))

    def test_resource_constraints_negative_two_select_one(self):
        # Test that child 4 is the only one picked as
        # child 5 has only one host ("host4") which is included
        # in the negative constraints
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host1'], True),
                       ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host4'], True)]

        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(1))
        assert_that(result, contains_inanyorder(self.child_4))

        constraints = [ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host1', 'host4'], True)]

        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(1))
        assert_that(result, contains_inanyorder(self.child_4))

    def test_resource_constraints_negative_no_match(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [ResourceConstraint(
            ResourceConstraintType.HOST,
            ['host1', 'host2', 'host3', 'host4'], True)]

        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(0))

    def test_resource_constraints_negative_all_match(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        constraints = [ResourceConstraint(ResourceConstraintType.HOST,
                                          ['host6', 'host7'], True)]

        result = strategy.filter_child(
            [self.child_4, self.child_5],
            self.request, constraints)
        assert_that(result, has_length(2))
        assert_that(result, contains_inanyorder(self.child_4, self.child_5))

    def test_resource_constraints_zero_constraints(self):
        strategy = RandomSubsetStrategy(1, 3)
        strategy._get_constraints = self._get_constraints

        result = strategy.filter_child(
            [self.child_1, self.child_2, self.child_3], self.request, [])
        assert_that(result, has_length(3))

    def test_resource_constraints_zero_constraints_min_fanout(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        result = strategy.filter_child(
            [self.child_1, self.child_2, self.child_3], self.request, [])
        assert_that(result, has_length(2))

    def test_resource_constraints_no_match(self):
        strategy = RandomSubsetStrategy(0.5, 2)
        strategy._get_constraints = self._get_constraints

        result = strategy.filter_child(
            [self.child_1, self.child_2, self.child_3],
            self.request,
            [ResourceConstraint(ResourceConstraintType.DATASTORE,
                                ['never_found'])])
        assert_that(result, has_length(0))


if __name__ == '__main__':
    unittest.main()
