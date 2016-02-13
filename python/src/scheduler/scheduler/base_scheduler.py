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

import abc

from gen.resource.ttypes import ResourceConstraint


class InvalidScheduler(Exception):
    pass


class BaseScheduler(object):
    """Base scheduler interface."""
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def find(self, request):
        """Find the specified resource.

        :type request: FindRequest
        :rtype: FindResponse
        """
        pass

    @abc.abstractmethod
    def place(self, request):
        """Place the specified resources.

        :type request: PlaceRequest
        :rtype: PlaceResponse
        """
        pass

    @abc.abstractmethod
    def cleanup(self):
        """ Cleanup as part of a scheduler demotion """
        pass

    def _coalesce_resources(self, children):
        """Coalesce resources by resource type.

        Build the coalesced ResourceConstraint with same type.
        i.e With multiple input ResourceConstraints, we'll aggregate
        all the values with same resource type.

        :param children: list of ChildInfo
        """
        for child_info in children:
            # No constraints, skip child
            if not child_info.constraints:
                continue

            constraints = {}
            for constraint in child_info.constraints:
                if constraint.type not in constraints:
                    constraints[constraint.type] = set()
                constraints[constraint.type].update(set(constraint.values))

            child_info.constraints = []

            for constraint_type, values in constraints.iteritems():
                child_info.constraints.append(
                    ResourceConstraint(constraint_type, list(values)))

    def _make_constraints_hashable(self, thrift_constraints):
        """ Before adding constraints to set(), we need to convert
            ResourceConstraint.values from unhashable list type to
            hashable frozenset type. set() uses objects' hash to
            compare them.
        :param thrift_constraints:
        :return: hashable_constraints
        """
        hashable_constraints = []
        for thrift_constraint in thrift_constraints:
            hashable_constraints.append(ResourceConstraint(
                thrift_constraint.type,
                frozenset(thrift_constraint.values),
                thrift_constraint.negative))
        return hashable_constraints

    def _collect_constraints(self, resource):
        """ Get resource constraints from placement request's resource

        :param resource: resource.Resource, the resource to be placed
        :return: set of ResourceConstraint
        """
        all_constraints = set()
        if resource:
            if resource.vm and resource.vm.resource_constraints:
                constraints = self._make_constraints_hashable(
                    resource.vm.resource_constraints)
                all_constraints = all_constraints.union(constraints)
            if resource.disks:
                for disk in resource.disks:
                    if disk.resource_constraints:
                        constraints = self._make_constraints_hashable(
                            disk.resource_constraints)
                        all_constraints = all_constraints.union(constraints)
        return list(all_constraints)
