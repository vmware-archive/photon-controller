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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.HierarchyConfig;
import com.vmware.photon.controller.chairman.gen.RegisterHostRequest;
import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.ChairmanServiceTest;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.zookeeper.ZookeeperHostMonitor;
import com.vmware.photon.controller.common.zookeeper.ZookeeperMissingHostMonitor;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import org.apache.thrift.TException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMapOf;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Tests {@link HierarchyManager}.
 */
public class HierarchyManagerTest extends PowerMockTestCase {

  private HierarchyManager hierarchyManager;

  private Hierarchy hierarchy;

  private AvailabilityZone availabilityZone;

  private Set<Datastore> datastores;

  private Set<Network> networks;

  private HierarchyConfig hierarchyConfig;

  @Mock
  private HostConfigurator hostConfigurator;

  @Mock
  private ServerSet rootSchedulerServerSet;

  @Mock
  private HierarchyUtils hierarchyUtils;

  @Mock
  private ZookeeperHostMonitor hostMonitor;

  @Captor
  private ArgumentCaptor<Map<String, byte[]>> changeSetCap;

  @Captor
  private ArgumentCaptor<LinkedHashMap<String, Host>> hostsCap;

  @Mock
  private ZookeeperMissingHostMonitor missingHostMonitor;

  private List<NetworkType> defaultNetworkType;

  @Mock
  private ScheduledExecutorService executor;

  @BeforeMethod
  public void setUp() {
    hierarchyConfig = new HierarchyConfig();
    hierarchyManager = new HierarchyManager(hierarchyConfig, hostConfigurator, hostMonitor, missingHostMonitor,
        rootSchedulerServerSet, hierarchyUtils, executor);
    hierarchy = hierarchyManager.getHierarchy();
    availabilityZone = new AvailabilityZone("availability-zone");
    datastores = new HashSet<>();
    networks = new HashSet<>();
    defaultNetworkType = new ArrayList<>();
    defaultNetworkType.add(NetworkType.VM);

  }

  @Test
  public void testAddingRootSchedulerServer() {
    Host rootHost = hierarchy.getRootScheduler().getOwner();
    assertThat(rootHost, is(notNullValue()));
    String addr = "addr";
    int port = 1234;
    InetSocketAddress server1 = new InetSocketAddress(addr, port);
    hierarchyManager.onServerAdded(server1);
    rootHost = hierarchy.getRootScheduler().getOwner();
    // Verify that the root host has been created
    assertThat(rootHost, notNullValue());
    assertThat(rootHost.getAddress(), is(addr));
    assertThat(rootHost.getPort(), is(port));
    assertThat(rootHost.isConfigured(), is(false));
    assertThat(rootHost.isDirty(), is(true));

    // Set the rootHost as configure and re-add it
    rootHost.setDirty(false);
    rootHost.setConfigured(true);
    hierarchyManager.onServerAdded(server1);
    assertThat(rootHost.isConfigured(), is(false));
    assertThat(rootHost.isDirty(), is(true));
  }

  @Test
  public void testSimpleAddHostWithNetwork() throws TException {
    Network net1 = new Network("net1", defaultNetworkType);
    List<NetworkType> nonVmNetwork = new ArrayList();
    nonVmNetwork.add(NetworkType.MANAGEMENT);
    Network net2 = new Network("net2", nonVmNetwork);
    // testing Network.types not set should not throw nullpointerexception
    // from HierarchyManager.getVmNetwork
    Network net3 = new Network("net3", null);
    Datastore datastore1 = new Datastore("ds1", DatastoreType.SHARED_VMFS);
    Datastore datastore2 = new Datastore("ds2", DatastoreType.LOCAL_VMFS);
    networks.add(net1);
    networks.add(net2);
    networks.add(net3);
    datastores.add(datastore1);
    datastores.add(datastore2);
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(datastores, networks,
        new HashSet<>(Arrays.asList("ds1")));
    when(hierarchyUtils.getNetworks(request.getConfig())).thenReturn(new HashSet(networks));
    when(hierarchyUtils.getDatastores(request.getConfig())).thenReturn(new HashSet(datastores));
    hierarchyManager.onHostAdded("host1", request.getConfig());
    Host res = hierarchy.getHosts().get("host1");
    assertThat(res, notNullValue());
    // Verify that non-VM networks have been excluded
    assertThat(res.getDatastores(), containsInAnyOrder(datastore1, datastore2));
    assertThat(res.getNetworks().size(), is(1));
    assertThat(res.getNetworks().contains(net1), is(true));
  }

