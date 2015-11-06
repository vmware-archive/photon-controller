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

import com.vmware.photon.controller.resource.gen.Disk;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.resource.gen.Vm;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.Roles;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
import com.vmware.photon.controller.rootscheduler.SchedulerFactory;
import com.vmware.photon.controller.rootscheduler.strategy.RandomStrategy;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.FindResultCode;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Score;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.collection.IsIn.isIn;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Tests {@link SchedulerManager}.
 */
public class SchedulerManagerTest extends PowerMockTestCase {
  private static long testTimeout = 60000;
  // Used during verification to match the content of the
  // managed scheduler resource list with the resource list
  // in the configuration request
  Map<String, List<ResourceConstraint>> mapOfResources;
  @Mock
  private SchedulerFactory schedulerFactory;
  @Mock
  private HealthChecker healthChecker;
  private SchedulerManager manager;
  private ManagedScheduler foo = mock(ManagedScheduler.class);
  private ManagedScheduler bar = mock(ManagedScheduler.class);
  private ManagedScheduler baz = mock(ManagedScheduler.class);
  private ManagedScheduler sched10;
  private ManagedScheduler sched11;
  private ManagedScheduler sched12;
  private String hostPrefix;
  private Config config;

  @Captor
  private ArgumentCaptor<List<ManagedScheduler>> captorSchedulers;

  @BeforeMethod
  public void setUp() {
    when(schedulerFactory.createHealthChecker(any(SchedulerRole.class))).thenReturn(healthChecker);
    config = new Config();
    config.initRootPlaceParams();

    hostPrefix = "hostOf-";
    manager = spy(new SchedulerManager(schedulerFactory, config, new RandomStrategy()));
    manager.healthChecker = healthChecker;

    when(foo.getWeight()).thenReturn(8);
    when(bar.getWeight()).thenReturn(8);
    when(baz.getWeight()).thenReturn(8);

    sched10 = mock(ManagedScheduler.class);
    sched11 = mock(ManagedScheduler.class);
    sched12 = mock(ManagedScheduler.class);

    when(sched10.getWeight()).thenReturn(8);
    when(sched11.getWeight()).thenReturn(8);
    when(sched12.getWeight()).thenReturn(8);

    when(foo.getId()).thenReturn("foo");
    when(bar.getId()).thenReturn("bar");
    when(baz.getId()).thenReturn("baz");
    when(schedulerFactory.create(eq("foo"), any(InetSocketAddress.class), any(String.class))).thenReturn(foo);
    when(schedulerFactory.create(eq("bar"), any(InetSocketAddress.class), any(String.class))).thenReturn(bar);
    when(schedulerFactory.create(eq("baz"), any(InetSocketAddress.class), any(String.class))).thenReturn(baz);

    when(sched10.getId()).thenReturn("sched10");
    when(sched11.getId()).thenReturn("sched11");
    when(sched12.getId()).thenReturn("sched12");
    when(schedulerFactory.create(eq("sched10"), any(InetSocketAddress.class), any(String.class))).thenReturn(sched10);
    when(schedulerFactory.create(eq("sched11"), any(InetSocketAddress.class), any(String.class))).thenReturn(sched11);
    when(schedulerFactory.create(eq("sched12"), any(InetSocketAddress.class), any(String.class))).thenReturn(sched12);

    // Call real methods for resources, so we can verify its content
    mapOfResources = new HashMap<>();
    when(sched10.getResources()).thenCallRealMethod();
    when(sched11.getResources()).thenCallRealMethod();
    when(sched12.getResources()).thenCallRealMethod();

    Mockito.doCallRealMethod().when(sched10).setResources(Mockito.any(Set.class));
    Mockito.doCallRealMethod().when(sched11).setResources(Mockito.any(Set.class));
    Mockito.doCallRealMethod().when(sched12).setResources(Mockito.any(Set.class));

    Mockito.doCallRealMethod().when(sched10).setResources(Mockito.any(ChildInfo.class));
    Mockito.doCallRealMethod().when(sched11).setResources(Mockito.any(ChildInfo.class));
    Mockito.doCallRealMethod().when(sched12).setResources(Mockito.any(ChildInfo.class));
  }

  private String hostName(String schName) {
    return hostPrefix + schName;
  }

