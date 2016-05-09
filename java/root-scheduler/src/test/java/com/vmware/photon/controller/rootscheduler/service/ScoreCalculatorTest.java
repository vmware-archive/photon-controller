/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.rootscheduler.RootSchedulerConfig;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Score;

import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests {@link ScoreCalculator}.
 */
public class ScoreCalculatorTest {
  @Mock
  RootSchedulerConfig config;

  @BeforeTest
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * {@link ScoreCalculator#pickBestResponse(Set)} should return null when the
   * response set is empty.
   */
  @Test
  void testEmptyResponse() {
    ScoreCalculator calculator = new ScoreCalculator(config);
    assertThat(calculator.pickBestResponse(null), is(nullValue()));
    assertThat(calculator.pickBestResponse(new HashSet<>()), is(nullValue()));
  }

  @Test
  void testPickBestResponse() {
    SchedulerConfig schedulerConfig = mock(SchedulerConfig.class);
    doReturn(schedulerConfig).when(config).getRoot();
    ScoreCalculator calculator = new ScoreCalculator(config);
    PlaceResponse better = new PlaceResponse(PlaceResultCode.OK);
    PlaceResponse worse = new PlaceResponse(PlaceResultCode.OK);

    doReturn(1.0).when(schedulerConfig).getUtilizationTransferRatio();
    better.setScore(new Score(2, 0));
    worse.setScore(new Score(1, 0));
    Set<PlaceResponse> responses = ImmutableSet.of(better, worse);
    assertThat(calculator.pickBestResponse(responses), is(better));

    doReturn(0.0).when(schedulerConfig).getUtilizationTransferRatio();
    better.setScore(new Score(1, 1));
    worse.setScore(new Score(2, 0));
    responses = ImmutableSet.of(better, worse);
    assertThat(calculator.pickBestResponse(responses), is(better));
  }
}
