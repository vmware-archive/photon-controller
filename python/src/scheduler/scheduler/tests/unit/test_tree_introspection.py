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

from scheduler.tree_introspection import Host
from scheduler.tree_introspection import Scheduler
from scheduler.tree_introspection import LEAF_SCHEDULER_TYPE
from scheduler.tree_introspection import ROOT_SCHEDULER_TYPE


class TreeIntrospectionTestCase(unittest.TestCase):
    def test_host_equality(self):
        leaf1 = Scheduler("sc1", LEAF_SCHEDULER_TYPE)
        h1 = Host("host1", "address1", 1234)
        leaf1.add_child(h1)
        h1p = Host("host1", "address1", 1234)
        h2 = Host("host2", "address2", 1235)
        # Same host
        assert_that(h1 == h1, is_(True))
        # One host has parent, the other doesnt
        assert_that(h1 == h1p, is_(False))
        # Different hosts
        assert_that(h1 == h2, is_(False))

    def test_scheduler_equality(self):
        root = Scheduler("root", ROOT_SCHEDULER_TYPE)
        leaf1 = Scheduler("sc1", LEAF_SCHEDULER_TYPE)
        root.add_child(leaf1)
        leaf2 = Scheduler("sc1", LEAF_SCHEDULER_TYPE)
        # One leaf has parent, the other doesnt
        assert_that(leaf1 == leaf2, is_(False))
        # Compare a leaf to a root
        assert_that(leaf1 == root, is_(False))

        # compare two leafs that have an equivalent parents
        root2 = Scheduler("root", ROOT_SCHEDULER_TYPE)
        root2.add_child(leaf2)
        assert_that(leaf1 == leaf2, is_(True))

        # compare two leafs that have different owners
        h1 = Host("h1", "addr", 1234)
        leaf1.owner = h1
        assert_that(leaf1 == leaf2, is_(False))

        # compare two leafs with the same owner
        leaf2.owner = h1
        assert_that(leaf1 == leaf2, is_(True))

        # compare two roots with the same children and grand children
        leaf1.add_child(h1)
        leaf2.add_child(h1)
        assert_that(root == root2, is_(True))

        # compare one root that has more leafs than the other
        leaf3 = Scheduler("sc3", LEAF_SCHEDULER_TYPE)
        root2.add_child(leaf3)
        assert_that(root == root2, is_(False))
        del root2.children[leaf3.id]

        # compare one tree that has more hosts than the other
        leaf2.add_child(Host("h2", "addr", 12345))
        assert_that(root == root2, is_(False))
