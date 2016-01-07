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

    def _collect_constraints(self, resource):
        """ Get resource constraints from placement request's resource

        :param resource: resource.Resource, the resource to be placed
        :return: set of ResourceConstraint
        """
        thrift_constraints = set()
        if resource:
            if resource.vm and resource.vm.resource_constraints:
                thrift_constraints = thrift_constraints.union(
                    resource.vm.resource_constraints)
            if resource.disks:
                for disk in resource.disks:
                    if disk.resource_constraints:
                        thrift_constraints = thrift_constraints.union(
                            disk.resource_constraints)
        return list(thrift_constraints)
