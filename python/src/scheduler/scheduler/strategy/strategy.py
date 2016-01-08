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


class Strategy(object):
    """Strategy is an abstract class to represent the strategy/algorithm to
       pass placement requests to child schedulers/hosts.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def filter_child(self, children, constraints):
        """Filter child schedulers/hosts, to pick those to which placement
           request is passing down

           :param: list of str, all child scheduler/host ids
           :param: list of Constraint
           :rtype: list of str, selected child scheduler/host ids
        """
        pass
