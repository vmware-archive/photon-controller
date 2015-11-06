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

import com.vmware.photon.controller.chairman.gen.RegisterHostRequest;
import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.ChairmanServiceTest;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.common.zookeeper.DataDictionary;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import org.apache.thrift.TSerializer;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests {@link HierarchyUtilsTest}.
 */
public class HierarchyUtilsTest extends PowerMockTestCase {

  private HierarchyUtils hierarchyUtils;

  @Mock
  private DataDictionary rolesDict;

  @Mock
  private DataDictionary hostsDict;

  private Scheduler rootScheduler;

  private Host rootSchedulerHost;

  private Set<Datastore> datastores;

  private Set<Network> networks;

  private List<NetworkType> defaultNetworkType;

  @BeforeMethod
  public void setUp() {
    hierarchyUtils = new HierarchyUtils(rolesDict, hostsDict);
    rootScheduler = new Scheduler(HierarchyManager.ROOT_SCHEDULER_ID);
    rootSchedulerHost = new Host(HierarchyManager.ROOT_SCHEDULER_HOST_ID,
        HierarchyManager.ROOT_SCHEDULER_AVAILABILITY_ZONE, null, -1);
    rootScheduler.setOwner(rootSchedulerHost);
    rootSchedulerHost.setDirty(false);

    datastores = new HashSet<>();
    networks = new HashSet<>();
    defaultNetworkType = new ArrayList<>();
    defaultNetworkType.add(NetworkType.VM);
  }

  @Test
  public void testgetConfigureRequestCompact() throws Exception {
    Set<Datastore> datastores = new HashSet<>();
    Set<Network> networks = new HashSet<>();
    datastores.add(new Datastore("ds1", DatastoreType.SHARED_VMFS));
    networks.add(new Network("net1", defaultNetworkType));

    Host h1 = new Host("h1", new AvailabilityZone("az1"), datastores, networks, "addr", 1234);
    // Leaf scheduler with two hosts
    Scheduler sc1 = new Scheduler("sc1");
    sc1.setOwner(h1);
    sc1.addHost(h1);

    rootScheduler.addChild(sc1);

    ConfigureRequest configReq = HierarchyUtils.getConfigureRequest(h1, false);
    SchedulerRole schRoleCompact = configReq.getRoles().getSchedulers().get(0);
    // Verify that constraints aren't set
    assertThat(schRoleCompact.getHost_children().get(0).isSetConstraints(), is(false));

    configReq = HierarchyUtils.getConfigureRequest(h1, true);
    SchedulerRole schRole = configReq.getRoles().getSchedulers().get(0);
    // Verify that constraints are set
    assertThat(schRole.getHost_children().get(0).isSetConstraints(), is(true));
  }

  @Test
  public void testRootSchedulerConfigReq() throws Exception {
    Host h1 = new Host("h1", new AvailabilityZone("az1"), "addr", 1234);
    Host h2 = new Host("h2", new AvailabilityZone("az1"), "addr", 1235);
    Host h3 = new Host("h3", new AvailabilityZone("az1"), "addr", 1236);

    // Leaf scheduler with two hosts
    Scheduler sc1 = new Scheduler("sc1");
    sc1.setOwner(h1);
    sc1.addHost(h1);
    sc1.addHost(h2);

    // Leaf scheduler with one host
    Scheduler sc2 = new Scheduler("sc2");
    sc2.setOwner(h3);
    sc2.addHost(h3);

    rootScheduler.addChild(sc1);
    rootScheduler.addChild(sc2);

    ConfigureRequest configReq = HierarchyUtils.getConfigureRequest(rootSchedulerHost);

    // Verify that the root scheduler only has one scheduler role
    assertThat(configReq.getRoles().getSchedulers().size(), is(1));

    SchedulerRole role = configReq.getRoles().getSchedulers().get(0);

    // Verify that it was configured to have two leaf schedulers
    assertThat(role.getScheduler_children().size(), is(rootScheduler.getChildren().size()));

    HashMap<String, ChildInfo> leafSch = new HashMap<>();
    for (ChildInfo child : role.getScheduler_children()) {
      leafSch.put(child.getId(), child);
    }

    // Verify that the leaf scheduler weights are set correctly
    assertThat(leafSch.get("sc1").getWeight(), is(sc1.getHosts().size()));
    assertThat(leafSch.get("sc2").getWeight(), is(sc2.getHosts().size()));

    // Verify that the leaf schedulers owner host ids are correct
    assertThat(leafSch.get("sc1").getOwner_host(), is(sc1.getOwner().getId()));
    assertThat(leafSch.get("sc2").getOwner_host(), is(sc2.getOwner().getId()));
  }

