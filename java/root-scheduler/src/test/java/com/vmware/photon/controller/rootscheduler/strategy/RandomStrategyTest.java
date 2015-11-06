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

package com.vmware.photon.controller.rootscheduler.strategy;

import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.service.ManagedScheduler;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.Scheduler;

import com.google.common.collect.ImmutableList;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tests {@link ManagedScheduler}.
 */
public class RandomStrategyTest extends PowerMockTestCase {

  private static final int SCHED_NUM = 32;
  // Nodes 0-2 have the same network as 29-31, nodes 3-28 have unique networks
  private static final int NETWORK_MOD = 29;
  // The same datastores are repeated every 8 nodes
  private static final int DATASTORE_MOD = 8;
  // The same datastores are repeated every 2 nodes
  private static final int DATASTORE_DIV_HALF = (SCHED_NUM / 2);
  // Number of slices for negative host testing
  private static final int SLICE_NUM = (SCHED_NUM / 8);
  // 8 different availability zones
  private static final int AVAILABILITY_ZONE_COUNT = 8;
  private static final int AVAILABILITY_ZONE_SIZE =
      (SCHED_NUM / AVAILABILITY_ZONE_COUNT);

  private List<ManagedScheduler> schedulers;
  private List<ManagedScheduler> schedulersOddCount;
  private Strategy placementStrategy;
  @Mock
  private StaticServerSetFactory serverSetFactory;
  @Mock
  private ClientPoolFactory<Scheduler.AsyncClient> clientPoolFactory;
  @Mock
  private ClientProxyFactory<Scheduler.AsyncClient> clientProxyFactory;
  @Mock
  private ServerSet serverSet;
  @Mock
  private ClientPool<Scheduler.AsyncClient> clientPool;
  @Mock
  private ClientProxy<Scheduler.AsyncClient> clientProxy;
  @Mock
  private Scheduler.AsyncClient client;
  private Config config;
  private PlaceParams placeParams;

  @BeforeMethod
  public void setUp() throws Exception {
    config = ConfigBuilder.build(Config.class,
        RandomStrategyTest.class.getResource("/config.yml").getPath());
    config.initRootPlaceParams();
    placeParams = config.getRootPlaceParams();

    when(serverSetFactory.create(any(InetSocketAddress[].class))).thenReturn(serverSet);
    when(clientPoolFactory.create(eq(serverSet), any(ClientPoolOptions.class))).thenReturn(clientPool);
    when(clientProxyFactory.create(clientPool)).thenReturn(clientProxy);

    when(clientProxy.get()).thenReturn(client);

    schedulers = new ArrayList<>();
    schedulersOddCount = new ArrayList<>();
    for (int index = 0; index < SCHED_NUM; index++) {
      InetSocketAddress address = InetSocketAddress.createUnresolved("foo", index);
      ManagedScheduler scheduler = new ManagedScheduler(
          "scheduler_" + index, address, "owner_" + index,
          new Config(), serverSetFactory,
          clientPoolFactory, clientProxyFactory);
      // Create resources
      Set<ResourceConstraint> resources = new TreeSet<>();
      // Datastores are distributed in this way:
      // i) the same datastore recurs every DATASTORE_MOD hosts
      // ii) 4 datastores are available to the lower HALF and 4 to the upper HALF
      // for example (assuming DATASTORE_MOD is 8 and SCHED_NUM is 32):
      // schedulers 0, 8, 16, .. have access to datastore_0
      // schedulers 1, 9, 17, .. have access to datastore_1,
      // ..
      // In addition to that, the first half of the schedulers has access to:
      // datastore_32, datastore_33, datastore_34, datastore_35
      // the second half to
      // datastore_64, datastore_65, datastore_66, datastore_67
      //
      int datastoreHalfIndex = (SCHED_NUM * ((index / DATASTORE_DIV_HALF) + 1));
      resources.add(new ResourceConstraint(
          ResourceConstraintType.DATASTORE,
          ImmutableList.of(
              "datastore_" + (index % DATASTORE_MOD),
              "datastore_" + datastoreHalfIndex,
              "datastore_" + (datastoreHalfIndex + 1),
              "datastore_" + (datastoreHalfIndex + 2),
              "datastore_" + (datastoreHalfIndex + 3)
          )));
      resources.add(new ResourceConstraint(
          ResourceConstraintType.NETWORK,
          ImmutableList.of("network_" + index % NETWORK_MOD)));

      int availabilityZoneIndex = index / AVAILABILITY_ZONE_SIZE;
      resources.add(new ResourceConstraint(
          ResourceConstraintType.AVAILABILITY_ZONE,
          ImmutableList.of(
              "availability_zone_" + availabilityZoneIndex
          )
      ));

      // Each scheduler has access to 3 hosts, for instance (assuming SCHED_NUM 32):
      // scheduler 0 has host_0, host_32 and host_64,
      // scheduler 1 has host_1, host_33 and host_65,
      // ..
      resources.add(new ResourceConstraint(
          ResourceConstraintType.HOST,
          ImmutableList.of(
              "host_" + index,
              "host_" + (index + SCHED_NUM),
              "host_" + (index + (SCHED_NUM * 2)))));
      scheduler.setResources(resources);
      scheduler.setWeight(8);
      schedulers.add(scheduler);
      if (index < SCHED_NUM - 1) {
        schedulersOddCount.add(scheduler);
      }
    }

    placementStrategy = new RandomStrategy();
  }

