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

import logging
import math
import random

from .scorer import Scorer
from gen.scheduler.ttypes import PlaceParams
from scheduler.strategy.strategy import Strategy


class ScoringHelper(object):
    """
    Helper class that does statistical processing on the scores.
    """
    def __init__(self, responses, ut_ratio):
        """
        Initializes the helper with the responses and their corresponding
        scores. The list order is sorted according to score in descending
        manner.

        :param responses: The responses that were received from the children.
        :param ut_ratio: Ratio of utilization vs transfer scores
        """
        self.ut_ratio = ut_ratio
        self.responses = sorted(responses,
                                reverse=True,
                                key=lambda x: self.score_formula(x.score))
        self.scores = [self.score_formula(r.score) for r in self.responses]
        self.mean = sum(self.scores) / float(len(self.scores))
        self.score_deviations = self.deviation_from_mean()

    def variance(self):
        squared = [pow(s - self.mean, 2) for s in self.scores]
        return sum(squared) / float(len(squared))

    def stddev(self):
        return math.sqrt(self.variance())

    def deviation_from_mean(self):
        """
        Returns the standard deviation interval for each score. Lower
        intervals are returned as negative integers.
        :return: dictionary of score - std. dev. interval pairs.
        """
        stddev = self.stddev()
        if stddev > 0:
            return [round((v - self.mean)/stddev) for v in self.scores]
        else:
            return None

    def get_top_k(self):
        if self.score_deviations:
            # Get standard deviation interval of the top score.
            top = self.score_deviations[0]

            # Get scores in the same standard deviation interval.
            return [self.responses[i]
                    for (i, v) in enumerate(self.score_deviations) if v == top]
        else:
            return self.responses

    def score_formula(self, score):
        return self.ut_ratio * score.utilization + score.transfer


class RandomSubsetStrategy(Strategy):
    """This strategy picks random subset of children to pass the placement
       message down.
    """

    def __init__(self, fanout_ratio, min_fanout, max_fanout=None):
        """Constructor

           :type fanout_ratio: double (0,1]
           :type min_fanout: int, >= 1
        """
        self.fanout_ratio = fanout_ratio
        self.min_fanout = min_fanout
        self.max_fanout = max_fanout
        self._logger = logging.getLogger(__name__)

    def filter_child(self, children, request, constraints=[]):
        """filter hosts with acquired resource constraints.

        :param children: list of ChildInfo with coalesced resource constraints
        :param request: PlaceRequest
        :param constraints: request's resource constraints
        :rtype: list of ChildInfo
        """
        place_params = self._get_place_params(request)
        fanout_size = int(
            math.ceil(len(children) * place_params.fanoutRatio))
        fanout_size = max(fanout_size, place_params.minFanoutCount)
        if self.max_fanout:
            fanout_size = min(fanout_size, place_params.maxFanoutCount)

        if len(constraints) == 0:
            result = children[:]
        else:
            result = [child for child in children
                      if self._satisfy(child, constraints)]
            self._logger.info(
                "Scheduler, found [%s] children with constraints: [%s]",
                [child.id for child in result], constraints)

        random.shuffle(result)
        result = result[:fanout_size]
        self._logger.debug("Fanning out %d children to: [%s]", fanout_size,
                           ",".join(str(child.id) for child in result))
        return result

    def _satisfy(self, child, constraints):
        """Check if child satisfies constrains

        :param child: ChildInfo with coalesced resource constraints
        :param constraints: request's resource constraints
        :rtype boolean
        """
        # dict of host's ResourceConstraint
        child_constraints_dict = self._get_constraints(child)

        if len(child_constraints_dict) > 0:
            self._logger.info("Scheduler, applying constraints: [%s]",
                              constraints)

        for constraint in constraints:
            if constraint.type in child_constraints_dict:
                resource_value_set = \
                    set(child_constraints_dict[constraint.type])

                valid = self._validate_single_constraint(
                    constraint, resource_value_set)

                if not valid:
                    return False
            else:
                return False

        return True

    @staticmethod
    def _validate_single_constraint(constraint, resource_value_set):
        if not constraint.negative:
            # If this is a positive constraint verify that at
            # least one constraint is matched by a resource
            for value in constraint.values:
                if value in resource_value_set:
                    return True
            return False
        else:
            # If this is a negative constraint, verify that at least
            # one of the resources is not in the negative list
            for value in resource_value_set:
                if value not in constraint.values:
                    return True
            return False

    @staticmethod
    def _get_constraints(child):
        """ Get constraints of a specific child

        :param child: child scheduler id for branch scheduler, child host id
                      for leaf scheduler.
        :type: ChildInfo
        :return: dict of ResourceConstraint.
        :rtype: dict of ResourceConstraint
            Key: ResourceConstraint.type
            Value: ResourceConstraint.values
        """
        child_constraints_dict = {}

        if not child.constraints:
            return child_constraints_dict

        for constraint in child.constraints:
            child_constraints_dict[constraint.type] = constraint.values

        return child_constraints_dict

    def _get_place_params(self, request):
        out_params = PlaceParams(fanoutRatio=self.fanout_ratio,
                                 minFanoutCount=self.min_fanout,
                                 maxFanoutCount=self.max_fanout)
        if request.leafSchedulerParams is None:
            return out_params

        in_params = request.leafSchedulerParams
        if in_params.fanoutRatio is not None:
            out_params.fanoutRatio = in_params.fanoutRatio
        if in_params.minFanoutCount is not None:
            out_params.minFanoutCount = in_params.minFanoutCount
        if in_params.maxFanoutCount is not None:
            out_params.maxFanoutCount = in_params.maxFanoutCount

        return out_params


class RandomScorer(Scorer):
    def __init__(self, ut_ratio):
        """
        Selects a random host from the higher standard deviation interval in
        the list.
        :param ut_ratio: Ratio of utilization vs transfer scores
        """
        self.ut_ratio = ut_ratio

    def score(self, responses):
        """
        Score the place responses and return the most suitable one.
        This improves upon the Default scorer by randomly selecting a
        response, when there are multiple responses that are close to each
        other.

        :type responses: list of PlaceResponse
        :rtype: PlaceResponse or None
        """
        if not responses:
            return None

        stats = ScoringHelper(responses, self.ut_ratio)
        responses = stats.get_top_k()
        return random.choice(responses)
