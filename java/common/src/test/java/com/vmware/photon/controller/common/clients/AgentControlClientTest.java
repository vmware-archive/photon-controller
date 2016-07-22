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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.agent.gen.PingRequest;
import com.vmware.photon.controller.agent.gen.ProvisionRequest;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResultCode;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentConfigurationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentStateException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ModuleFactory;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.stats.plugin.gen.StatsPluginConfig;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Test {@link AgentControlClient}.
 */
public class AgentControlClientTest {

  private AgentControlClient agentControlClient;
  private AgentControl.AsyncClient clientProxy;

  private void setUp() {
    agentControlClient = spy(new AgentControlClient(
        mock(ClientProxyFactory.class), mock(ClientPoolFactory.class)));
    clientProxy = mock(AgentControl.AsyncClient.class);
  }

  private void setUpWithGuiceInjection() {
    Injector injector = Guice.createInjector(
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<AgentControl.AsyncClient>() {
            }
        ),
        new ModuleFactory.TracingTestModule());

    agentControlClient = injector.getInstance(AgentControlClient.class);
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for method {@link AgentControlClient#ensureClient() ensureClient}.
   */
  public class EnsureClientTest {

    @BeforeMethod
    private void setUp() {
      setUpWithGuiceInjection();
    }

    @AfterMethod
    private void tearDown() {
      agentControlClient = null;
    }

    @Test
    public void testNoLeak() throws Throwable {
      for (int i = 0; i < 10000; i++) {
        agentControlClient.setIpAndPort("127.0.0.1", 2181 + i);
        assertThat(agentControlClient.getClientProxy(), nullValue());
        agentControlClient.ensureClient();
        assertThat(agentControlClient.getClientProxy(), notNullValue());
      }
    }

    @Test
    public void testSetClientProxyWithIpAndPort() throws Throwable {
      agentControlClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(agentControlClient.getClientProxy(), nullValue());
      agentControlClient.ensureClient();
      assertThat(agentControlClient.getClientProxy(), notNullValue());
      assertThat(agentControlClient.getHostIp(), is("127.0.0.1"));
      assertThat(agentControlClient.getPort(), is(2181));
    }

    @Test
    public void testErrorWithNothingSet() throws Throwable {
      try {
        agentControlClient.ensureClient();
        fail("ensureClient should fail with nothing set");
      } catch (Exception e) {
        assertThat(e.getClass() == IllegalArgumentException.class, is(true));
        assertThat(e.getMessage(), is("hostname can't be null"));
      }
    }
  }

  /**
   * This class implements tests for the methods which set IP and port.
   */
  public class SetIpTest {

    @BeforeMethod
    private void setUp() {
      AgentControlClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      agentControlClient = null;
    }

    @Test
    public void testSetIpWithNullIp() throws Throwable {
      try {
        agentControlClient.setHostIp(null);
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("IP can not be null"));
      }
    }

    @Test
    public void testSetIpAndPortWithInvalidPort() throws Throwable {
      try {
        agentControlClient.setIpAndPort("127.0.0.1", 0);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Please set port above 1023"));
      }
    }

    @Test
    public void testSetIpAndPortWithValidIpPort() throws Throwable {
      agentControlClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(agentControlClient.getHostIp(), is("127.0.0.1"));
      assertThat(agentControlClient.getPort(), is(2181));
    }

    @Test
    public void testSetIpChanged() throws Throwable {
      agentControlClient.setIpAndPort("127.0.0.1", 2181);
      assertThat(agentControlClient.getHostIp(), is("127.0.0.1"));
      assertThat(agentControlClient.getPort(), is(2181));

      agentControlClient.setClientProxy(mock(AgentControl.AsyncClient.class));

      agentControlClient.setIpAndPort("127.0.0.1", 2180);
      assertThat(agentControlClient.getHostIp(), is("127.0.0.1"));
      assertThat(agentControlClient.getPort(), is(2180));
      assertThat(agentControlClient.getClientProxy(), nullValue());
    }

  }

  /**
   * This class implements tests for the provision method.
   */
  public class ProvisionTest {

    private List<String> dataStoreList = Arrays.asList("dataStore1", "dataStore2", "dataStore3");
    private String imageDataStore = "dataStore1";
    private boolean usedForVms = false;
    private String hostAddress = "hostAddress";
    private int hostPort = 8000;
    private double memoryOverCommit = 1.0;
    private String loggingEndpoint = "loggingEndpoint";
    private String logLevel = "logLevel";
    private StatsPluginConfig statsPluginConfig = new StatsPluginConfig(false);
    private boolean managementOnly = false;
    private String hostId = "id1";
    private String deploymentId = "deploymentId";
    private String ntpEndpoint = "ntpEndpoint";