  @Test
  public void testSimpleAddHost() throws TException {
    Datastore datastore1 = new Datastore("test", DatastoreType.SHARED_VMFS);
    datastores.add(datastore1);
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(datastores);
    when(hierarchyUtils.getDatastores(request.getConfig())).thenReturn(datastores);
    hierarchyManager.onHostAdded("host1", request.getConfig());
    Host res = hierarchy.getHosts().get("host1");
    assertThat(res, notNullValue());
    assertThat(res.getDatastores().size(), is(datastores.size()));
    assertThat(res.getDatastores().contains(datastore1), is(true));

  }

  @Test
  public void testAddManagementOnlyHost() throws TException {
    Datastore datastore1 = new Datastore("test", DatastoreType.SHARED_VMFS);
    datastores.add(datastore1);
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(datastores);
    request.getConfig().setManagement_only(true);
    when(hierarchyUtils.getDatastores(request.getConfig())).thenReturn(datastores);
    hierarchyManager.onHostAdded("host1", request.getConfig());
    Host res = hierarchy.getHosts().get("host1");
    assertThat(res, notNullValue());
  }

  @Test
  public void testPersistDirtyHosts() throws Exception {
    // Test that dirty hosts and persisted to zk and configured
    datastores.add(new Datastore("DS1", DatastoreType.SHARED_VMFS));
    String hostId1 = "host1";
    String hostId2 = "host2";
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(hostId1, availabilityZone.getId(),
        datastores, networks, "addr", 1234, new HashSet<>(Arrays.asList("DS1")));
    RegisterHostRequest request2 = ChairmanServiceTest.createRegReq(hostId2, availabilityZone.getId(),
        datastores, networks, "addr", 1234, new HashSet<>(Arrays.asList("DS1")));

    hierarchyManager.onHostAdded(hostId1, request.getConfig());
    hierarchyManager.onHostAdded(hostId2, request2.getConfig());

    InetSocketAddress server1 = new InetSocketAddress("addr", 1234);
    hierarchyManager.onServerAdded(server1);

    Host host1 = hierarchy.getHosts().get(hostId1);
    Host host2 = hierarchy.getHosts().get(hostId2);
    Host root = hierarchy.getRootScheduler().getOwner();

    Mockito.doReturn(mock(Future.class)).when(hostConfigurator).configure(any(Host.class), any(ConfigureRequest.class));

    hierarchyManager.scan();
    // Since the host configurator is mocked we need to set these flags
    host1.setConfigured(true);
    host2.setConfigured(true);
    root.setConfigured(true);

    // The second scan should have no affect (i.e. doesn't result in sending host configuration
    // flows and writing to /roles because the hierarchy doesn't change after the first scan)
    hierarchyManager.scan();
    // Make sure that the hierarchy is only persisted once
    verify(hierarchyUtils, times(1)).writeRolesToZk(changeSetCap.capture(), any(int.class));

    // Make sure the change set will persist host1 in /roles
    assertThat(changeSetCap.getValue().get(host1.getId()), notNullValue());
    assertThat(changeSetCap.getValue().size(), is(1));

    // Make sure both hosts are not dirty after persisting to zk
    assertThat(host1.isDirty(), is(false));
    assertThat(host2.isDirty(), is(false));

    // Make sure host configuration flows have been sent to dirty hosts
    verify(hostConfigurator).configure(eq(host1), any(ConfigureRequest.class));
    verify(hostConfigurator).configure(eq(host2), any(ConfigureRequest.class));
    verify(hostConfigurator).configure(eq(root), any(ConfigureRequest.class));

    verifyNoMoreInteractions(hostConfigurator);
  }

