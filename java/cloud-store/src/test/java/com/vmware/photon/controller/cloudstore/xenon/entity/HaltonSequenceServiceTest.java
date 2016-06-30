/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.IsNull.notNullValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Tests for HaltonSequenceService.
 *
 * This class includes tests for the distribution of scheduling constants, which
 * work by creating several HostService instances and then measuring some
 * properties of the distribution of their scheduling constants.
 * The metric of choice is the coefficient of variation of the differences
 * between each scheduling constant and the next greater scheduling constant.
 * A smaller coefficient of variation indicates a more even spacing between
 * scheduling constants.
 */
public class HaltonSequenceServiceTest {
  private final Logger logger = LoggerFactory.getLogger(HaltonSequenceServiceTest.class);

  // Thread count for concurrent-create tests
  private static final int THREADS = 10;

  // Maximum acceptable coefficient of variation of gaps between adjacent
  // scheduling constants.
  // This value is somewhat arbitrary, but simulation and testing showed that it
  // is a good choice for distinguishing randomly-generated and Halton-generated
  // scheduling constants.
  private static final double MAX_VARIATION = 1.13;

  /**
   * Test that a PATCH operation returns a body with index and lastValue set.
   */
  @Test
  public void testPatchBody() throws Throwable {
    TestEnvironment env = TestEnvironment.create(1);
    Operation patchOp = env.sendPatchAndWait(
        HaltonSequenceService.SINGLETON_LINK,
        new HaltonSequenceService.State());

    HaltonSequenceService.State body = patchOp.getBody(HaltonSequenceService.State.class);

    assertThat(body.index, notNullValue());
    assertThat(body.lastValue, notNullValue());
  }

  /**
   * Test that a PATCH increments the index field by exactly 1.
   */
  @Test
  public void testPatchIncrementsIndex() throws Throwable {
    TestEnvironment env = TestEnvironment.create(1);
    HaltonSequenceService.State currentState =
        env.getServiceState(
            HaltonSequenceService.SINGLETON_LINK,
            HaltonSequenceService.State.class);

    int currentIndex = currentState.index;

    Operation patchOp = env.sendPatchAndWait(
        HaltonSequenceService.SINGLETON_LINK,
        new HaltonSequenceService.State());

    int newIndex = patchOp.getBody(HaltonSequenceService.State.class).index;

    assertThat(newIndex, equalTo(currentIndex + 1));

    env.stop();
  }

  /**
   * Test distribution of scheduling constants, creating hosts serially on a
   * single Xenon host.
   */
  @Test(dataProvider = "HostCounts")
  public void testSchedulingConstantVariationSerial(int hostCount) throws Throwable {
    List<Long> schedulingConstants;
    TestEnvironment env = TestEnvironment.create(1);
    ServiceHost xenonHost = env.getHosts()[0];

    schedulingConstants = createHosts(xenonHost, hostCount);

    env.stop();

    assertThat(schedulingConstants.size(), equalTo(hostCount));
    Collections.sort(schedulingConstants);

    double cv = schedulingConstantGapCV(schedulingConstants);
    logger.info("Scheduling constant gap coefficient of variation: {}", cv);
    assertThat(cv, lessThan(MAX_VARIATION));
  }

  /**
   * Test distribution of scheduling constants, creating hosts concurrently on a
   * single Xenon host.
   */
  @Test(dataProvider = "HostCounts")
  public void testSchedulingConstantVariationConcurrent(int hostCount) throws Throwable {
    List<Long> schedulingConstants = Collections.synchronizedList(new ArrayList<>());
    TestEnvironment env = TestEnvironment.create(1);
    List<Thread> threads = new ArrayList<>();
    ServiceHost xenonHost = env.getHosts()[0];

    IntStream.range(0, THREADS).forEach((threadId) -> {
      Thread t = new Thread(() -> {
        List<Long> thisThreadSchedulingConstants = createHosts(xenonHost, hostCount);
        schedulingConstants.addAll(thisThreadSchedulingConstants);
      });
      t.start();
      threads.add(t);
    });

    for (Thread t: threads) {
      t.join();
    }

    env.stop();

    assertThat(schedulingConstants.size(), equalTo(hostCount * THREADS));
    Collections.sort(schedulingConstants);

    double cv = schedulingConstantGapCV(schedulingConstants);
    logger.info("Scheduling constant gap coefficient of variation: {}", cv);
    assertThat(cv, lessThan(MAX_VARIATION));
  }

  /**
   * Test distribution of scheduling constants, creating hosts on multiple Xenon
   * hosts, one thread per Xenon host.
   */
  @Test(dataProvider = "MultiHostHostCounts")
  public void testSchedulingConstantVariationMultiHost(int xenonHostCount, int hostCount) throws Throwable {
    List<Long> schedulingConstants = Collections.synchronizedList(new ArrayList<>());
    TestEnvironment env = TestEnvironment.create(xenonHostCount);
    List<Thread> threads = new ArrayList<>();

    ServiceHost[] xenonHosts = env.getHosts();

    IntStream.range(0, xenonHostCount).forEach((xenonHostId) -> {
      Thread t = new Thread(() -> {
        List<Long> thisThreadSchedulingConstants = createHosts(xenonHosts[xenonHostId], hostCount);
        schedulingConstants.addAll(thisThreadSchedulingConstants);
      });
      t.start();
      threads.add(t);
    });

    for (Thread t: threads) {
      t.join();
    }

    env.stop();

    assertThat(schedulingConstants.size(), equalTo(hostCount * xenonHostCount));
    Collections.sort(schedulingConstants);

    double cv = schedulingConstantGapCV(schedulingConstants);
    logger.info("Scheduling constant gap coefficient of variation: {}", cv);
    assertThat(cv, lessThan(MAX_VARIATION));
  }

