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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceResultCode;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;

import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Test cases for FlatSchedulerService.
 */

public class FlatSchedulerServiceTest {

  @Mock
  private Config config;

  @Mock
  private HostClientFactory hostClientFactory;

  @Mock
  private ConstraintChecker checker;

  @Mock
  private DcpRestClient dcpRestClient;

  @BeforeTest
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    PlaceParams placeParams = new PlaceParams();
    placeParams.setMaxFanoutCount(4);
    placeParams.setTimeout(20000);
    doReturn(placeParams).when(config).getRootPlaceParams();
  }

  /**
   * No constraint match.
   */
  @DataProvider(name = "empty")
  public Object[][] createEmpty() {
      doReturn(ImmutableMap.of()).when(checker)
          .getCandidates(anyListOf(ResourceConstraint.class), anyInt());
      return new Object[][]{
        {new FlatSchedulerService(config, hostClientFactory, checker, dcpRestClient)},
    };
  }

  /**
   * No constraint match.
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
        {new FlatSchedulerService(config, hostClientFactory, checker, dcpRestClient)},
    };
  }

  @Test(dataProvider = "empty")
  public void testNoCandidate(RootScheduler.Iface scheduler) throws Exception {
    PlaceRequest request = new PlaceRequest();
    Resource resource = new Resource();
    request.setResource(resource);
    PlaceResponse response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.NO_SUCH_RESOURCE));
  }

  @Test(dataProvider = "four-candidates")
  public void testNoResponse(RootScheduler.Iface scheduler) throws Exception {
    HostClient client = mock(HostClient.class);
    doReturn(client).when(hostClientFactory).create();
    doAnswer((InvocationOnMock invocation) -> {
      Object[] arguments = invocation.getArguments();
      AsyncMethodCallback<Host.AsyncClient.place_call> call =
          (AsyncMethodCallback<Host.AsyncClient.place_call>) arguments[1];
      call.onError(new Exception());
      return null;
    }).when(client).place(any(), any(FlatSchedulerService.PlaceCallback.class));

    PlaceRequest request = new PlaceRequest();
    PlaceResponse response = scheduler.place(request);
    assertThat(response.getResult(), is(PlaceResultCode.SYSTEM_ERROR));
  }
}