  @Test
  public void testDontConfigureHostsIfPersistStepFailed() throws Exception {
    datastores.add(new Datastore("DS1", DatastoreType.SHARED_VMFS));
    String hostId1 = "host1";
    String hostId2 = "host2";
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(hostId1, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));
    RegisterHostRequest request2 = ChairmanServiceTest.createRegReq(hostId2, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));

    doThrow(new Exception()).when(hierarchyUtils).writeRolesToZk(anyMapOf(String.class, byte[].class), any(int.class));

    hierarchyManager.onHostAdded(hostId1, request.getConfig());
    hierarchyManager.onHostAdded(hostId2, request2.getConfig());

    InetSocketAddress server1 = new InetSocketAddress("addr", 1234);
    hierarchyManager.onServerAdded(server1);

    Host host1 = hierarchy.getHosts().get(hostId1);
    Host host2 = hierarchy.getHosts().get(hostId2);

    hierarchyManager.scan();

    assertThat(host1.isDirty(), is(true));
    assertThat(host2.isDirty(), is(true));
    assertThat(host1.isConfigured(), is(false));
    assertThat(host2.isConfigured(), is(false));

    verifyNoMoreInteractions(hostConfigurator);
  }

  @Test
  public void testPersistDirtyHostsAndPrune() throws Exception {
    // Test that the scan will prune all scheduler hosts from /roles and then
    // write the new hierarchy.
    datastores.add(new Datastore("DS1", DatastoreType.SHARED_VMFS));
    String hostId1 = "host1";
    String hostId2 = "host2";
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(hostId1, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));
    RegisterHostRequest request2 = ChairmanServiceTest.createRegReq(hostId2, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));

    hierarchyManager.onHostAdded(hostId1, request.getConfig());
    hierarchyManager.onHostAdded(hostId2, request2.getConfig());

    InetSocketAddress server1 = new InetSocketAddress("addr", 1234);
    hierarchyManager.onServerAdded(server1);

    Host host1 = hierarchy.getHosts().get(hostId1);
    Host host2 = hierarchy.getHosts().get(hostId2);
    Host root = hierarchy.getRootScheduler().getOwner();

    List<String> schedulerHosts = new ArrayList();
    schedulerHosts.add("host3");
    schedulerHosts.add("host1");
    Mockito.doReturn(mock(Future.class)).when(hostConfigurator).configure(any(Host.class), any(ConfigureRequest.class));
    when(hierarchyUtils.getSchedulerHosts()).thenReturn(schedulerHosts);

    hierarchyManager.scan();

    // make sure that hosts have been un-dirtied
    assertThat(host1.isDirty(), is(false));
    assertThat(host2.isDirty(), is(false));

    verify(hierarchyUtils, times(1)).writeRolesToZk(changeSetCap.capture(), any(int.class));

    ArgumentCaptor<Host> hostCap = ArgumentCaptor.forClass(Host.class);

    // Since host1 is dirty and it used to be a host scheduler, then make sure it
    // is overwritten and not deleted.
    assertThat(changeSetCap.getValue().get(host1.getId()), not(nullValue()));
    // Since host3 is no longer a host scheduler it is deleted
    assertThat(changeSetCap.getValue().containsKey("host3"), is(true));
    // Make sure that the changeSet only prunes host3 and overwrite host1
    assertThat(changeSetCap.getValue().size(), is(2));

    verify(hostConfigurator, times(3)).configure(hostCap.capture(), any(ConfigureRequest.class));

    verifyNoMoreInteractions(hostConfigurator);

    // Verify that all the dirty hosts were configured
    assertThat(hostCap.getAllValues().get(0), is(host1));
    assertThat(hostCap.getAllValues().get(1), is(host2));
    assertThat(hostCap.getAllValues().get(2), is(root));
  }

  @Test
  public void testInitSuccess() throws Exception {
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host3 = new Host("host3", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host4 = new Host("host4", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);

    Scheduler sch1 = new Scheduler("sch1");
    sch1.setOwner(host1);
    sch1.addHost(host1);

    Scheduler sch2 = new Scheduler("sch2");
    sch2.setOwner(host2);
    sch2.addHost(host2);
    sch2.addHost(host3);

    LinkedHashMap<String, Host> hosts = new LinkedHashMap<>();
    hosts.put(host1.getId(), host1);
    hosts.put(host2.getId(), host2);
    hosts.put(host3.getId(), host3);
    hosts.put(host4.getId(), host4);

    Map<String, Scheduler> schedulers = new HashMap<>();
    schedulers.put(sch1.getId(), sch1);
    schedulers.put(sch2.getId(), sch2);

    when(hierarchyUtils.readHostsFromZk()).thenReturn(hosts);
    when(hierarchyUtils.readSchedulersFromZk(eq(hosts))).thenReturn(schedulers);
    when(hierarchyUtils.getRolesDictVersion()).thenReturn(1);

    Future<?> future1 = mock(Future.class);
    Future<?> future2 = mock(Future.class);
    Future<?> future3 = mock(Future.class);
    Future<?> future4 = mock(Future.class);

    Mockito.doReturn(future1).when(hostConfigurator).configure(eq(host1), any(ConfigureRequest.class));
    Mockito.doReturn(future2).when(hostConfigurator).configure(eq(host2), any(ConfigureRequest.class));
    Mockito.doReturn(future3).when(hostConfigurator).configure(eq(host3), any(ConfigureRequest.class));
    Mockito.doReturn(future4).when(hostConfigurator).configure(eq(host4), any(ConfigureRequest.class));

    hierarchyManager.init();

    Map<String, Host> tHosts = hierarchy.getHosts();
    Map<String, Scheduler> tSchedulers = hierarchy.getLeafSchedulers();

    assertThat(tHosts.get(host1.getId()), not(nullValue()));
    assertThat(tHosts.get(host2.getId()), not(nullValue()));
    assertThat(tHosts.get(host3.getId()), not(nullValue()));
    assertThat(tHosts.size(), is(hosts.size()));

    assertThat(tSchedulers.get(sch1.getId()), not(nullValue()));
    assertThat(tSchedulers.get(sch2.getId()), not(nullValue()));
    // Make sure the root scheduler was added
    assertThat(tSchedulers.size(), is(schedulers.size()));

    // Make sure leaf schedulers were added to the root scheduler
    assertThat(tSchedulers.get(sch1.getId()).getParent().getId(), is(hierarchy.getRootScheduler().getId()));
    assertThat(tSchedulers.get(sch2.getId()).getParent().getId(), is(hierarchy.getRootScheduler().getId()));

    // Make sure that hosts are non dirty and not configured
    assertThat(tHosts.get(host1.getId()).isDirty(), is(false));
    assertThat(tHosts.get(host2.getId()).isDirty(), is(false));
    assertThat(tHosts.get(host3.getId()).isDirty(), is(false));

    verify(future1).get();
    verify(future2).get();
    verify(future3).get();
    verify(future4, never()).get();
  }

  @Test
  public void testInitFailAndRetry() throws Exception {
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host3 = new Host("host3", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);

    Scheduler sch1 = new Scheduler("sch1");
    sch1.setOwner(host1);
    sch1.addHost(host1);

    Scheduler sch2 = new Scheduler("sch2");
    sch2.setOwner(host2);
    sch2.addHost(host2);
    sch2.addHost(host3);

    LinkedHashMap<String, Host> hosts = new LinkedHashMap<>();
    hosts.put(host1.getId(), host1);
    hosts.put(host2.getId(), host2);
    hosts.put(host3.getId(), host3);

    Map<String, Scheduler> schedulers = new HashMap<>();
    schedulers.put(sch1.getId(), sch1);
    schedulers.put(sch2.getId(), sch2);

    when(hierarchyUtils.readHostsFromZk()).thenReturn(hosts);
    when(hierarchyUtils.readSchedulersFromZk(eq(hosts))).thenReturn(schedulers);
    when(hierarchyUtils.getRolesDictVersion()).thenReturn(1);

    Future<?> future1 = mock(Future.class);
    Future<?> future2 = mock(Future.class);
    Future<?> future3 = mock(Future.class);

    Mockito.doReturn(future1).when(hostConfigurator).configure(eq(host1), any(ConfigureRequest.class));
    Mockito.doReturn(future2).when(hostConfigurator).configure(eq(host2), any(ConfigureRequest.class));
    Mockito.doReturn(future3).when(hostConfigurator).configure(eq(host3), any(ConfigureRequest.class));

    hierarchyManager.init();

    // Verify that init retried building the hierarchy
    verify(hierarchyUtils).readHostsFromZk();
    verify(hierarchyUtils).readSchedulersFromZk(eq(hosts));
    verify(hierarchyUtils).getRolesDictVersion();
    // Verify that the tree structure is correct
    Map<String, Host> tHosts = hierarchy.getHosts();
    Map<String, Scheduler> tSchedulers = hierarchy.getLeafSchedulers();

    assertThat(tHosts.get(host1.getId()), not(nullValue()));
    assertThat(tHosts.get(host2.getId()), not(nullValue()));
    assertThat(tHosts.get(host3.getId()), not(nullValue()));
    assertThat(tHosts.size(), is(hosts.size()));

    assertThat(tSchedulers.get(sch1.getId()), not(nullValue()));
    assertThat(tSchedulers.get(sch2.getId()), not(nullValue()));
    // Make sure the root scheduler was added
    assertThat(tSchedulers.size(), is(schedulers.size()));

    // Make sure leaf schedulers were added to the root scheduler
    assertThat(tSchedulers.get(sch1.getId()).getParent().getId(), is(hierarchy.getRootScheduler().getId()));
    assertThat(tSchedulers.get(sch2.getId()).getParent().getId(), is(hierarchy.getRootScheduler().getId()));

    // Make sure that hosts are non dirty and not configured
    assertThat(tHosts.get(host1.getId()).isDirty(), is(false));
    assertThat(tHosts.get(host2.getId()).isDirty(), is(false));
    assertThat(tHosts.get(host3.getId()).isDirty(), is(false));

    verify(future1).get();
    verify(future2).get();
    verify(future3).get();
  }

  @Test
  public void testInitMissingHosts() throws Exception {
    final Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    final Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    Host host3 = new Host("host3", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);

    Scheduler sch1 = new Scheduler("sch1");
    sch1.setOwner(host1);
    sch1.addHost(host1);

    Scheduler sch2 = new Scheduler("sch2");
    sch2.setOwner(host2);
    sch2.addHost(host2);
    sch2.addHost(host3);

    LinkedHashMap<String, Host> hosts = new LinkedHashMap<>();
    hosts.put(host1.getId(), host1);
    hosts.put(host2.getId(), host2);
    hosts.put(host3.getId(), host3);

    Map<String, Scheduler> schedulers = new HashMap<>();
    schedulers.put(sch1.getId(), sch1);
    schedulers.put(sch2.getId(), sch2);

    when(hierarchyUtils.readHostsFromZk()).thenReturn(hosts);
    when(hierarchyUtils.readSchedulersFromZk(eq(hosts))).thenReturn(schedulers);
    when(hierarchyUtils.getRolesDictVersion()).thenReturn(1);

    // Fire missing host events during the init stage
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        hierarchyManager.onHostAdded(host1.getId());
        hierarchyManager.onHostAdded(host2.getId());
        hierarchyManager.onHostAdded("UnknownHostId");
        return null;
      }
    }).when(missingHostMonitor).addChangeListener(eq(hierarchyManager));

    Future<?> future1 = mock(Future.class);
    Future<?> future2 = mock(Future.class);
    Future<?> future3 = mock(Future.class);

    Mockito.doReturn(future1).when(hostConfigurator).configure(eq(host1), any(ConfigureRequest.class));
    Mockito.doReturn(future2).when(hostConfigurator).configure(eq(host2), any(ConfigureRequest.class));
    Mockito.doReturn(future3).when(hostConfigurator).configure(eq(host3), any(ConfigureRequest.class));

    hierarchyManager.init();

    Map<String, Host> tHosts = hierarchy.getHosts();

    // Make sure that the reported hosts are marked as missing
    assertThat(tHosts.get(host1.getId()).isMissing(), is(true));
    assertThat(tHosts.get(host2.getId()).isMissing(), is(true));
    assertThat(tHosts.get(host3.getId()).isMissing(), is(false));

    verify(future1).get();
    verify(future2).get();
    verify(future3).get();
  }

  @Test
  public void testScanWaitsForConfigureRequests() throws Exception {
    datastores.add(new Datastore("DS1", DatastoreType.SHARED_VMFS));
    datastores.add(new Datastore("DS1", DatastoreType.SHARED_VMFS));
    String hostId1 = "host1";
    String hostId2 = "host2";
    RegisterHostRequest request = ChairmanServiceTest.createRegReq(hostId1, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));
    RegisterHostRequest request2 = ChairmanServiceTest.createRegReq(hostId2, availabilityZone.getId(),
        datastores, networks, "host", 1234, new HashSet<>(Arrays.asList("DS1")));

    hierarchyManager.onHostAdded(hostId1, request.getConfig());
    hierarchyManager.onHostAdded(hostId2, request2.getConfig());

    InetSocketAddress server1 = new InetSocketAddress("addr", 1234);
    hierarchyManager.onServerAdded(server1);

    Host host1 = hierarchy.getHosts().get(hostId1);
    Host host2 = hierarchy.getHosts().get(hostId2);
    Host root = hierarchy.getRootScheduler().getOwner();

    Future<?> future1 = mock(Future.class);
    Future<?> future2 = mock(Future.class);
    Future<?> future3 = mock(Future.class);

    Mockito.doReturn(future1).when(hostConfigurator).configure(eq(host1), any(ConfigureRequest.class));
    Mockito.doReturn(future2).when(hostConfigurator).configure(eq(host2), any(ConfigureRequest.class));
    Mockito.doReturn(future3).when(hostConfigurator).configure(eq(root), any(ConfigureRequest.class));

    // After sending configurationHostFlows to both hosts and root
    // verify that the scan method will call get on the future. In other words,
    // wait for all the configure requests to finish processing

    hierarchyManager.scan();

    verify(future1).get();
    verify(future2).get();
    verify(future3).get();

  }

  /**
   * Test that the scanner gets started onJoin and stopped onLeave.
   */
  @Test
  public void testJoinLeave() {
    HierarchyManager manager = mock(HierarchyManager.class);
    hierarchyConfig.setEnableScan(true);
    when(manager.getConfig()).thenReturn(hierarchyConfig);
    when(manager.getExecutor()).thenReturn(executor);
    doNothing().when(manager).init();
    doCallRealMethod().when(manager).onJoin();

    manager.onJoin();
    verify(manager).cancelScan();
    verify(manager).init();

    reset(manager);
    doCallRealMethod().when(manager).onLeave();
    manager.onLeave();
    verify(manager).cancelScan();
  }
}