  @Test
  public void testReadHostsFromZk() throws Exception {
    Datastore ds1 = new Datastore("DS1", DatastoreType.SHARED_VMFS);
    Network net1 = new Network("net1", defaultNetworkType);
    datastores.add(ds1);
    networks.add(net1);
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);
    Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "adddr2", 1235);

    byte[] hostConfig1 = ChairmanServiceTest.serialize(getHostConfig(host1));
    byte[] hostConfig2 = ChairmanServiceTest.serialize(getHostConfig(host2));

    when(hostsDict.getKeys()).thenReturn(Arrays.asList(host1.getId(), host2.getId()));
    when(hostsDict.read(host1.getId())).thenReturn(hostConfig1);
    when(hostsDict.read(host2.getId())).thenReturn(hostConfig2);


    Map<String, Host> hosts = hierarchyUtils.readHostsFromZk();

    assertThat(hosts.size(), is(2));

    // Check that all hosts and schedulers were read correctly
    assertThat(hosts.get(host1.getId()), is(notNullValue()));
    assertThat(hosts.get(host2.getId()), is(notNullValue()));

    Host resHost1 = hosts.get(host1.getId());
    Host resHost2 = hosts.get(host2.getId());

    assertThat(resHost1.getNetworks().iterator().next(), is(host1.getNetworks().iterator().next()));
    assertThat(resHost1.getDatastores().iterator().next(), is(host1.getDatastores().iterator().next()));

    assertThat(resHost2.getNetworks().iterator().next(), is(host2.getNetworks().iterator().next()));
    assertThat(resHost2.getDatastores().iterator().next(), is(host2.getDatastores().iterator().next()));
  }

  @Test
  public void testreadSchedulersFromZk() throws Exception {
    Datastore ds1 = new Datastore("DS1", DatastoreType.SHARED_VMFS);
    Network net1 = new Network("net1", defaultNetworkType);
    datastores.add(ds1);
    networks.add(net1);
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);
    Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "adddr2", 1235);
    Host host3 = new Host("host3", new AvailabilityZone("az1"), datastores, networks, "adddr3", 1236);
    Host host4 = new Host("host4", new AvailabilityZone("az1"), datastores, networks, "adddr4", 1237);

    Scheduler sch1 = new Scheduler("sch1");
    Scheduler sch2 = new Scheduler("sch2");

    sch1.setOwner(host1);
    sch1.addHost(host1);
    sch1.addHost(host2);

    sch2.setOwner(host3);
    sch2.addHost(host3);
    sch2.addHost(host4);

    rootScheduler.addChild(sch1);
    rootScheduler.addChild(sch2);

    byte[] hostConfig1 = ChairmanServiceTest.serialize(getHostConfig(host1));
    byte[] hostConfig2 = ChairmanServiceTest.serialize(getHostConfig(host2));
    byte[] hostConfig3 = ChairmanServiceTest.serialize(getHostConfig(host3));
    byte[] hostConfig4 = ChairmanServiceTest.serialize(getHostConfig(host4));

    when(hostsDict.getKeys()).thenReturn(Arrays.asList(host1.getId(), host2.getId(), host3.getId(), host4.getId()));
    when(hostsDict.read(host1.getId())).thenReturn(hostConfig1);
    when(hostsDict.read(host2.getId())).thenReturn(hostConfig2);
    when(hostsDict.read(host3.getId())).thenReturn(hostConfig3);
    when(hostsDict.read(host4.getId())).thenReturn(hostConfig4);

    when(rolesDict.getKeys()).thenReturn(Arrays.asList(host1.getId(), host3.getId()));
    when(rolesDict.read(host1.getId())).thenReturn(serializeRoles(host1));
    when(rolesDict.read(host3.getId())).thenReturn(serializeRoles(host3));

    Map<String, Host> hosts = hierarchyUtils.readHostsFromZk();
    Map<String, Scheduler> schedulers = hierarchyUtils.readSchedulersFromZk(hosts);


    assertThat(hosts.size(), is(4));
    assertThat(schedulers.size(), is(2));

    // Check that all hosts and schedulers were read correctly
    assertThat(hosts.get(host1.getId()), is(notNullValue()));
    assertThat(hosts.get(host2.getId()), is(notNullValue()));
    assertThat(hosts.get(host3.getId()), is(notNullValue()));
    assertThat(hosts.get(host4.getId()), is(notNullValue()));

    assertThat(schedulers.get(sch1.getId()), is(notNullValue()));
    assertThat(schedulers.get(sch2.getId()), is(notNullValue()));


    Host tHost1 = hosts.get(host1.getId());
    Host tHost2 = hosts.get(host2.getId());
    Host tHost3 = hosts.get(host3.getId());
    Host tHost4 = hosts.get(host4.getId());

    assertThat(tHost1.getParentScheduler().getId(), is(host1.getParentScheduler().getId()));
    assertThat(tHost2.getParentScheduler().getId(), is(host2.getParentScheduler().getId()));
    assertThat(tHost3.getParentScheduler().getId(), is(host3.getParentScheduler().getId()));
    assertThat(tHost4.getParentScheduler().getId(), is(host4.getParentScheduler().getId()));

    Scheduler tSch1 = schedulers.get(sch1.getId());
    Scheduler tSch2 = schedulers.get(sch2.getId());

    assertThat(tSch1.getChildren().size(), is(sch1.getChildren().size()));
    assertThat(tSch2.getChildren().size(), is(sch2.getChildren().size()));
  }

  @Test
  public void testReadSchedulersFromZkIncorrectOwnerHostReference() throws Exception {
    Datastore ds1 = new Datastore("DS1", DatastoreType.SHARED_VMFS);
    Network net1 = new Network("net1", defaultNetworkType);
    datastores.add(ds1);
    networks.add(net1);
    Host host1 = new Host("hostX", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);
    Host host2 = new Host("host2", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);

    Scheduler sch1 = new Scheduler("sch1");
    sch1.setOwner(host1);

    sch1.addHost(host1);
    sch1.addHost(host2);
    rootScheduler.addChild(sch1);
    // This case can happen when a host role has been written to /roles, but then
    // the host is is removed from /hosts, but that change hasn't been persisted to
    // roles
    when(rolesDict.getKeys()).thenReturn(Arrays.asList(host1.getId()));
    when(rolesDict.read(host1.getId())).thenReturn(serializeRoles(host1));

    LinkedHashMap<String, Host> hosts = new LinkedHashMap<>();
    hosts.put(host2.getId(), host2);
    Map<String, Scheduler> schedulers = hierarchyUtils.readSchedulersFromZk(hosts);

    // Verify that the missing owner host reference is ignored and a leaf is constructed anyways
    assertThat(schedulers.size(), is(1));
    Scheduler leaf1 = schedulers.get(sch1.getId());
    assertThat(leaf1, is(notNullValue()));
    assertThat(leaf1.getOwner(), is(nullValue()));
    assertThat(leaf1.getHosts().size(), is(1));
    assertThat(leaf1.getHosts().containsKey(host2.getId()), is(true));
  }

  @Test
  public void testReadSchedulersFromZkIncorrectChildHostReference() throws Exception {
    Datastore ds1 = new Datastore("DS1", DatastoreType.SHARED_VMFS);
    Network net1 = new Network("net1", defaultNetworkType);
    datastores.add(ds1);
    networks.add(net1);
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);
    Host hostX = new Host("hostX", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);

    Scheduler sch1 = new Scheduler("sch1");
    sch1.setOwner(host1);

    sch1.addHost(host1);
    sch1.addHost(hostX);
    rootScheduler.addChild(sch1);

    byte[] hostConfig1 = ChairmanServiceTest.serialize(getHostConfig(host1));

    when(hostsDict.getKeys()).thenReturn(Arrays.asList(host1.getId()));
    when(hostsDict.read(host1.getId())).thenReturn(hostConfig1);
    // This can happen when a scheduler has a child host that doesn't
    // correspond to any hosts in /hosts
    when(rolesDict.getKeys()).thenReturn(Arrays.asList(host1.getId()));
    when(rolesDict.read(host1.getId())).thenReturn(serializeRoles(host1));

    Map<String, Host> hosts = hierarchyUtils.readHostsFromZk();
    Map<String, Scheduler> schedulers = hierarchyUtils.readSchedulersFromZk(hosts);

    // Verify that the missing host reference is ignored and a leaf is constructed anyways
    assertThat(schedulers.size(), is(1));
    Scheduler leaf1 = schedulers.get(sch1.getId());
    assertThat(leaf1, is(notNullValue()));
    assertThat(leaf1.getOwner().getId(), is(host1.getId()));
    assertThat(leaf1.getHosts().size(), is(1));
    assertThat(leaf1.getHosts().containsKey(host1.getId()), is(true));
  }

  @Test
  public void testGetDatastoreWithTags() {
    Datastore ds1 = new Datastore("DS1", DatastoreType.SHARED_VMFS);
    datastores.add(ds1);
    Host host1 = new Host("host1", new AvailabilityZone("az1"), datastores, networks, "adddr1", 1234);
    Set<String> tags = new HashSet();
    tags.add("tag1");

    HostConfig config = getHostConfig(host1);
    // Add a tag to the datastore
    config.getDatastores().iterator().next().setTags(tags);
    Set<Datastore> datastores = hierarchyUtils.getDatastores(config);

    // Verify that the tags were parsed correctly
    Datastore parsedDs = datastores.iterator().next();
    assertThat(parsedDs.getTags(), is(tags));
  }

  private HostConfig getHostConfig(Host host) {
    RegisterHostRequest req = ChairmanServiceTest.createRegReq(host.getDatastores(), host.getNetworks(), "DS1");
    req.getConfig().setAgent_id(host.getId());
    return req.getConfig();
  }

  private byte[] serializeRoles(Host host) throws Exception {
    ConfigureRequest req = HierarchyUtils.getConfigureRequest(host);
    TSerializer serializer = new TSerializer();
    byte[] data = serializer.serialize(req.getRoles());
    return data;
  }
}