  private int applyPlacementRatio(int num) {
    Double res = num * placeParams.getFanoutRatio();
    return Math.min(res.intValue(), placeParams.getMaxFanoutCount());
  }

  @Test
  public void testSuccessfulFilterChildren() throws Exception {
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, null);
    assertThat(selectedSchedulers.size(), is(applyPlacementRatio(schedulers.size())));
  }

  @Test
  public void testSuccessfulFilterChildrenOddCount() throws Exception {
    placeParams.setMaxFanoutCount(32);
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(placeParams,
            schedulersOddCount, null);
    assertThat(selectedSchedulers.size(),
        is(applyPlacementRatio(schedulersOddCount.size())));
  }

  @Test
  public void testSuccessfulFilterChildrenMinFanout() throws Exception {
    placeParams.setMinFanoutCount(20);
    placeParams.setMaxFanoutCount(20);
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(placeParams, schedulers, null);
    assertThat(selectedSchedulers.size(), is(20));
  }

  @Test
  public void testSuccessfulFilterChildrenMaxFanout() throws Exception {
    placeParams.setMaxFanoutCount(4);
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(placeParams, schedulers, null);
    assertThat(selectedSchedulers.size(), is(4));
  }

  @Test
  public void testSuccessfulFilterChildrenLargeMin() throws Exception {
    placeParams.setMinFanoutCount(128);
    placeParams.setMaxFanoutCount(256);
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(placeParams, schedulers, null);
    assertThat(selectedSchedulers.size(), is(schedulers.size()));
  }

  @Test
  public void testSuccessfulFilterChildrenFanoutRatio() throws Exception {
    placeParams.setFanoutRatio(0.25);
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(placeParams, schedulers, null);
    assertThat(selectedSchedulers.size(), is(applyPlacementRatio(schedulers.size())));
  }

  @Test
  public void testSuccessfulFilterChildrenWithEmptyConstraints() throws Exception {
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(),
            schedulers, new HashSet<ResourceConstraint>());
    assertThat(selectedSchedulers.size(), is(applyPlacementRatio(schedulers.size())));
  }

  @Test
  public void testSuccessfulFilterChildrenWithDatastoreConstraint() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore_3")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(),
            schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(schedulers.size() / DATASTORE_MOD));
    for (ManagedScheduler selected : selectedSchedulers) {
      Set<ResourceConstraint> resources = selected.getResources();
      for (ResourceConstraint resource : resources) {
        if (resource.getType() != ResourceConstraintType.DATASTORE) {
          continue;
        }
        assertThat(resource.getValues().contains("datastore_3"), is(true));
      }
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWithNetworkConstraint() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of("network_1")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(2));
    for (ManagedScheduler selected : selectedSchedulers) {
      Set<ResourceConstraint> resources = selected.getResources();
      for (ResourceConstraint resource : resources) {
        if (resource.getType() != ResourceConstraintType.NETWORK) {
          continue;
        }
        assertThat(resource.getValues().equals(ImmutableList.of("network_1")), is(true));
      }
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWithAvailZoneConstraint() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE,
        ImmutableList.of("availability_zone_2")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(AVAILABILITY_ZONE_SIZE));
    for (ManagedScheduler selected : selectedSchedulers) {
      Set<ResourceConstraint> resources = selected.getResources();
      for (ResourceConstraint resource : resources) {
        if (resource.getType() != ResourceConstraintType.AVAILABILITY_ZONE) {
          continue;
        }
        assertThat(resource.getValues().equals(
            ImmutableList.of("availability_zone_2")), is(true));
      }
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWithDsAndNwConstraints() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore_7")));
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of("network_7")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(1));
    Set<ResourceConstraint> resources = selectedSchedulers.get(0).getResources();
    for (ResourceConstraint resource : resources) {
      if (resource.getType() == ResourceConstraintType.NETWORK) {
        assertThat(resource.getValues().equals(ImmutableList.of("network_7")), is(true));
      }
      if (resource.getType() == ResourceConstraintType.DATASTORE) {
        assertThat(resource.getValues().contains("datastore_7"), is(true));
      }
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWithDsAndAzConstraints() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore_7")));
    constraints.add(new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE, ImmutableList.of("availability_zone_1")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(1));
    Set<ResourceConstraint> resources = selectedSchedulers.get(0).getResources();
    for (ResourceConstraint resource : resources) {
      if (resource.getType() == ResourceConstraintType.AVAILABILITY_ZONE) {
        assertThat(resource.getValues().equals(ImmutableList.of("availability_zone_1")), is(true));
      }
      if (resource.getType() == ResourceConstraintType.DATASTORE) {
        assertThat(resource.getValues().contains("datastore_7"), is(true));
      }
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWithNegativeConstraints() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    // The network constraint selects leaf scheduler 0 and 29
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of("network_0")));
    // The host negative constraints exclude scheduler 0
    ResourceConstraint hostConstraint =
        new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of("host_0", "host_32", "host_64"));
    hostConstraint.setNegative(true);
    constraints.add(hostConstraint);

    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(1));
    for (ManagedScheduler selected : selectedSchedulers) {
      Set<ResourceConstraint> resources = selected.getResources();
      for (ResourceConstraint resource : resources) {
        if (resource.getType() != ResourceConstraintType.NETWORK) {
          continue;
        }
        assertThat(resource.getValues().equals(ImmutableList.of("network_0")), is(true));
      }
    }
  }

  @Test
  public void testUnsuccessfulFilterChildrenWithDatastoreConstraint() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore_8")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(),
            schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(0));
  }

  @Test
  public void testUnsuccessfulFilterChildrenWithNetworkConstraint() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of("network_29")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(0));
  }

  @Test
  public void testUnsuccessfulFilterChildrenWithBothConstraints() throws Exception {
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore_8")));
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of("network_29")));
    List<ManagedScheduler> selectedSchedulers =
        placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
    assertThat(selectedSchedulers.size(), is(0));
  }

  @Test
  public void testSuccessfulFilterChildrenWeighted() {
    /**
     * This test relies on the fact that given the
     * same random seeding the placement results should
     * be exactly the same. Changes in the placement
     * algorithm may return different results but the
     * ratios are expected to be approximately the
     * same.
     */
    placementStrategy.init(112358);
    int[] weights = {1, 8, 16, 32};
    int[] results = {0, 0, 0, 0};
    int schedulerIndex = 0;
    for (ManagedScheduler scheduler : schedulers) {
      scheduler.setWeight(weights[schedulerIndex % 4]);
      schedulerIndex++;
    }

    // Run random selection of 4 schedulers multiple times
    config.getRoot().setMaxFanoutCount(4);
    for (int index = 0; index < 10000; index++) {
      List<ManagedScheduler> selected =
          placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, null);
      for (ManagedScheduler scheduler : selected) {
        if (scheduler.getWeight() == SchedulerWeight.SCHED_WEIGHT_1.getWeight()) {
          results[SchedulerWeight.SCHED_WEIGHT_1.getIndex()]++;
        } else if (scheduler.getWeight() == SchedulerWeight.SCHED_WEIGHT_8.getWeight()) {
          results[SchedulerWeight.SCHED_WEIGHT_8.getIndex()]++;
        } else if (scheduler.getWeight() == SchedulerWeight.SCHED_WEIGHT_16.getWeight()) {
          results[SchedulerWeight.SCHED_WEIGHT_16.getIndex()]++;
        } else if (scheduler.getWeight() == SchedulerWeight.SCHED_WEIGHT_32.getWeight()) {
          results[SchedulerWeight.SCHED_WEIGHT_32.getIndex()]++;
        }
      }
    }
    int[] expectedResults = {792, 5969, 11495, 21744};
    for (int index = 0; index < SchedulerWeight.values().length; index++) {
      assertThat(results[index], is(expectedResults[index]));
    }
  }

  @Test
  public void testSuccessfulFilterChildrenWeightedWithConstraints() {
    /**
     * This test relies on the fact that given the
     * same random seeding the placement results should
     * be exactly the same. Changes in the placement
     * algorithm may return different results but the
     * ratios are expected to be approximately the
     * same.
     */
    placementStrategy.init(112358);
    int[] weights = {1, 8, 16, 32};
    int[] results = new int[SLICE_NUM];
    int schedulerIndex = 0;
    for (ManagedScheduler scheduler : schedulers) {
      scheduler.setWeight(weights[schedulerIndex % 4]);
      schedulerIndex++;
    }

    /**
     * Select half of the schedulers with a positive constraint,
     * sched0, sched[SCHED_NUM/2]
     */
    Set<ResourceConstraint> constraints = new TreeSet<ResourceConstraint>();
    constraints.add(new ResourceConstraint(
        ResourceConstraintType.DATASTORE,
        ImmutableList.of(
            "datastore_" + SCHED_NUM,
            "datastore_" + (SCHED_NUM + 1))));

    /**
     * Each scheduler[i] has three hosts, host[i], host[i+SCHED_NUM], host[i+(SCHED_NUM*2)]
     * Use negative host constraints to select schedulers with this algorithm:
     * N = SCHED_NUM/8
     * scheduler0, scheduler[N]: 3 valid hosts,
     * scheduler[N], scheduler[2N]: 2 valid hosts,
     * scheduler[2N], scheduler[3N]: 1 valid host,
     * scheduler[3N], scheduler[4N]: no valid host
     */

    /**
     * Add negative constraints
     */

    int sliceIndex;
    List<String> constraintValues = new ArrayList<>();

    for (ManagedScheduler scheduler : schedulers) {
      schedulerIndex = getSchedulerIndex(scheduler);
      sliceIndex = getSliceIndex(scheduler);
      switch (sliceIndex) {
        case 1:
          constraintValues.add("host_" + schedulerIndex);
          break;
        case 2:
          constraintValues.add("host_" + schedulerIndex);
          constraintValues.add("host_" + (schedulerIndex + SCHED_NUM));
          break;
        case 3:
          constraintValues.add("host_" + schedulerIndex);
          constraintValues.add("host_" + (schedulerIndex + SCHED_NUM));
          constraintValues.add("host_" + (schedulerIndex + (SCHED_NUM * 2)));
          break;
      }
    }

    ResourceConstraint negativeConstraint = new ResourceConstraint(
        ResourceConstraintType.HOST, constraintValues);
    negativeConstraint.setNegative(true);
    constraints.add(negativeConstraint);

    // Run random selection of 4 schedulers multiple times
    config.getRoot().setMaxFanoutCount(4);
    for (int index = 0; index < 10000; index++) {
      List<ManagedScheduler> selected =
          placementStrategy.filterChildren(config.getRootPlaceParams(), schedulers, constraints);
      // Count selected schedulers
      for (ManagedScheduler scheduler : selected) {
        sliceIndex = getSliceIndex(scheduler);
        results[sliceIndex]++;
      }
    }
    int[] expectedResults = {18993, 13638, 7369, 0};
    for (int index = 0; index < SchedulerWeight.values().length; index++) {
      assertThat(results[index], is(expectedResults[index]));
    }
  }

  private int getSchedulerIndex(ManagedScheduler scheduler) {
    String id = scheduler.getId();
    String[] parts = id.split("_");
    int index = Integer.parseInt(parts[1]);
    return index;
  }

  private int getSliceIndex(ManagedScheduler scheduler) {
    return getSchedulerIndex(scheduler) / SLICE_NUM;
  }

  /**
   * Weights reflect number of children.
   */
  public enum SchedulerWeight {
    SCHED_WEIGHT_1(0, 1),
    SCHED_WEIGHT_8(1, 8),
    SCHED_WEIGHT_16(2, 16),
    SCHED_WEIGHT_32(3, 32);
    private final int index;
    private final int weight;

    SchedulerWeight(int index, int weight) {
      this.index = index;
      this.weight = weight;
    }

    public int getIndex() {
      return this.index;
    }

    public int getWeight() {
      return this.weight;
    }
  }

}
