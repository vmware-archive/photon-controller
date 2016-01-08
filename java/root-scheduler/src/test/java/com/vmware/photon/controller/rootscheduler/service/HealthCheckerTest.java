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

import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.chairman.gen.ReportMissingRequest;
import com.vmware.photon.controller.chairman.gen.ReportMissingResponse;
import com.vmware.photon.controller.chairman.gen.ReportMissingResultCode;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedRequest;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedResponse;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedResultCode;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSetFactory;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.HealthCheckConfig;
import com.vmware.photon.controller.rootscheduler.HeartbeatServerSetFactory;
import com.vmware.photon.controller.scheduler.gen.Scheduler;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests {@link HealthChecker}.
 */
public class HealthCheckerTest extends PowerMockTestCase {

  @Mock
  private StaticServerSetFactory staticServerSetFactory;

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
  private ClientProxy<Chairman.AsyncClient> chairmanProxy;

  @Mock
  private Chairman.AsyncClient chairman;

  @Mock
  private HeartbeatServerSetFactory serverSetFactory;

  @Mock
  private ScheduledExecutorService executorService;

  private HealthCheckConfig config;

  @BeforeMethod
  public void setUp() {
    config = new HealthCheckConfig();
    config.setTimeoutMs(0);
    when(chairmanProxy.get()).thenReturn(chairman);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testHostsNotAllowed() throws Exception {
    SchedulerRole role = new SchedulerRole();
    role.setId("foo");
    role.addToSchedulers("x");
    role.addToHosts("A");

    getHealthChecker(role);
  }

  @Test
  public void testReportResurrected() throws Exception {
    ArgumentCaptor<ReportResurrectedRequest> argReq = ArgumentCaptor.forClass(ReportResurrectedRequest.class);
    HealthChecker hc = getHealthChecker(new SchedulerRole("root"));
    Set<String> resurrected = hc.getResurrectedSchedulers();
    resurrected.add("h1");
    resurrected.add("h2");
    hc.reportResurrectedChildren();

    verify(chairman).report_resurrected(argReq.capture(), any(HealthChecker.ReportResurrectedResponseHandler.class));

    ReportResurrectedRequest req = argReq.getValue();

    // Verify that the scheduler host id is reported, not the scheduler id
    assertThat(req.getSchedulers().size(), is(2));
    assertThat(req.getSchedulers().contains("h1"), is(true));
    assertThat(req.getSchedulers().contains("h2"), is(true));
  }

  @Test
  public void testReportMissing() throws Exception {
    ArgumentCaptor<ReportMissingRequest> argReq = ArgumentCaptor.forClass(ReportMissingRequest.class);
    HealthChecker hc = getHealthChecker(new SchedulerRole("root"));

    Map<String, Long> schedulerUpdates = hc.getSchedulerUpdates();
    schedulerUpdates.put("h1", new Long(0));
    hc.reportMissingChildren();

    verify(chairman).report_missing(argReq.capture(), any(HealthChecker.ReportMissingResponseHandler.class));

    ReportMissingRequest req = argReq.getValue();

    // Verify that the scheduler host id is reported, not the scheduler id
    assertThat(req.getSchedulers().size(), is(1));
    assertThat(req.getSchedulers().contains("h1"), is(true));
  }

  @Test
  public void testNoEmptyReports() throws Exception {
    HealthChecker hc = getHealthChecker(new SchedulerRole());

    when(serverSetFactory.create(anyString(), anyList(), anyInt())).thenReturn(new StaticServerSet());

    hc.start();
    hc.reportMissingChildren();

    Mockito.verifyNoMoreInteractions(chairmanProxy);
  }

  @Test
  public void testNoReportsAfterStop() throws Exception {
    SchedulerRole role = new SchedulerRole();
    role.addToSchedulers("x");

    HealthChecker hc = getHealthChecker(role);

    when(serverSetFactory.create(anyString(), anyList(), anyInt())).thenReturn(new StaticServerSet());

    hc.start();
    hc.stop();
    hc.reportMissingChildren();

    verifyNoMoreInteractions(chairmanProxy);
  }

  private ManagedScheduler createManagedScheduler(String schId, String hostId) {
    InetSocketAddress address = new InetSocketAddress("host", 1234);
    serverSet = new StaticServerSet(address);
    when(staticServerSetFactory.create(address)).thenReturn(serverSet);
    when(clientPoolFactory.create(eq(serverSet), any(ClientPoolOptions.class))).thenReturn(clientPool);
    when(clientProxyFactory.create(clientPool)).thenReturn(clientProxy);

    return new ManagedScheduler(schId, address, hostId, new Config(),
        staticServerSetFactory, clientPoolFactory, clientProxyFactory);
  }

  private HealthChecker getHealthChecker(SchedulerRole role) {
    return new HealthChecker(role, chairmanProxy, executorService, config,
        serverSetFactory);
  }

  /**
   * Test that root scheduler continues to report missing children until chairman acknowledges.
   */
  @Test
  public void testReportMissingFailure() throws Exception {
    ArgumentCaptor<HealthChecker.ReportMissingResponseHandler> responseHandler =
        ArgumentCaptor.forClass(HealthChecker.ReportMissingResponseHandler.class);

    HealthChecker hc = getHealthChecker(new SchedulerRole("root"));
    hc.getSchedulerUpdates().put("sch1", new Long(0));

    // Root scheduler continues to report the missing child if chairman returns
    // a non-zero response code.
    doAnswer(new ReportMissingHandler(responseHandler, ReportMissingResultCode.SYSTEM_ERROR)).
        when(chairman).report_missing(any(ReportMissingRequest.class), responseHandler.capture());
    int i = 0;
    for (; i < 10; i++) {
      hc.reportMissingChildren();
      verify(chairman, times(i + 1)).report_missing(any(ReportMissingRequest.class),
          any(HealthChecker.ReportMissingResponseHandler.class));
      assertThat(hc.getMissingSchedulers().size(), is(0));
    }

    // Make sure the root scheduler stops sending requests to chairman once
    // the reporting succeeds.
    doAnswer(new ReportMissingHandler(responseHandler, ReportMissingResultCode.OK)).
        when(chairman).report_missing(any(ReportMissingRequest.class), responseHandler.capture());
    hc.reportMissingChildren();
    verify(chairman, times(i + 1)).report_missing(any(ReportMissingRequest.class),
        any(HealthChecker.ReportMissingResponseHandler.class));
    assertThat(hc.getMissingSchedulers().size(), is(1));
    hc.reportMissingChildren();
    Mockito.verifyNoMoreInteractions(chairman);
  }

  /**
   * Test that root scheduler continues to report resurrected children until chairman acknowledges.
   */
  @Test
  public void testReportResurrectedFailure() throws Exception {
    ArgumentCaptor<HealthChecker.ReportResurrectedResponseHandler> responseHandler =
        ArgumentCaptor.forClass(HealthChecker.ReportResurrectedResponseHandler.class);

    HealthChecker hc = getHealthChecker(new SchedulerRole("root"));
    hc.getResurrectedSchedulers().add("sch1");

    // Root scheduler continues to report the resurrected child if chairman returns
    // a non-zero response code.
    doAnswer(new ReportResurrectedHandler(responseHandler, ReportResurrectedResultCode.SYSTEM_ERROR)).
        when(chairman).report_resurrected(any(ReportResurrectedRequest.class), responseHandler.capture());
    int i = 0;
    for (; i < 10; i++) {
      hc.reportResurrectedChildren();
      verify(chairman, times(i + 1)).report_resurrected(any(ReportResurrectedRequest.class),
          any(HealthChecker.ReportMissingResponseHandler.class));
      assertThat(hc.getResurrectedSchedulers().size(), is(1));
    }

    // Make sure the root scheduler stops sending requests to chairman once
    // the reporting succeeds.
    doAnswer(new ReportResurrectedHandler(responseHandler, ReportResurrectedResultCode.OK)).
        when(chairman).report_resurrected(any(ReportResurrectedRequest.class), responseHandler.capture());
    hc.reportResurrectedChildren();
    verify(chairman, times(i + 1)).report_resurrected(any(ReportResurrectedRequest.class),
        any(HealthChecker.ReportResurrectedResponseHandler.class));
    assertThat(hc.getResurrectedSchedulers().size(), is(0));
    hc.reportResurrectedChildren();
    Mockito.verifyNoMoreInteractions(chairman);
  }

  /**
   *  Verify that the active scheduler set gets updated on server set notifications.
   */
  @Test
  public void testActiveSchedulers() {
    SchedulerRole role = new SchedulerRole("root");
    for (int i = 0; i < 10; i++) {
      ChildInfo child = new ChildInfo("sch" + i, "localhost" + i, 1000 + i);
      child.setOwner_host("host" + i);
      role.addToScheduler_children(child);
    }
    when(serverSetFactory.create(anyString(), anyList(), anyInt())).thenReturn(new StaticServerSet());
    HealthChecker hc = getHealthChecker(role);
    hc.start();
    assertThat(hc.getActiveSchedulers().isEmpty(), is(true));
    hc.listener.onServerAdded(InetSocketAddress.createUnresolved("localhost0", 1000));
    assertThat(hc.getActiveSchedulers(), containsInAnyOrder("host0"));
    hc.listener.onServerAdded(InetSocketAddress.createUnresolved("localhost1", 1001));
    assertThat(hc.getActiveSchedulers(), containsInAnyOrder("host0", "host1"));
    hc.listener.onServerRemoved(InetSocketAddress.createUnresolved("localhost1", 1001));
    assertThat(hc.getActiveSchedulers(), containsInAnyOrder("host0"));
    hc.listener.onServerRemoved(InetSocketAddress.createUnresolved("localhost0", 1000));
    assertThat(hc.getActiveSchedulers().isEmpty(), is(true));
  }

    /**
     * A mock report missing request handler to specify return code.
     */
  class ReportMissingHandler implements Answer {
    private final ArgumentCaptor<HealthChecker.ReportMissingResponseHandler> responseHandler;
    private final ReportMissingResultCode responseCode;

    public ReportMissingHandler(
        ArgumentCaptor<HealthChecker.ReportMissingResponseHandler> responseHandler,
        ReportMissingResultCode responseCode) {
      this.responseHandler = responseHandler;
      this.responseCode = responseCode;
    }

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
      Chairman.AsyncClient.report_missing_call response = mock(Chairman.AsyncClient.report_missing_call.class);
      when(response.getResult()).thenReturn(new ReportMissingResponse(responseCode));
      responseHandler.getValue().onComplete(response);
      return null;
    }
  }

  /**
   * A mock report resurrected request handler to specify return code.
   */
  class ReportResurrectedHandler implements Answer {
    private final ArgumentCaptor<HealthChecker.ReportResurrectedResponseHandler> responseHandler;
    private final ReportResurrectedResultCode responseCode;

    public ReportResurrectedHandler(
        ArgumentCaptor<HealthChecker.ReportResurrectedResponseHandler> responseHandler,
        ReportResurrectedResultCode responseCode) {
      this.responseHandler = responseHandler;
      this.responseCode = responseCode;
    }

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
      Chairman.AsyncClient.report_resurrected_call response = mock(Chairman.AsyncClient.report_resurrected_call.class);
      when(response.getResult()).thenReturn(new ReportResurrectedResponse(responseCode));
      responseHandler.getValue().onComplete(response);
      return null;
    }
  }
}