  /**
   * Test for distinct scheduling constants, creating hosts serially on a single
   * Xenon host.
   */
  @Test(dataProvider = "HostCounts")
  public void testDistinctSchedulingConstantsSerial(int hostCount) throws Throwable {
    List<Long> schedulingConstants;
    TestEnvironment env = TestEnvironment.create(1);
    ServiceHost xenonHost = env.getHosts()[0];

    schedulingConstants = createHosts(xenonHost, hostCount);

    env.stop();

    assertThat(schedulingConstants.size(), equalTo(hostCount));

    // Check that all scheduling constants are distinct by adding them to a set
    // and checking the size of the set.
    Set<Long> schedulingConstantsSet = new HashSet<>();
    schedulingConstantsSet.addAll(schedulingConstants);
    assertThat(schedulingConstantsSet.size(), equalTo(schedulingConstants.size()));
  }

  /**
   * Test for distinct scheduling constants, creating hosts concurrently on a
   * single Xenon host.
   */
  @Test(dataProvider = "HostCounts")
  public void testDistinctSchedulingConstantsConcurrent(int hostCount) throws Throwable {
    List<Long> schedulingConstants = Collections.synchronizedList(new ArrayList<>());
    TestEnvironment env = TestEnvironment.create(1);
    List<Thread> threads = new ArrayList<>();
    ServiceHost xenonHost = env.getHosts()[0];

    IntStream.range(0, THREADS).forEach((threadId) -> {
      Thread t = new Thread(() -> {
        List<Long> thisThreadSchedulingConstants = createHosts(xenonHost, hostCount);
        schedulingConstants.addAll(thisThreadSchedulingConstants);
      });
      t.start();
      threads.add(t);
    });

    for (Thread t: threads) {
      t.join();
    }

    env.stop();

    assertThat(schedulingConstants.size(), equalTo(hostCount * THREADS));

    // Check that all scheduling constants are distinct (see note in
    // testDistinctSchedulingConstantsSerial)
    Set<Long> schedulingConstantsSet = new HashSet<>();
    schedulingConstantsSet.addAll(schedulingConstants);
    assertThat(schedulingConstantsSet.size(), equalTo(schedulingConstants.size()));
  }

  /**
   * Create several new HostService instances on the given Xenon ServiceHost.
   *
   * @return list of the newly-created hosts' scheduling constants
   */
  private List<Long> createHosts(ServiceHost xenonHost, int hostCount) {
    List<Long> schedulingConstants = new ArrayList<>();

    for (int i = 0; i < hostCount; i++) {
      try {
        HostService.State h = TestHelper.getHostServiceStartState();
        Operation op = Operation
            .createPost(xenonHost, HostServiceFactory.SELF_LINK)
            .setReferer(ServiceUriPaths.CLOUDSTORE_ROOT)
            .setBody(h);
        Operation completedOp = ServiceHostUtils.sendRequestAndWait(xenonHost, op, ServiceUriPaths.CLOUDSTORE_ROOT);
        long newSchedConstant = completedOp.getBody(HostService.State.class).schedulingConstant;
        schedulingConstants.add(newSchedConstant);
      } catch (Throwable t) {
        logger.error("Exception creating host: {}", t);
        // Skip this iteration. The caller should assert that the length of this
        // function's output is equal to hostCount; that should correctly cause
        // tests to fail in case of error here.
        continue;
      }
    }

    return schedulingConstants;
  }

  /**
   * Compute the coefficient of variation of the gaps between adjacent
   * scheduling constants.
   *
   * @param schedulingConstants Sorted list of scheduling constants.
   */
  private double schedulingConstantGapCV(List<Long> schedulingConstants) {
    // Compute the gaps between adjacent scheduling constants. Sort the list,
    // then compute the difference between each constant and the next.
    double[] gaps = new double[schedulingConstants.size()];

    for (int i = 0; i < schedulingConstants.size(); i++) {
      long gap;

      // Special case at end of list: wrap around
      if (i == schedulingConstants.size() - 1) {
        gap = schedulingConstants.get(0) - schedulingConstants.get(i) + 10000;
      } else {
        gap = schedulingConstants.get(i + 1) - schedulingConstants.get(i);
      }

      gaps[i] = (double) gap;
    }

    // Compute coefficient of variation.
    double gapMean = new Mean().evaluate(gaps);
    double gapSD = new StandardDeviation().evaluate(gaps);
    return gapSD / gapMean;
  }

  // Counts of HostService instances to create
  @DataProvider(name = "HostCounts")
  public Object[][] getHostCounts() {
    return new Object[][]{
        {6},
        {30},
        {100},
    };
  }

  // Counts of Xenon ServiceHosts, HostService instances to create
  @DataProvider(name = "MultiHostHostCounts")
  public Object[][] getMultiHostHostCounts() {
    return new Object[][] {
        {3, 6},
        {3, 30},
        {3, 100},
    };
  }
}
