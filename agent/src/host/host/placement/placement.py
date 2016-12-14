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

from common.equality import EqualityMixin
from gen.scheduler.ttypes import Score


class AgentPlacementScore(EqualityMixin):
    """Placement score is used for scoring resources based on their location
     and consumption requirements.
    """

    def __init__(self, utilization, transfer):
        """Placement score.

        :type utilization: int
        :type transfer: int
        """
        self.utilization = utilization
        self.transfer = transfer

    def to_thrift(self):
        thrift_score = Score(utilization=self.utilization,
                             transfer=self.transfer)
        return thrift_score

    @staticmethod
    def from_thrift(thrift_object):
        agent_placement_score = AgentPlacementScore(
            utilization=thrift_object.utilization,
            transfer=thrift_object.tranfer)
        return agent_placement_score

    def __repr__(self):
        return "AgentPlacementScore(utilization=%d, transfer=%d)" % (
            self.utilization, self.transfer)