  @Test
  public void testApplyConfiguration() throws IOException {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar");

    manager.applyConfiguration(configuration);
    verify(schedulerFactory).create(eq("foo"), any(InetSocketAddress.class), any(String.class));
    verify(schedulerFactory).create(eq("bar"), any(InetSocketAddress.class), any(String.class));
    verify(healthChecker).start();

    // Reconfigure the root scheduler
    manager.applyConfiguration(configuration);
    // Verify that the previous schedulers were cleaned up
    for (ManagedScheduler leaf : manager.getManagedSchedulersMap().values()) {
      verify(leaf).cleanUp();
    }

    assertThat(manager.getManagedSchedulersMap().values(), hasItems(foo, bar));
  }

  @Test
  public void testApplyConfigurationWithoutLeaves() throws IOException {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar");
    manager.applyConfiguration(configuration);
    assertThat(manager.getManagedSchedulersMap().values(), hasItems(foo, bar));

    // Configure root scheduler with no leaves, then verify that
    // old leaves are removed
    SchedulerRole schedulerRole = new SchedulerRole("ROOT");
    schedulerRole.setParent_id("None");
    ConfigureRequest request = getConfigureRequest(schedulerRole);
    manager.applyConfiguration(request);
    verify(healthChecker, times(2)).stop();
    assertThat(manager.getManagedSchedulersMap().size(), is(0));
  }

  @Test
  public void testGetPlacementSchedulers() throws Exception {

    RandomStrategy randomStrategy = mock(RandomStrategy.class);

    manager = spy(new SchedulerManager(schedulerFactory, config, randomStrategy));

    ChildInfo c1 = new ChildInfo("foo", "foo", 1024);
    ChildInfo c2 = new ChildInfo("bar", "bar", 1024);
    c1.setOwner_host(hostName("foo"));
    c2.setOwner_host(hostName("bar"));
    List<ResourceConstraint> constraints = new ArrayList();
    constraints.add(new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY, ImmutableList.of("")));
    c1.setConstraints(constraints);

    ManagedScheduler scheduler = mock(ManagedScheduler.class);
    when(scheduler.getResources()).thenReturn(new HashSet(constraints));
    when(schedulerFactory.create(eq(c1.getId()), any(InetSocketAddress.class),
            any(String.class))).thenReturn(scheduler);

    ConfigureRequest configuration = getConfigureRequestFromChildren(c1, c2);
    manager.applyConfiguration(configuration);

    // Verify that there are two managed schedulers
    assertThat(manager.getManagedSchedulersMap().size(), is(2));

