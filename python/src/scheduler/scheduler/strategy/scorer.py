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


class Scorer(object):
    """Scorer is an abstract class to represent the strategy/algorithm to
       score the placement responses from the child schedulers/hosts.
    """
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def score(self, responses):
        """Generate an overall score for this scheduler by going through the
           list of scores returned.

           :param: List of children responses that have the scores
           :rtype: The winning response.
        """
        pass
