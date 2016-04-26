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

package com.vmware.photon.controller.apife.clients;

import com.vmware.photon.controller.api.Component;
import com.vmware.photon.controller.api.ComponentStatus;
import com.vmware.photon.controller.api.SystemStatus;
import com.vmware.photon.controller.apife.clients.status.DcpStatusProvider;
import com.vmware.photon.controller.apife.clients.status.DcpStatusProviderFactory;
import com.vmware.photon.controller.apife.clients.status.StatusProviderFactory;
import com.vmware.photon.controller.apife.clients.status.ThriftClientFactory;
import com.vmware.photon.controller.apife.config.StatusConfig;
import com.vmware.photon.controller.common.clients.DeployerClient;
import com.vmware.photon.controller.common.clients.HousekeeperClient;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.deployer.gen.Deployer;
import com.vmware.photon.controller.housekeeper.gen.Housekeeper;
import com.vmware.photon.controller.scheduler.root.gen.RootScheduler;
import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private List<ClientPool> singleInstanceClientPools;
  private List<ClientProxy> singleInstanceClientProxies;

  private List<StatusProvider> housekeeperClients;
  private List<StatusProvider> rootSchedulerClients;
  private List<StatusProvider> deployerClients;
  private List<StatusProvider> cloudStoreClients;

  private ClientProxyFactory<Housekeeper.AsyncClient> houseKeeperProxyFactory = mock(ClientProxyFactory.class);
  private ClientProxyFactory<RootScheduler.AsyncClient> rootSchedulerProxyFactory = mock(ClientProxyFactory.class);
  private ClientProxyFactory<Deployer.AsyncClient> deployerProxyFactory = mock(ClientProxyFactory.class);

  private ClientPoolFactory<Housekeeper.AsyncClient> houseKeeperPoolFactory = mock(ClientPoolFactory.class);
  private ClientPoolFactory<RootScheduler.AsyncClient> rootSchedulerPoolFactory = mock(ClientPoolFactory.class);
  private ClientPoolFactory<Deployer.AsyncClient> deployerPoolFactory = mock(ClientPoolFactory.class);

  private ServerSet housekeeperServerSet = mock(StaticServerSet.class);
  private ServerSet rootSchedulerServerSet = mock(StaticServerSet.class);
  private ServerSet deployerServerSet = mock(StaticServerSet.class);
  private ServerSet cloudStoreServerSet = mock(StaticServerSet.class);

  private ExecutorService executor = Executors.newFixedThreadPool(5);

  @BeforeMethod
  public void setup() throws Throwable {
    statusConfig = new StatusConfig();
    statusConfig.setComponents(
        ImmutableList.of("housekeeper", "rootScheduler", "deployer", "cloudStore"));

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

    when(housekeeperClients.get(0).getStatus()).thenReturn(readyStatus);
    when(housekeeperClients.get(1).getStatus()).thenReturn(initializingStatus);
    when(housekeeperClients.get(2).getStatus()).thenReturn(errorStatus);

    SystemStatus systemStatus = client.getSystemStatus();
    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
    assertThat(systemStatus.getStatus(), is(StatusType.PARTIAL_ERROR));

    List<ComponentStatus> compStatuses = systemStatus.getComponents();

    for (ComponentStatus status : compStatuses) {
      if (status.getComponent().equals(Component.HOUSEKEEPER)) {
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
  public void testPartialErrorWithDifferentNumOfInstances() throws Throwable {
    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);
    Status initializingStatus = new Status(StatusType.INITIALIZING);
    setMessageAndStats(initializingStatus);
    Status errorStatus = new Status(StatusType.ERROR);
    setMessageAndStats(errorStatus);

    when(rootSchedulerServerSet.getServers()).thenReturn(singleInstanceServerSets.get(1).getServers());

    mockAllClientsToReturnSameStatus(readyStatus);

    when(housekeeperClients.get(0).getStatus()).thenReturn(readyStatus);
    when(housekeeperClients.get(1).getStatus()).thenReturn(initializingStatus);
    when(housekeeperClients.get(2).getStatus()).thenReturn(errorStatus);


    SystemStatus systemStatus = client.getSystemStatus();
    assertThat(systemStatus.getComponents().size(), is(statusConfig.getComponents().size()));
    assertThat(systemStatus.getStatus(), is(StatusType.PARTIAL_ERROR));

    List<ComponentStatus> compStatuses = systemStatus.getComponents();

    for (ComponentStatus status : compStatuses) {
      if (status.getComponent().equals(Component.HOUSEKEEPER)) {
        assertThat(status.getStatus(), is(StatusType.PARTIAL_ERROR));
        assertThat(status.getStats().get(StatusType.READY.toString()), is("1"));
        assertThat(status.getStats().get(StatusType.INITIALIZING.toString()), is("1"));
        assertThat(status.getStats().get(StatusType.ERROR.toString()), is("1"));
      } else if (status.getComponent().equals(Component.ROOT_SCHEDULER)) {
        assertThat(status.getStatus(), is(StatusType.READY));
        assertThat(status.getStats().get(StatusType.READY.toString()), is("1"));
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

  @Test
  public void testEmptyServerSet() throws Throwable {
    Status readyStatus = new Status(StatusType.READY);
    setMessageAndStats(readyStatus);
    when(deployerServerSet.getServers()).thenReturn(new HashSet<>());

    mockAllClientsToReturnSameStatus(readyStatus);

    SystemStatus systemStatus = client.getSystemStatus();

    assertThat(systemStatus.getComponents().size(), is(4));
    assertThat(systemStatus.getStatus(), is(StatusType.ERROR));

    ComponentStatus deployerComponent = systemStatus.getComponents().stream()
        .filter(c -> Component.DEPLOYER.equals(c.getComponent())).findFirst().get();
    assertThat(deployerComponent, notNullValue());
    assertThat(deployerComponent.getStatus(), is(StatusType.UNREACHABLE));
    assertThat(deployerComponent.getMessage(), is("Empty ServerSet"));
  }

  private void mockAllClientsToReturnSameStatus(Status status) {
    for (int i = 0; i < SERVER_COUNT; i++) {
      when(housekeeperClients.get(i).getStatus()).thenReturn(status);
      when(rootSchedulerClients.get(i).getStatus()).thenReturn(status);
      when(deployerClients.get(i).getStatus()).thenReturn(status);
      when(cloudStoreClients.get(i).getStatus()).thenReturn(status);
    }
  }

  private void setMessageAndStats(Status status) {
    status.setMessage("test");
    status.setStats(ImmutableMap.of("foo", "bar"));
  }

  private void prepareStatusFeClient() throws Throwable {

    client = new StatusFeClient(
        executor,
        housekeeperServerSet, rootSchedulerServerSet, deployerServerSet, cloudStoreServerSet,
        houseKeeperProxyFactory,
        houseKeeperPoolFactory,
        rootSchedulerProxyFactory,
        rootSchedulerPoolFactory,
        deployerProxyFactory,
        deployerPoolFactory,
        statusConfig);

    Map<Component, StatusProviderFactory> statusProviderFactories = client.getStatusProviderFactories();
    ThriftClientFactory housekeeperClientFactory = spy(new ThriftClientFactory(
        housekeeperServerSet, houseKeeperPoolFactory, houseKeeperProxyFactory, HousekeeperClient.class, "Housekeeper"));
    setupStatusProviderFactory(housekeeperClientFactory, housekeeperClients);
    statusProviderFactories.put(Component.HOUSEKEEPER, housekeeperClientFactory);

    StatusProviderFactory rootSchedulerClientFactory = spy(new DcpStatusProviderFactory(rootSchedulerServerSet, this
        .executor));
    setupStatusProviderFactory(rootSchedulerClientFactory, rootSchedulerClients);
    statusProviderFactories.put(Component.ROOT_SCHEDULER, rootSchedulerClientFactory);

    ThriftClientFactory deployerClientFactory = spy(new ThriftClientFactory(
        deployerServerSet, deployerPoolFactory, deployerProxyFactory, DeployerClient.class, "Deployer"));
    setupStatusProviderFactory(deployerClientFactory, deployerClients);
    statusProviderFactories.put(Component.DEPLOYER, deployerClientFactory);

    StatusProviderFactory cloudStoreClientFactory = spy(new DcpStatusProviderFactory(cloudStoreServerSet, executor));
    setupStatusProviderFactory(cloudStoreClientFactory, cloudStoreClients);
    statusProviderFactories.put(Component.CLOUD_STORE, cloudStoreClientFactory);
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

  private void createServerSet() {
    servers = new ArrayList<>();
    serverSet = new HashSet<>();
    for (int i = 0; i < SERVER_COUNT; i++) {
      InetSocketAddress server = new InetSocketAddress("192.168.1." + i, 256);
      servers.add(server);
      serverSet.add(server);
    }

    when(housekeeperServerSet.getServers()).thenReturn(serverSet);
    when(rootSchedulerServerSet.getServers()).thenReturn(serverSet);
    when(deployerServerSet.getServers()).thenReturn(serverSet);
    when(cloudStoreServerSet.getServers()).thenReturn(serverSet);
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
    housekeeperClients = new ArrayList<>();
    rootSchedulerClients = new ArrayList<>();
    deployerClients = new ArrayList<>();
    cloudStoreClients = new ArrayList<>();
    for (int i = 0; i < SERVER_COUNT; i++) {
      housekeeperClients.add(mock(HousekeeperClient.class));
      rootSchedulerClients.add(mock(DcpStatusProvider.class));
      deployerClients.add(mock(DeployerClient.class));
      cloudStoreClients.add(mock(DcpStatusProvider.class));
    }
  }
}
