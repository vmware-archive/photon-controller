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

package com.vmware.photon.controller.api.frontend.clients;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.clients.status.StatusProviderFactory;
import com.vmware.photon.controller.api.frontend.clients.status.XenonStatusProvider;
import com.vmware.photon.controller.api.frontend.clients.status.XenonStatusProviderFactory;
import com.vmware.photon.controller.api.frontend.config.StatusConfig;
import com.vmware.photon.controller.api.model.Component;
import com.vmware.photon.controller.api.model.ComponentStatus;
import com.vmware.photon.controller.api.model.SystemStatus;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.NodeState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tests {@link StatusFeClient}.
 */
public class StatusFeClientTest {

  private static final int SERVER_COUNT = 3;

  private List<InetSocketAddress> servers;
  private Set<InetSocketAddress> serverSet;

  private StatusConfig statusConfig;
  private StatusFeClient client;

  private List<ServerSet> singleInstanceServerSets;
  private List<ClientPool<?>> singleInstanceClientPools;
  private List<ClientProxy<?>> singleInstanceClientProxies;

  private List<StatusProvider> photonControllerClients;

  private ExecutorService executor = Executors.newFixedThreadPool(5);

  private ApiFeXenonRestClient xenonClient;
  private BasicServiceHost host;

  @BeforeMethod
  public void setup() throws Throwable {
    xenonClient = mock(ApiFeXenonRestClient.class);

    statusConfig = new StatusConfig();
    statusConfig.setComponents(
        ImmutableList.of("photon-controller"));

    createServerSet();

    createIndividualServerSets();

    mockClientPools();
    mockClientProxies();
    mockComponentClients();

    prepareStatusFeClient();
  }

  @Test
  public void testAllComponents() throws Throwable {
    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);

    mockAllClientsToReturnSameStatus(readyStatus);

