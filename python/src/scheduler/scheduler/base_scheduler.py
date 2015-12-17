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
