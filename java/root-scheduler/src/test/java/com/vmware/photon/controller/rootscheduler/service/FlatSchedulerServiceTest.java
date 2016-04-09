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

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.photon.controller.rootscheduler.xenon.task.PlacementTask;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Score;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import com.google.common.collect.ImmutableMap;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Test cases for FlatSchedulerService.
 */
public class FlatSchedulerServiceTest {
  Random random = new Random();

  @Mock
  private Config config;

  @Mock
  private HostClient client;

  @Mock
  private ConstraintChecker checker;

  @Mock
  private HostClientFactory hostClientFactory;

  private SchedulerXenonHost schedulerXenonHost;

  @BeforeTest
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    PlaceParams rootPlaceParams = new PlaceParams();
    rootPlaceParams.setMaxFanoutCount(4);
    rootPlaceParams.setTimeout(20000);
    SchedulerConfig schedulerConfig = new SchedulerConfig();
    schedulerConfig.setUtilizationTransferRatio(0.5);
    config.initRootPlaceParams();
    doReturn(schedulerConfig).when(config).getRoot();
    doReturn(rootPlaceParams).when(config).getRootPlaceParams();
    when(hostClientFactory.create()).thenReturn(client);
    schedulerXenonHost = mock(SchedulerXenonHost.class);
    when(schedulerXenonHost.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
  }

  /**
   * No constraint match.
   */
  @DataProvider(name = "empty")
  public Object[][] createEmpty() {
    doReturn(ImmutableMap.of()).when(checker)
        .getCandidates(anyListOf(ResourceConstraint.class), anyInt());
    return new Object[][]{
        {new FlatSchedulerService(config, schedulerXenonHost)},
    };
  }

  /**
   * Test the case where there is no candidate that match all the constraints.
   */
  @Test(dataProvider = "empty")
  public void testNoCandidate(RootScheduler.Iface scheduler) throws Exception {
    final PlacementTask serviceDocument = new PlacementTask();
    serviceDocument.resultCode = PlaceResultCode.NO_SUCH_RESOURCE;
    serviceDocument.generation = 0;
    serviceDocument.error = "";
    serviceDocument.serverAddress = new ServerAddress("host", 0);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Operation op = (Operation) invocation.getArguments()[0];
        op.setBody(serviceDocument);
        op.complete();
        return null;
      }
    }).when(schedulerXenonHost).sendRequest(any(Operation.class));

    PlaceRequest request = new PlaceRequest();
    Resource resource = new Resource();
    request.setResource(resource);
    PlaceResponse response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
    verifyNoMoreInteractions(client);
  }

  /**
   * Four candidates.
   */
  @DataProvider(name = "four-candidates")
  public Object[][] createFourCandidates() {
    return new Object[][]{
        {new FlatSchedulerService(config, schedulerXenonHost)},
    };
  }

  /**
   * Test the case where all the hosts respond successfully.
   */
  @Test(dataProvider = "four-candidates")
  public void testSuccess(RootScheduler.Iface scheduler) throws Exception {
    Set<PlaceResponse> responses = new HashSet<>();
    PlaceResponse response = new PlaceResponse(PlaceResultCode.OK);
    response.setScore(new Score(random.nextInt(), random.nextInt()));
    responses.add(response);

    final PlacementTask serviceDocument = new PlacementTask();
    serviceDocument.resultCode = PlaceResultCode.OK;
    serviceDocument.generation = 0;
    serviceDocument.error = "";
    serviceDocument.serverAddress = new ServerAddress("host", 0);

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Operation op = (Operation) invocation.getArguments()[0];
        op.setBody(serviceDocument);
        op.complete();
        return null;
      }
    }).when(schedulerXenonHost).sendRequest(any(Operation.class));

    PlaceRequest request = new PlaceRequest();
    response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.OK));
  }
}
