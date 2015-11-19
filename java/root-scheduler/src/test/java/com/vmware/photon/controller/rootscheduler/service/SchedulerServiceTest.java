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

import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.SchedulerConfig;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.gen.Scheduler;
import com.vmware.photon.controller.scheduler.gen.Score;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;

import com.google.common.collect.ImmutableMap;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.BeforeMethod;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Test cases for SchedulerService.
 */
public class SchedulerServiceTest {
  Random random = new Random();

  @Mock
  private Config config;

  @Mock
  private StaticServerSetFactory staticServerSetFactory;

  @Mock
  private ClientPool<Scheduler.AsyncClient> clientPool;

  @Mock
  private ClientPoolFactory<Scheduler.AsyncClient> clientPoolFactory;

  @Mock
  private ClientProxy<Scheduler.AsyncClient> clientProxy;

  @Mock
  private ClientProxyFactory<Scheduler.AsyncClient> clientProxyFactory;

  @Mock
  private Scheduler.AsyncClient client;

  @Mock
  private ConstraintChecker checker;

  @Mock
  private DcpRestClient dcpRestClient;

  private ScoreCalculator scoreCalculator;

  @BeforeMethod
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
    scoreCalculator = new ScoreCalculator(config);
    doReturn(clientPool).when(clientPoolFactory).create(any(ServerSet.class), any(ClientPoolOptions.class));
    doReturn(clientProxy).when(clientProxyFactory).create(any());
    doReturn(client).when(clientProxy).get();
  }

  /**
   * No constraint match.
   */
  @DataProvider(name = "empty")
  public Object[][] createEmpty() {
      doReturn(ImmutableMap.of()).when(checker)
          .getCandidates(anyListOf(ResourceConstraint.class), anyInt());
      return new Object[][]{
        {new SchedulerService(config, clientPoolFactory, clientProxyFactory, checker,
            dcpRestClient, scoreCalculator, staticServerSetFactory)},
    };
  }

  /**
   * Test the case where there is no candidate that match all the constraints.
   */
  @Test(dataProvider = "empty")
  public void testNoCandidate(RootScheduler.Iface scheduler) throws Exception {
    PlaceRequest request = new PlaceRequest();
    Resource resource = new Resource();
    request.setResource(resource);
    PlaceResponse response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
  }

  /**
   * Four candidates.
   */
  @DataProvider(name = "four-candidates")
  public Object[][] createFourCandidates() {
    ImmutableMap<String, ServerAddress> matches = ImmutableMap.of(
        "h1", new ServerAddress("h1", 1234),
        "h2", new ServerAddress("h2", 1234),
        "h3", new ServerAddress("h3", 1234),
        "h4", new ServerAddress("h4", 1234));

    doReturn(matches).when(checker)
        .getCandidates(anyListOf(ResourceConstraint.class), anyInt());
    return new Object[][]{
        {new SchedulerService(config, clientPoolFactory, clientProxyFactory, checker,
            dcpRestClient, scoreCalculator, staticServerSetFactory)},
    };
  }

  /**
   * Test the case where the scheduler fails to sample any host.
   */
  @Test(dataProvider = "four-candidates")
  public void testNoResponse(RootScheduler.Iface scheduler) throws Exception {
    doAnswer((InvocationOnMock invocation) -> {
      Object[] arguments = invocation.getArguments();
      AsyncMethodCallback<Scheduler.AsyncClient.host_place_call> call =
          (AsyncMethodCallback<Scheduler.AsyncClient.host_place_call>) arguments[1];
      call.onError(new Exception());
      return null;
    }).when(client).host_place(any(), any());

    PlaceRequest request = new PlaceRequest();
    PlaceResponse response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.SYSTEM_ERROR));
    verifyNoMoreInteractions(client);
    verifyNoMoreInteractions(clientPool);
  }

  /**
   * Test the case where all the hosts respond successfully.
   */
  @Test(dataProvider = "four-candidates")
  public void testSuccess(RootScheduler.Iface scheduler) throws Exception {
    Set<PlaceResponse> responses = new HashSet<>();
    doAnswer((InvocationOnMock invocation) -> {
      Object[] arguments = invocation.getArguments();
      AsyncMethodCallback<Scheduler.AsyncClient.host_place_call> call =
          (AsyncMethodCallback<Scheduler.AsyncClient.host_place_call>) arguments[1];
      PlaceResponse response = new PlaceResponse(PlaceResultCode.OK);
      response.setScore(new Score(random.nextInt(), random.nextInt()));
      responses.add(response);
      Scheduler.AsyncClient.host_place_call placeResponse = mock(Scheduler.AsyncClient.host_place_call.class);
      doReturn(response).when(placeResponse).getResult();
      call.onComplete(placeResponse);
      return null;
    }).when(client).host_place(any(), any());

    PlaceRequest request = new PlaceRequest();
    PlaceResponse response = scheduler.place(request);
    assertThat(response, is(scoreCalculator.pickBestResponse(responses)));
    verify(client, times(4)).host_place(any(), any());
    verify(clientPool, times(4)).close();
  }

  /**
   * Test the case where two out of four candidates respond successfully.
   */
  @Test(dataProvider = "four-candidates")
  public void testPartialSuccess(RootScheduler.Iface scheduler) throws Exception {
    int numResponses = 2;
    Set<PlaceResponse> responses = new HashSet<>();
    doAnswer((InvocationOnMock invocation) -> {
      Object[] arguments = invocation.getArguments();
      AsyncMethodCallback<Scheduler.AsyncClient.host_place_call> call =
          (AsyncMethodCallback<Scheduler.AsyncClient.host_place_call>) arguments[1];
      if (responses.size() < numResponses) {
        PlaceResponse response = new PlaceResponse(PlaceResultCode.OK);
        response.setScore(new Score(random.nextInt(), random.nextInt()));
        responses.add(response);
        Scheduler.AsyncClient.host_place_call placeResponse = mock(Scheduler.AsyncClient.host_place_call.class);
        doReturn(response).when(placeResponse).getResult();
        call.onComplete(placeResponse);
      } else {
        call.onError(new Exception());
      }
      return null;
    }).when(client).host_place(any(), any());

    PlaceRequest request = new PlaceRequest();
    PlaceResponse response = scheduler.place(request);
    assertThat(response, is(scoreCalculator.pickBestResponse(responses)));
    verify(client, times(4)).host_place(any(), any());
    verify(clientPool, times(4)).close();
  }
}