    SystemStatus systemStatus = client.getSystemStatus();

    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
    assertThat(systemStatus.getStatus(), is(StatusType.READY));
  }

  @Test
  public void testDefaultComponents() throws Throwable {
    statusConfig.setComponents(ImmutableList.<String>of());

    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);
    mockAllClientsToReturnSameStatus(readyStatus);

    SystemStatus systemStatus = client.getSystemStatus();

    assertThat(systemStatus.getStatus(), is(StatusType.READY));
    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
  }

  @Test
  public void testPartialErrorScenarioInSystemStatus() throws Throwable {

    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);
    Status initializingStatus = new Status(StatusType.INITIALIZING);
    setMessageAndStats(initializingStatus);
    Status errorStatus = new Status(StatusType.ERROR);
    setMessageAndStats(errorStatus);

    mockAllClientsToReturnSameStatus(readyStatus);

    when(photonControllerClients.get(0).getStatus()).thenReturn(readyStatus);
    when(photonControllerClients.get(1).getStatus()).thenReturn(initializingStatus);
    when(photonControllerClients.get(2).getStatus()).thenReturn(errorStatus);

    SystemStatus systemStatus = client.getSystemStatus();
    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
    assertThat(systemStatus.getStatus(), is(StatusType.PARTIAL_ERROR));

    List<ComponentStatus> compStatuses = systemStatus.getComponents();

    for (ComponentStatus status : compStatuses) {
      if (status.getComponent().equals(Component.PHOTON_CONTROLLER)) {
        assertThat(status.getStatus(), is(StatusType.PARTIAL_ERROR));
        assertThat(status.getStats().get(StatusType.READY.toString()), is("1"));
        assertThat(status.getStats().get(StatusType.INITIALIZING.toString()), is("1"));
        assertThat(status.getStats().get(StatusType.ERROR.toString()), is("1"));
      } else {
        assertThat(status.getStatus(), is(StatusType.READY));
        assertThat(status.getStats().get(StatusType.READY.toString()), is("3"));
      }
    }
  }

  @Test
  public void testAllInstancesInErrorState() throws Throwable {
    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);
    Status initializingStatus = new Status(StatusType.INITIALIZING);
    setMessageAndStats(initializingStatus);
    Status errorStatus = new Status(StatusType.ERROR);
    setMessageAndStats(errorStatus);

    mockAllClientsToReturnSameStatus(errorStatus);

    SystemStatus systemStatus = client.getSystemStatus();
    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
    assertThat(systemStatus.getStatus(), is(StatusType.ERROR));

    List<ComponentStatus> compStatuses = systemStatus.getComponents();

    for (ComponentStatus status : compStatuses) {
      assertThat(status.getStatus(), is(StatusType.ERROR));
      assertThat(status.getStats().get(StatusType.ERROR.toString()), is("3"));
    }
  }

  private void mockAllClientsToReturnSameStatus(Status status) {
    for (int i = 0; i < SERVER_COUNT; i++) {
      when(photonControllerClients.get(i).getStatus()).thenReturn(status);
    }
  }

  private void setMessageAndStats(Status status) {
    status.setMessage("test");
    status.setStats(ImmutableMap.of("foo", "bar"));
  }

  private void prepareStatusFeClient() throws Throwable {
    host = new BasicServiceHost();
    client = new StatusFeClient(
        executor,
        Executors.newScheduledThreadPool(1),
        statusConfig,
        host,
        xenonClient);

    Map<Component, StatusProviderFactory> statusProviderFactories = client.getStatusProviderFactories();
    StatusProviderFactory photonControllerClientFactory = spy(new XenonStatusProviderFactory(
        new StaticServerSet(), executor, Executors.newScheduledThreadPool(1), host));
    setupStatusProviderFactory(photonControllerClientFactory, photonControllerClients);
    statusProviderFactories.put(Component.PHOTON_CONTROLLER, photonControllerClientFactory);
  }

  private void setupStatusProviderFactory(
      StatusProviderFactory statusProviderFactory, List<StatusProvider> clients)
      throws Throwable {
    for (int i = 0; i < servers.size(); i++) {
      doReturn(clients.get(i)).when(statusProviderFactory).create(servers.get(i));
    }
  }

  private void createIndividualServerSets() {
    singleInstanceServerSets = new ArrayList<>();
    for (int i = 0; i < servers.size(); i++) {
      singleInstanceServerSets.add(new StaticServerSet(servers.get(i)));
    }
  }

  private void createServerSet() throws Exception, DocumentNotFoundException {
    servers = new ArrayList<>();
    serverSet = new HashSet<>();

    NodeGroupService.NodeGroupState nodeGroupState = new NodeGroupService.NodeGroupState();
    for (int i = 0; i < SERVER_COUNT; i++) {
      InetSocketAddress server = new InetSocketAddress("192.168.1." + i, 256);
      servers.add(server);
      serverSet.add(server);

      NodeState nodeState = new NodeState();
      nodeState.groupReference = new URI("http", null, server.getHostString(), server.getPort(), null, null, null);
      nodeGroupState.nodes.put(UUID.randomUUID().toString(), nodeState);
    }

    com.vmware.xenon.common.Operation serviceOp = mock(com.vmware.xenon.common.Operation.class);
    when(serviceOp.getBody(Matchers.any())).thenReturn(nodeGroupState);
    when(xenonClient.get(ServiceUriPaths.DEFAULT_NODE_GROUP)).thenReturn(serviceOp);
  }

  private void mockClientPools() {
    singleInstanceClientPools = new ArrayList<>();
    for (int i = 0; i < SERVER_COUNT; i++) {
      singleInstanceClientPools.add(mock(ClientPool.class));
    }
  }

  private void mockClientProxies() {
    singleInstanceClientProxies = new ArrayList<>();
    for (int i = 0; i < SERVER_COUNT; i++) {
      singleInstanceClientProxies.add(mock(ClientProxy.class));
    }
  }

  private void mockComponentClients() {
    photonControllerClients = new ArrayList<>();
    for (int i = 0; i < SERVER_COUNT; i++) {
      photonControllerClients.add(mock(XenonStatusProvider.class));
    }
  }
}
