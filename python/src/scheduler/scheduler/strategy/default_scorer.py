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

from .scorer import Scorer
from random import shuffle


class DefaultScorer(Scorer):
    """Default Placement scorer.

    Used for scoring the responses and returning the most suitable upstream
    """

    def __init__(self, ut_ratio):
        """Create a new scorer."""
        self.ut_ratio = ut_ratio

    def score(self, responses):
        """Score the place responses and return the most suitable one.

        :type responses: list of PlaceResponse
        :rtype: PlaceResponse or None
        """
        if not responses:
            return None

        # Sort below is stable, so shuffling should help
        # picking random response if scores are the same
        shuffle(responses)
        responses = sorted(responses,
                           reverse=True,
                           key=lambda r: self.score_formula(r.score))

        return responses[0]

    def score_formula(self, score):
        return self.ut_ratio * score.utilization + score.transfer

    def normalized_score_formula(self, score):
        return round(self.score_formula(score) / (self.ut_ratio + 1))