    // Verify that management schedulers are filtered
    PlaceRequest req = new PlaceRequest();
    PlaceParams placeParams = new PlaceParams();
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("foo"), hostName("bar")));
    manager.getPlacementSchedulers(req, placeParams);
    verify(randomStrategy).filterChildren(any(PlaceParams.class), captorSchedulers.capture(),
            Matchers.anySetOf(ResourceConstraint.class));

    assertThat(captorSchedulers.getValue().size(), is(1));
    ManagedScheduler nonMgmtSch = captorSchedulers.getValue().get(0);
    assertThat(nonMgmtSch.getId(), is(c2.getId()));
  }

  @Test
  public void testConfigureSameSchedulerId() throws IOException {
    // configure the scheduler
    ChildInfo c1 = new ChildInfo("foo", "foo", 1024);
    ChildInfo c2 = new ChildInfo("bar", "bar", 1024);
    c1.setOwner_host(hostName("foo"));
    c2.setOwner_host(hostName("bar"));
    ConfigureRequest configuration = getConfigureRequestFromChildren(c1, c2);
    manager.applyConfiguration(configuration);
    InetSocketAddress foo = InetSocketAddress.createUnresolved("foo", 1024);
    InetSocketAddress bar = InetSocketAddress.createUnresolved("bar", 1024);
    verify(schedulerFactory).create(eq("foo"), eq(foo), eq(hostName("foo")));
    verify(schedulerFactory).create(eq("bar"), eq(bar), eq(hostName("bar")));

    // configure it again with same child IDs but different address.

    ChildInfo c3 = new ChildInfo("foo", "foo2", 2024);
    ChildInfo c4 = new ChildInfo("bar", "bar2", 2024);
    c3.setOwner_host(hostName("foo"));
    c4.setOwner_host(hostName("bar"));
    configuration = getConfigureRequestFromChildren(c3, c4);
    manager.applyConfiguration(configuration);

    // verify that the addresses get updated.
    foo = InetSocketAddress.createUnresolved("foo2", 2024);
    bar = InetSocketAddress.createUnresolved("bar2", 2024);
    verify(schedulerFactory).create(eq("foo"), eq(foo), eq(hostName("foo")));
    verify(schedulerFactory).create(eq("bar"), eq(bar), eq(hostName("bar")));
  }

  @Test
  public void testNewConfiguration() throws IOException {
    ConfigureRequest oldConfiguration = getConfigureRequest("foo", "bar");

    {
      manager.applyConfiguration(oldConfiguration);
      verify(schedulerFactory).create(eq("foo"), any(InetSocketAddress.class), eq(hostName("foo")));
      verify(schedulerFactory).create(eq("bar"), any(InetSocketAddress.class), eq(hostName("bar")));
    }

    ConfigureRequest newConfiguration = getConfigureRequest("bar", "baz");

    {
      manager.applyConfiguration(newConfiguration);
      verify(schedulerFactory).create(eq("baz"), any(InetSocketAddress.class), eq(hostName("baz")));
    }

    assertThat(manager.getManagedSchedulersMap().values(), hasItems(bar, baz));

    verify(healthChecker, times(2)).stop();
    verify(healthChecker, times(2)).start();
  }

  /*
   * Test creation of managed schedulers. The list
   * of resources for the two schedulers is saved
   * in a global map and is used by the verification
   * routines.
   */
  @Test
  public void testApplyConfigurationWithResources() throws IOException {
    ConfigureRequest configuration = getConfigureRequestWithResources(1, "sched10", "sched11");

    manager.applyConfiguration(configuration);
    verify(schedulerFactory).create(eq("sched10"), any(InetSocketAddress.class), eq(hostName("sched10")));
    verify(schedulerFactory).create(eq("sched11"), any(InetSocketAddress.class), eq(hostName("sched11")));
    verify(healthChecker).start();

    assertThat(manager.getManagedSchedulersMap().values(), hasItems(sched10, sched11));

    Map<String, ManagedScheduler> managedSchedulers = manager.getManagedSchedulersMap();
    for (ManagedScheduler scheduler : managedSchedulers.values()) {
      verifyResources(scheduler.getId(), scheduler.getResources());
    }
  }

  /*
   * Test removal and update of managed schedulers.
   * Simulate two configuration operations, the first
   * one creates schedulers: sched10 and sched11
   * the second one adds sched12 but remove sched10.
   * Check that sched10 is removed and that the sched11
   * contains the new list of resources.
   */
  @Test
  public void testUpdateConfigurationWithResources() throws IOException {
    ConfigureRequest oldConfiguration = getConfigureRequestWithResources(1, "sched10", "sched11");

    {
      manager.applyConfiguration(oldConfiguration);
      verify(schedulerFactory).create(eq("sched10"), any(InetSocketAddress.class), eq(hostName("sched10")));
      verify(schedulerFactory).create(eq("sched11"), any(InetSocketAddress.class), eq(hostName("sched11")));

      assertThat(manager.getManagedSchedulersMap().values(), hasItems(sched10, sched11));

      Map<String, ManagedScheduler> managedSchedulers = manager.getManagedSchedulersMap();
      for (ManagedScheduler scheduler : managedSchedulers.values()) {
        verifyResources(scheduler.getId(), scheduler.getResources());
      }
    }

    ConfigureRequest newConfiguration = getConfigureRequestWithResources(2, "sched11", "sched12");

    {
      manager.applyConfiguration(newConfiguration);
      verify(schedulerFactory).create(eq("sched12"), any(InetSocketAddress.class), eq(hostName("sched12")));

      assertThat(manager.getManagedSchedulersMap().values().size(), is(2));
      assertThat(manager.getManagedSchedulersMap().values(), hasItems(sched11, sched12));

      Map<String, ManagedScheduler> managedSchedulers = manager.getManagedSchedulersMap();
      for (ManagedScheduler scheduler : managedSchedulers.values()) {
        verifyResources(scheduler.getId(), scheduler.getResources());
      }
    }

    verify(healthChecker, times(2)).stop();
    verify(healthChecker, times(2)).start();
  }

  @DataProvider(name = "useLocalPlaceParams")
  public Object[][] usePlaceParams() {
    return new Object[][]{
        new Object[]{false},
        new Object[]{true}
    };
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testSampleSchedulersForPlacement(boolean useLocalPlaceParams) throws Exception {
    for (int i = 0; i <= 6; i++) {
      ManagedScheduler scheduler = mock(ManagedScheduler.class);
      when(schedulerFactory.create(eq("s-" + i), any(InetSocketAddress.class), any(String.class))).
          thenReturn(scheduler);
      when(scheduler.getWeight()).thenReturn(8);
    }

    PlaceRequest placeRequest = new PlaceRequest();
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);

    manager.applyConfiguration(getConfigureRequest());
    // we short-circuit the configuration logic so that the health checker doesn't get
    // started if there is no child. Explicitly set the health checker here.
    manager.healthChecker = healthChecker;
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of());
    assertThat(manager.getPlacementSchedulers(placeRequest, placeParams).size(), is(0));

    manager.applyConfiguration(getConfigureRequest("s-0"));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("s-0")));
    assertThat(manager.getPlacementSchedulers(placeRequest, placeParams).size(), is(1));

    manager.applyConfiguration(getConfigureRequest("s-0", "s-1"));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("s-0"), hostName("s-1")));
    assertThat(manager.getPlacementSchedulers(placeRequest, placeParams).size(), is(2));

    manager.applyConfiguration(getConfigureRequest("s-0", "s-1", "s-2", "s-3", "s-4", "s-5"));
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("s-0"), hostName("s-1"), hostName("s-2"), hostName("s-3"),
                                               hostName("s-4"), hostName("s-5")));
    assertThat(manager.getPlacementSchedulers(placeRequest, placeParams).size(), is(2));

    manager.applyConfiguration(getConfigureRequest("s-0", "s-1", "s-2", "s-3", "s-4", "s-5", "s-6"));
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("s-0"), hostName("s-1"), hostName("s-2"), hostName("s-3"),
                                               hostName("s-4"), hostName("s-5"), hostName("s-6")));
    assertThat(manager.getPlacementSchedulers(placeRequest, placeParams).size(), is(2));
  }

  @Test
  public void testPlaceScore() throws InterruptedException, IOException {
    verifyPlaceScore(getPlaceResponse("foo", 51, 0), getPlaceResponse("bar", 50, 0));
    verifyPlaceScore(getPlaceResponse("foo", 51, 100), getPlaceResponse("bar", 50, 100));
    verifyPlaceScore(getPlaceResponse("foo", 50, 11), getPlaceResponse("bar", 51, 0));
    verifyPlaceScore(getPlaceResponse("foo", 51, 0), getPlaceResponse("bar", 50, 8));
    verifyPlaceScore(getPlaceResponse("foo", 50, 2), getPlaceResponse("bar", 51, 0), 1.0);
    verifyPlaceScore(getPlaceResponse("foo", 0, 1), getPlaceResponse("bar", 0, 0));
    verifyPlaceScore(getPlaceResponse("foo", 0, 51), getPlaceResponse("bar", 9, 50), 0.1);

  }

  private void verifyPlaceScore(PlaceResponse best, PlaceResponse other) throws IOException,
      InterruptedException {
    verifyPlaceScore(best, other, null);
  }

  private void verifyPlaceScore(PlaceResponse best, PlaceResponse other, Double ratio) throws IOException,
      InterruptedException {
    if (ratio != null) {
      config.getRoot().setUtilizationTransferRatio(ratio);
    }
    ConfigureRequest configuration = getConfigureRequest(best.getAgent_id(), other.getAgent_id());

    manager.applyConfiguration(configuration);

    PlaceRequest request = new PlaceRequest();
    when(foo.place(request, testTimeout)).thenReturn(Futures.immediateFuture(best));
    when(bar.place(request, testTimeout)).thenReturn(Futures.immediateFuture(other));
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName(best.getAgent_id()), hostName(other.getAgent_id())));
    PlaceResponse response = manager.place(request);
    assertThat(response.getAgent_id(), is(best.getAgent_id()));
  }

  @Test
  public void testPlaceError() throws InterruptedException, IOException {
    ConfigureRequest configuration = getConfigureRequest("foo");

    manager.applyConfiguration(configuration);

    PlaceRequest request = new PlaceRequest();
    when(foo.place(request, testTimeout)).
        thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.SYSTEM_ERROR)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("foo")));
    PlaceResponse response = manager.place(request);
    assertThat(response, is(notNullValue()));
    assertThat(response.getResult(), is(PlaceResultCode.SYSTEM_ERROR));
    assertThat(response.getError(), is("0 scheduler responded OK in 15000 ms out of 1 placement scheduler(s)"));
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceMissingScheduler(boolean useLocalPlaceParams) throws InterruptedException, IOException {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = new PlaceRequest();

    when(sched10.place(placeRequest, testTimeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 90, 90)));
    when(sched11.place(placeRequest, testTimeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 90, 90)));
    when(sched12.place(placeRequest, testTimeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));

    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    placeParams.setFanoutRatio(100.0);
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11")));
    manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10).place(placeRequest, testTimeout);
    verify(sched11).place(placeRequest, testTimeout);
    verify(sched12, never()).place(placeRequest, testTimeout);
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceResurrectedScheduler(boolean useLocalPlaceParams) throws InterruptedException, IOException {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = new PlaceRequest();

    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 90, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 90, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));

    placeParams.setFanoutRatio(100.0);
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11"), hostName("sched12")));
    manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10).place(placeRequest, timeout);
    verify(sched11).place(placeRequest, timeout);
    verify(sched12).place(placeRequest, timeout);
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceInvalidScheduler(boolean useLocalPlaceParams) throws InterruptedException, IOException {
    ConfigureRequest configuration = getConfigureRequest("foo");

    manager.applyConfiguration(configuration);

    PlaceRequest request = new PlaceRequest();
    PlaceParams placeParams = selectPlaceParams(request, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(foo.place(request, timeout)).thenReturn(
        Futures.immediateFuture(new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("foo")));
    PlaceResponse response = manager.place(request);
    assertThat(response, is(notNullValue()));
    assertThat(response.getResult(), is(PlaceResultCode.INVALID_SCHEDULER));
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceResourceConstraint(boolean useLocalPlaceParams) throws Exception {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar");

    manager.applyConfiguration(configuration);

    PlaceRequest request = new PlaceRequest();
    PlaceParams placeParams = selectPlaceParams(request, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(foo.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER)));
    when(bar.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("foo"), hostName("bar")));
    PlaceResponse response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.INVALID_SCHEDULER));

    when(foo.place(request, timeout))
        .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.INVALID_SCHEDULER)));
    when(bar.place(request, timeout))
        .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)));

    response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE));

    when(foo.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE)));
    when(bar.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)));

    response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NOT_ENOUGH_CPU_RESOURCE));

    when(foo.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE)));
    when(bar.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY)));

    response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NOT_ENOUGH_MEMORY_RESOURCE));

    when(foo.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY)));
    when(bar.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)));

    response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NOT_ENOUGH_DATASTORE_CAPACITY));

    when(foo.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)));
    when(bar.place(request, timeout))
            .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.NO_SUCH_RESOURCE)));

    response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
  }

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlacePartialResourceConstraint(boolean useLocalPlaceParams) throws Exception {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar");

    manager.applyConfiguration(configuration);

    PlaceRequest request = new PlaceRequest();
    PlaceParams placeParams = selectPlaceParams(request, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(foo.place(request, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("a", 40, 90)));

    when(bar.place(request, timeout))
        .thenReturn(Futures.immediateFuture(new PlaceResponse(PlaceResultCode.RESOURCE_CONSTRAINT)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of(hostName("foo"), hostName("bar")));
    PlaceResponse response = manager.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.OK));
    assertThat(response.getAgent_id(), is("a"));
  }

  /*
   * All children schedulers have different constraints. The request
   * is built including the constraints for sched10, therefore the
   * place request should only be propagated to sched10.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceVmWithResourceConstraints(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = createPlaceRequestWithVmConstraints("sched10");
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11"), hostName("sched12")));
    manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10).place(placeRequest, timeout);
    verify(sched11, never()).place(placeRequest, timeout);
    verify(sched12, never()).place(placeRequest, timeout);
  }

  /*
   * Two schedulers have the same set of resources (sched10 and sched11).
   * They should always be selected.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceVmWithOverlappingResourceConstraints(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithOverlappingResources(2);
    PlaceRequest placeRequest = createPlaceRequestWithVmConstraints("sched10");
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("invalid", 100, 90)));
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11"), hostName("sched12")));

    // Try 1000 times
    for (int i = 0; i < 1000; i++) {
      PlaceResponse response = manager.place(placeRequest);

      assertThat(response.getResult(), is(PlaceResultCode.OK));
      assertThat(response.getAgent_id(), startsWith("sched1"));
    }
  }

  /*
   * All children schedulers have different constraints. The request
   * is built including the constraints for sched10, therefore the
   * place request should only be propagated to sched10.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceVmWithResourceConstraintsFailure(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = createPlaceRequestWithVmConstraints("sched10");
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    configAndVerifySchedulersWithResources(2);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of("sched10", "sched11", "sched12"));
    PlaceResponse response = manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10, never()).place(placeRequest, timeout);
    verify(sched11, never()).place(placeRequest, timeout);
    verify(sched12, never()).place(placeRequest, timeout);

    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
  }

  /*
   * All children schedulers have different constraints. The request
   * is built including the constraints for sched10, therefore the
   * place request should only be propagated to sched10.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceDiskWithResourceConstraints(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = createPlaceRequestWithDiskConstraints("sched10");
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));
    when(healthChecker.getActiveSchedulers())
            .thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11"), hostName("sched12")));
    manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10).place(placeRequest, timeout);
    verify(sched11, never()).place(placeRequest, timeout);
    verify(sched12, never()).place(placeRequest, timeout);
  }

  /*
   * Two schedulers have the same set of resources (sched10 and sched11).
   * They should always be selected.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceDiskWithOverlappingResourceConstraints(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithOverlappingResources(2);
    PlaceRequest placeRequest = createPlaceRequestWithDiskConstraints("sched10");
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("invalid", 100, 90)));

    when(healthChecker.getActiveSchedulers())
            .thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11")));
    // Try 1000 times
    for (int i = 0; i < 1000; i++) {
      PlaceResponse response = manager.place(placeRequest);

      assertThat(response.getResult(), is(PlaceResultCode.OK));
      assertThat(response.getAgent_id(), startsWith("sched1"));
    }
  }

  /*
   * All children schedulers have different constraints. The request
   * is built including the constraints for sched10, therefore the
   * place request should only be propagated to sched10.
   */

  @Test(dataProvider = "useLocalPlaceParams")
  public void testPlaceDiskWithResourceConstraintsFailure(boolean useLocalPlaceParams) throws Exception {
    configAndVerifySchedulersWithResources(1);
    PlaceRequest placeRequest = createPlaceRequestWithDiskConstraints("sched10");
    configAndVerifySchedulersWithResources(2);
    PlaceParams placeParams = selectPlaceParams(placeRequest, useLocalPlaceParams);
    long timeout = placeParams.getTimeout();

    when(sched10.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched10", 100, 90)));
    when(sched11.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched11", 100, 90)));
    when(sched12.place(placeRequest, timeout))
        .thenReturn(Futures.immediateFuture(getPlaceResponse("sched12", 100, 90)));
    when(healthChecker.getActiveSchedulers()).thenReturn(ImmutableSet.<String>of("sched10", "sched11", "sched12"));
    PlaceResponse response = manager.place(placeRequest);

    verify(manager).getPlacementSchedulers(placeRequest, placeParams);
    verify(sched10, never()).place(placeRequest, timeout);
    verify(sched11, never()).place(placeRequest, timeout);
    verify(sched12, never()).place(placeRequest, timeout);

    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
  }

  @Test
  public void testFind() throws IOException, InterruptedException {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar", "baz");

    manager.applyConfiguration(configuration);

    FindRequest request = new FindRequest();
    when(foo.find(request, testTimeout)).thenReturn(Futures.immediateFuture(getFindOkResponse("a")));
    when(bar.find(request, testTimeout)).thenReturn(
        Futures.<FindResponse>immediateFailedFuture(new Exception("timed out")));
    when(baz.find(request, testTimeout)).thenReturn(Futures.immediateFuture(getFindNotFoundResponse()));

    FindResponse response = manager.find(request);
    assertThat(response.getResult(), is(FindResultCode.OK));
    assertThat(response.getAgent_id(), is("a"));
  }

  @Test
  public void testFindMultiple() throws IOException, InterruptedException {
    ConfigureRequest configuration = getConfigureRequest("foo", "bar", "baz");

    manager.applyConfiguration(configuration);

    FindRequest request = new FindRequest();
    when(foo.find(request, testTimeout)).thenReturn(Futures.immediateFuture(getFindOkResponse("a1")));
    when(bar.find(request, testTimeout)).thenReturn(
        Futures.<FindResponse>immediateFailedFuture(new Exception("timed out")));
    when(baz.find(request, testTimeout)).thenReturn(Futures.immediateFuture(getFindOkResponse("a2")));

    FindResponse response = manager.find(request);
    assertThat(response.getAgent_id(), isIn(ImmutableList.of("a1", "a2")));
  }

  @Test
  public void testGetActiveSchedulers() throws Exception {
    ConfigureRequest configuration = getConfigureRequestWithResources(1, "sched10", "sched11", "sched12");
    manager.applyConfiguration(configuration);
    when(healthChecker.getActiveSchedulers()).
            thenReturn(ImmutableSet.<String>of(hostName("sched10"), hostName("sched11")));
    Collection<ManagedScheduler> activeSchedulers = manager.getActiveSchedulers();
    assertThat(activeSchedulers, containsInAnyOrder(sched10, sched11));

    // Return all the schedulers if the health checker is not running.
    manager.healthChecker = null;
    activeSchedulers = manager.getActiveSchedulers();
    assertThat(activeSchedulers.size(), is(3));
    assertThat(sched10, isIn(activeSchedulers));
    assertThat(sched11, isIn(activeSchedulers));
    assertThat(sched12, isIn(activeSchedulers));
  }

  private ConfigureRequest getConfigureRequest(String... roles) {
    return getConfigureRequest(getSchedulerRole(roles));
  }

  private ConfigureRequest getConfigureRequestFromChildren(ChildInfo... children) {
    SchedulerRole role = new SchedulerRole();
    for (ChildInfo child : children) {
      role.addToScheduler_children(child);
    }
    return getConfigureRequest(role);
  }

  private ConfigureRequest getConfigureRequest(SchedulerRole schRole) {
    ConfigureRequest result = new ConfigureRequest();
    Roles role = new Roles();
    result.setScheduler("parent1");
    role.addToSchedulers(schRole);
    result.setRoles(role);
    return result;
  }

  private SchedulerRole getSchedulerRole(String... roles) {
    SchedulerRole result = new SchedulerRole();
    for (String role : roles) {
      ChildInfo c1 = new ChildInfo(role, "localhost", 1024);
      c1.setOwner_host(hostName(role));
      result.addToScheduler_children(c1);
    }
    return result;
  }

  private PlaceResponse getPlaceResponse(String agentId, int utilization, int transfer) {
    PlaceResponse result = new PlaceResponse();
    result.setResult(PlaceResultCode.OK);
    result.setAgent_id(agentId);
    result.setScore(new Score(utilization, transfer));
    return result;
  }

  private FindResponse getFindOkResponse(String agentId) {
    FindResponse result = new FindResponse();
    result.setResult(FindResultCode.OK);
    result.setAgent_id(agentId);
    return result;
  }

  private FindResponse getFindNotFoundResponse() {
    FindResponse result = new FindResponse();
    result.setResult(FindResultCode.NOT_FOUND);
    return result;
  }

  private PlaceRequest createPlaceRequestWithVmConstraints(String schedName) {
    PlaceRequest placeRequest = new PlaceRequest();
    Resource resource = new Resource();
    Vm vm = new Vm();
    List<ResourceConstraint> sched10Constraints = mapOfResources.get(schedName);
    vm.setResource_constraints(sched10Constraints);
    resource.setVm(vm);
    placeRequest.setResource(resource);
    return placeRequest;
  }

  private PlaceRequest createPlaceRequestWithDiskConstraints(String schedName) {
    PlaceRequest placeRequest = new PlaceRequest();
    Resource resource = new Resource();
    List<Disk> diskList = new ArrayList<>();
    Disk disk = new Disk();
    List<ResourceConstraint> sched10Constraints = mapOfResources.get(schedName);
    List<ResourceConstraint> diskConstraints = new ArrayList<>();
    for (ResourceConstraint constraint : sched10Constraints) {
      if (constraint.getType() == ResourceConstraintType.DATASTORE) {
        diskConstraints.add(constraint);
      }
    }
    disk.setResource_constraints(diskConstraints);
    diskList.add(disk);
    resource.setDisks(diskList);
    placeRequest.setResource(resource);
    return placeRequest;
  }

  private void configAndVerifySchedulersWithResources(int generation) throws IOException {
    ConfigureRequest configuration =
        getConfigureRequestWithResources(generation, "sched10", "sched11", "sched12");

    manager.applyConfiguration(configuration);

    assertThat(manager.getManagedSchedulersMap().values(), hasItems(sched10, sched11, sched12));

    Map<String, ManagedScheduler> managedSchedulers = manager.getManagedSchedulersMap();
    for (ManagedScheduler scheduler : managedSchedulers.values()) {
      verifyResources(scheduler.getId(), scheduler.getResources());
    }
  }

  private void configAndVerifySchedulersWithOverlappingResources(int overlapCount) throws IOException {
    ConfigureRequest configuration =
        getConfigureRequestWithOverlappingResources(overlapCount, "sched10", "sched11", "sched12");

    manager.applyConfiguration(configuration);
    verify(schedulerFactory).create(eq("sched10"), any(InetSocketAddress.class), eq(hostName("sched10")));
    verify(schedulerFactory).create(eq("sched11"), any(InetSocketAddress.class), eq(hostName("sched11")));
    verify(schedulerFactory).create(eq("sched12"), any(InetSocketAddress.class), eq(hostName("sched12")));

    assertThat(manager.getManagedSchedulersMap().values(), hasItems(sched10, sched11, sched12));

    Map<String, ManagedScheduler> managedSchedulers = manager.getManagedSchedulersMap();
    for (ManagedScheduler scheduler : managedSchedulers.values()) {
      verifyResources(scheduler.getId(), scheduler.getResources());
    }
  }

  private ConfigureRequest getConfigureRequestWithResources(int generation, String... roles) {
    return getConfigureRequest(
        getSchedulerRoleWithResources(generation, roles));
  }

  /*
   * Create resources for a set of schedulers. All schedulers have different resources.
   * A new set of resources can be generated changing the generation parameter.
   */
  private SchedulerRole getSchedulerRoleWithResources(int generation, String... roles) {
    SchedulerRole result = new SchedulerRole();
    for (String role : roles) {
      // Create 4 resources
      List<ResourceConstraint> resources = new ArrayList<>();
      resources.add(new ResourceConstraint(ResourceConstraintType.DATASTORE,
          ImmutableList.of(role + "_ds0_" + generation)));
      resources.add(new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG,
          ImmutableList.of(role + "_dt0_" + generation)));
      resources.add(new ResourceConstraint(ResourceConstraintType.NETWORK,
          ImmutableList.of(role + "_nk0_" + generation)));
      resources.add(new ResourceConstraint(ResourceConstraintType.HOST,
          ImmutableList.of(role + "_ht0_" + generation)));
      ChildInfo childInfo = new ChildInfo(role, "localhost", 1024);
      childInfo.setConstraints(resources);
      childInfo.setOwner_host(hostName(role));
      mapOfResources.put(role, resources);
      result.addToScheduler_children(childInfo);
    }
    return result;
  }

  private ConfigureRequest getConfigureRequestWithOverlappingResources(int overlapCounter, String... roles) {
    return getConfigureRequest(
        getSchedulerRoleWithOverlappingResources(overlapCounter, roles));
  }

  /*
   * Create resources for a set of schedulers. The first "overlapCounter" have the same resources.
   */
  private SchedulerRole getSchedulerRoleWithOverlappingResources(int overlapCounter, String... roles) {
    SchedulerRole result = new SchedulerRole();
    int index = 0;
    String prefix = "";
    for (String role : roles) {
      if (index++ == overlapCounter) {
        prefix = role;
      }
      // Create 3 resources
      List<ResourceConstraint> resources = new ArrayList<>();
      resources.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of(prefix + "ds0")));
      resources.add(new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, ImmutableList.of(prefix + "dt0")));
      resources.add(new ResourceConstraint(ResourceConstraintType.NETWORK, ImmutableList.of(prefix + "nk0")));
      resources.add(new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(prefix + "hs0")));
      ChildInfo childInfo = new ChildInfo(role, "localhost", 1024);
      childInfo.setConstraints(resources);
      childInfo.setOwner_host(hostPrefix + role);
      mapOfResources.put(role, resources);
      result.addToScheduler_children(childInfo);
    }
    return result;
  }

  private void verifyResources(String id, Set<ResourceConstraint> resources) {
    // Four resources added by the getSchedulerRolWithResources() method
    assertThat(resources.size(), is(4));
    List<ResourceConstraint> resourceList = mapOfResources.get(id);

    for (ResourceConstraint resource : resources) {
      // ResourceConstraint overrides equal
      assertThat(resourceList.contains(resource), is(true));
    }
  }

  /* Placement tests should work with either
   * the placeParams provided in the Request
   * or the parameters created from the Config
   * object. This method return a reference to
   * the selected place parameters object, this
   * is necessary for verification. In addition
   * when the placeParam object is part of the
   * request this method invalidates the one created
   * from the config object. This is done to make
   * sure that the underlying methods are
   * using the placeParam object from the request
   * and not the one from Config.
   */
  private PlaceParams selectPlaceParams(PlaceRequest request, boolean usePlaceParams) {
    if (usePlaceParams == false) {
      return config.getRootPlaceParams();
    }
    PlaceParams placeParams = new PlaceParams(config.getRootPlaceParams());
    invalidateConfig();
    request.setRootSchedulerParams(placeParams);
    return placeParams;
  }

  private void invalidateConfig() {
    SchedulerConfig root = config.getRoot();
    root.setPlaceTimeoutMs(0);
    root.setFanoutRatio(0);
    root.setMinFanoutCount(0);
    root.setMaxFanoutCount(0);
    root.setFastPlaceResponseRatio(0);
    root.setFastPlaceResponseTimeoutRatio(0);
    root.setFastPlaceResponseMinCount(0);
    config.initRootPlaceParams();
  }

}
