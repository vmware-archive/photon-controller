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
import random

from hamcrest import *  # noqa

from gen.scheduler.ttypes import PlaceResponse
from gen.scheduler.ttypes import Score
from nose_parameterized import parameterized
from scheduler.strategy.default_scorer import DefaultScorer
from scheduler.strategy.random_subset_strategy import RandomScorer


class ScorerTestCase(unittest.TestCase):

    def setUp(self):
        self.scorer = DefaultScorer(ut_ratio=9)

    def test_highest_score_random(self):
        place_responses = []
        for i in xrange(10):
            resp = PlaceResponse(score=Score(random.randint(0, 100),
                                             random.randint(0, 100)))
            place_responses.append(resp)
        self._test_highest_score(place_responses)

    @parameterized.expand([
        ([Score(40, 90), Score(49, 78), Score(50, 70)], 9, Score(50, 70)),
        ([Score(40, 90), Score(49, 80), Score(50, 70)], 9, Score(49, 80)),
        ([Score(40, 90), Score(45, 80), Score(50, 70)], 1, Score(40, 90)),
        ([Score(40, 90), Score(45, 86), Score(50, 70)], 1, Score(45, 86)),
        ([Score(40, 60), Score(59, 69), Score(50, 70)], 0.1, Score(50, 70)),
        ([Score(40, 60), Score(61, 69), Score(50, 70)], 0.1, Score(61, 69)),
    ])
    def test_ratio(self, scores, ratio, expected):
        scorer = DefaultScorer(ut_ratio=ratio)
        place_responses = [PlaceResponse(score=score) for score in scores]
        response = scorer.score(place_responses)
        assert_that(response.score, is_(expected))

    def _test_highest_score(self, place_responses):
        response = self.scorer.score(place_responses)
        scores = [self.scorer.score_formula(resp.score) for resp in
                  place_responses]

        assert_that(self.scorer.score_formula(response.score), is_(max(
            scores)))

    def test_new_resource_score(self):
        response = self.scorer.score([
            PlaceResponse(score=Score(40, 0)),
            PlaceResponse(score=Score(50, 0))
        ])

        # should pick one with higher utilization score
        assert_that(response, is_(PlaceResponse(score=Score(50, 0))))

    def test_same_score_pick_random(self):
        responseA = PlaceResponse(score=Score(98, 100))
        responseB = PlaceResponse(score=Score(98, 100))

        a_freq = 0
        b_freq = 0

        # Still can fail with 1/2^30 probability, should be negligible
        for i in xrange(0, 30):
            response = self.scorer.score([responseA, responseB])
            if response is responseA:
                a_freq += 1
            else:
                b_freq += 1

        assert_that(a_freq, greater_than(0))
        assert_that(b_freq, greater_than(0))

    @parameterized.expand([
        ([Score(60, 0), Score(61, 0), Score(62, 0)], 1, [Score(62, 0)]),
        ([Score(50, 90), Score(50, 90), Score(49, 90)], 1, [Score(50, 90)]),
        ([Score(60, 0), Score(70, 0), Score(69, 0)], 1, [Score(69, 0),
                                                         Score(70, 0)]),
        ([Score(50, 90), Score(45, 80), Score(10, 70)], 9, [Score(50, 90),
                                                            Score(45, 80)]),
    ])
    def test_random_scorer_close_scores(self, scores, ut_ratio, expected):
        scorer = RandomScorer(ut_ratio=ut_ratio)
        place_responses = [PlaceResponse(score=score) for score in scores]
        response = scorer.score(place_responses)
        assert_that(expected, has_item(response.score))


if __name__ == '__main__':
    unittest.main()