    @BeforeMethod
    private void setUp() {
      AgentControlClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      agentControlClient = null;
    }

    private Answer getAnswer(final AgentControl.AsyncClient.provision_call provisionCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<AgentControl.AsyncClient.provision_call> handler = (AsyncMethodCallback) args[1];
          handler.onComplete(provisionCall);
          return null;
        }
      };
    }

    public void testSuccess() throws Exception {
      ProvisionResponse provisionResponse = new ProvisionResponse();
      provisionResponse.setResult(ProvisionResultCode.OK);
      final AgentControl.AsyncClient.provision_call provisionCall = mock(AgentControl.AsyncClient.provision_call.class);
      doReturn(provisionResponse).when(provisionCall).getResult();
      ArgumentCaptor<ProvisionRequest> request = ArgumentCaptor.forClass(ProvisionRequest.class);
      doAnswer(getAnswer(provisionCall))
          .when(clientProxy).provision(any(ProvisionRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      assertThat(agentControlClient.provision(dataStoreList, new HashSet<>(Arrays.asList(imageDataStore)),
              usedForVms, hostAddress, hostPort, memoryOverCommit, loggingEndpoint,
              logLevel, statsPluginConfig, managementOnly, hostId, deploymentId, ntpEndpoint),
          is(provisionResponse));
      verify(clientProxy).provision(request.capture(), any(AsyncMethodCallback.class));
      // Verify that the image_datastores field is set.
      assertThat(request.getValue().getImage_datastores(), containsInAnyOrder(imageDataStore));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        agentControlClient.provision(dataStoreList, Collections.singleton(imageDataStore), usedForVms,
            hostAddress, hostPort, memoryOverCommit, loggingEndpoint,
            logLevel, statsPluginConfig, managementOnly, hostId, deploymentId, ntpEndpoint);
        fail("Synchronous provision call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).provision(any(ProvisionRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.provision(dataStoreList, new HashSet<>(Arrays.asList(imageDataStore)),
            usedForVms, hostAddress, hostPort, memoryOverCommit, loggingEndpoint,
            logLevel, statsPluginConfig, managementOnly, hostId, deploymentId, ntpEndpoint);
        fail("Synchronous provision call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {
      final AgentControl.AsyncClient.provision_call provisionCall = mock(AgentControl.AsyncClient.provision_call.class);
      doThrow(new TException("Thrift exception")).when(provisionCall).getResult();
      doAnswer(getAnswer(provisionCall))
          .when(clientProxy).provision(any(ProvisionRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.provision(dataStoreList, new HashSet<>(Arrays.asList(imageDataStore)),
            usedForVms, hostAddress, hostPort, memoryOverCommit, loggingEndpoint,
            logLevel, statsPluginConfig, managementOnly, hostId, deploymentId, ntpEndpoint);
        fail("Synchronous provision call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "ProvisionFailureResultCodes")
    public void testFailureResult(ProvisionResultCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      ProvisionResponse provisionResponse = new ProvisionResponse();
      provisionResponse.setResult(resultCode);
      provisionResponse.setError(resultCode.toString());

      final AgentControl.AsyncClient.provision_call provisionCall = mock(AgentControl.AsyncClient.provision_call.class);
      doReturn(provisionResponse).when(provisionCall).getResult();
      doAnswer(getAnswer(provisionCall))
          .when(clientProxy).provision(any(ProvisionRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.provision(dataStoreList, new HashSet<>(Arrays.asList(imageDataStore)),
            usedForVms, hostAddress, hostPort, memoryOverCommit, loggingEndpoint,
            logLevel, statsPluginConfig, managementOnly, hostId, deploymentId, ntpEndpoint);
        fail("Synchronous provision call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
        assertThat(e.getMessage(), is(resultCode.toString()));
      }
    }

    @DataProvider(name = "ProvisionFailureResultCodes")
    public Object[][] getProvisionFailureResultCodes() {
      return new Object[][]{
          {ProvisionResultCode.INVALID_CONFIG, InvalidAgentConfigurationException.class},
          {ProvisionResultCode.INVALID_STATE, InvalidAgentStateException.class},
          {ProvisionResultCode.SYSTEM_ERROR, SystemErrorException.class},
      };
    }

    @Test(enabled = false)
    public void testFailureUnknownResult() {
    }
  }

  /**
   * Tests for the getAgentStatus method.
   */
  public class GetAgentStatusTest {

    @BeforeMethod
    private void setUp() {
      AgentControlClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      agentControlClient = null;
    }

    private Answer getAnswer(final AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call> handler = (AsyncMethodCallback) args[0];
          handler.onComplete(getAgentStatusCall);
          return null;
        }
      };
    }

    @Test
    public void testSuccess() throws Exception {
      AgentStatusResponse agentStatusResponse = new AgentStatusResponse();
      agentStatusResponse.setStatus(AgentStatusCode.OK);

      final AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall =
          mock(AgentControl.AsyncClient.get_agent_status_call.class);

      doReturn(agentStatusResponse).when(getAgentStatusCall).getResult();


      doAnswer(getAnswer(getAgentStatusCall))
          .when(clientProxy).get_agent_status(any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);
      assertThat(agentControlClient.getAgentStatus(), is(agentStatusResponse));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        agentControlClient.getAgentStatus();
        fail("Synchronous getAgentStatus call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).get_agent_status(any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.getAgentStatus();
        fail("Synchronous getAgentStatus call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test
    public void testFailureTExceptionOnGetResult() throws Exception {

      final AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall =
          mock(AgentControl.AsyncClient.get_agent_status_call.class);

      doThrow(new TException("Thrift exception")).when(getAgentStatusCall).getResult();


      doAnswer(getAnswer(getAgentStatusCall))
          .when(clientProxy).get_agent_status(any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.getAgentStatus();
        fail("Synchronous getAgentStatus call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }

    @Test(dataProvider = "GetAgentStatusFailureResultCodes")
    public void testFailureResult(AgentStatusCode resultCode,
                                  Class<RuntimeException> exceptionClass) throws Exception {
      AgentStatusResponse agentStatusResponse = new AgentStatusResponse();
      agentStatusResponse.setStatus(resultCode);

      final AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall =
          mock(AgentControl.AsyncClient.get_agent_status_call.class);

      doReturn(agentStatusResponse).when(getAgentStatusCall).getResult();
      doAnswer(getAnswer(getAgentStatusCall))
          .when(clientProxy).get_agent_status(any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.getAgentStatus();
        fail("Synchronous getAgentStatus call should throw on failure result: " + resultCode.toString());
      } catch (Exception e) {
        assertTrue(e.getClass() == exceptionClass);
      }
    }

    @DataProvider(name = "GetAgentStatusFailureResultCodes")
    public Object[][] getAgentStatusFailureResultCodes() {
      return new Object[][]{
          {AgentStatusCode.RESTARTING, IllegalStateException.class},
          {AgentStatusCode.UPGRADING, IllegalStateException.class},
      };
    }
  }

  /**
   * This class implements tests for the ping method.
   */
  public class PingTest {

    @BeforeMethod
    private void setUp() {
      AgentControlClientTest.this.setUp();
    }

    @AfterMethod
    private void tearDown() {
      agentControlClient = null;
    }

    private Answer getAnswer(final AgentControl.AsyncClient.ping_call pingCall) {
      return invocation -> {
        Object[] args = invocation.getArguments();
        AsyncMethodCallback<AgentControl.AsyncClient.ping_call> handler = (AsyncMethodCallback) args[1];
        handler.onComplete(pingCall);
        return null;
      };
    }

    @Test
    public void testSuccess() throws Exception {
      final AgentControl.AsyncClient.ping_call pingCall = mock(AgentControl.AsyncClient.ping_call.class);
      ArgumentCaptor<PingRequest> request = ArgumentCaptor.forClass(PingRequest.class);
      doAnswer(getAnswer(pingCall))
          .when(clientProxy).ping(any(PingRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      agentControlClient.ping();
      verify(clientProxy).ping(request.capture(), any(AsyncMethodCallback.class));
    }

    @Test
    public void testFailureNullHostIp() throws Exception {
      try {
        agentControlClient.ping();
        fail("Synchronous provision call should throw with null async clientProxy");
      } catch (IllegalArgumentException e) {
        assertThat(e.toString(), is("java.lang.IllegalArgumentException: hostname can't be null"));
      }
    }

    @Test
    public void testFailureTExceptionOnCall() throws Exception {
      doThrow(new TException("Thrift exception"))
          .when(clientProxy).ping(any(PingRequest.class), any(AsyncMethodCallback.class));

      agentControlClient.setClientProxy(clientProxy);

      try {
        agentControlClient.ping();
        fail("Synchronous provision call should convert TException on call to RpcException");
      } catch (RpcException e) {
        assertThat(e.getMessage(), is("Thrift exception"));
      }
    }
  }
}
