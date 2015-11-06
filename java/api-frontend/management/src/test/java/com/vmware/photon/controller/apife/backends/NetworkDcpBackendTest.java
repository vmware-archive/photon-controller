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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.Network;
import com.vmware.photon.controller.api.NetworkCreateSpec;
import com.vmware.photon.controller.api.NetworkState;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.db.HibernateTestModule;
import com.vmware.photon.controller.apife.db.dao.BaseDaoTest;
import com.vmware.photon.controller.apife.entities.NetworkEntity;
import com.vmware.photon.controller.apife.entities.TaskEntity;
import com.vmware.photon.controller.apife.entities.TombstoneEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidNetworkStateException;
import com.vmware.photon.controller.apife.exceptions.external.NetworkNotFoundException;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupsAlreadyAddedToNetworkException;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkService;
import com.vmware.photon.controller.cloudstore.dcp.entity.NetworkServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link NetworkDcpBackend}.
 */
public class NetworkDcpBackendTest {

  private static NetworkCreateSpec createNetworkCreateSpec() {
    NetworkCreateSpec spec = new NetworkCreateSpec();
    spec.setName("network1");
    spec.setDescription("VM VLAN");
    List<String> portGroups = new ArrayList<>();
    portGroups.add("PG1");
    portGroups.add("PG2");
    spec.setPortGroups(portGroups);
    return spec;
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link NetworkDcpBackend#createNetwork(NetworkCreateSpec)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class CreateNetworkTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testCreateNetworkSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity taskEntity = networkBackend.createNetwork(spec);

      String documentSelfLink = NetworkServiceFactory.SELF_LINK + "/" + taskEntity.getEntityId();

      NetworkService.State savedState = dcpClient.getAndWait(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.name, is(spec.getName()));
      assertThat(savedState.description, is(spec.getDescription()));
      assertThat(savedState.state, is(NetworkState.READY));
      assertThat(savedState.portGroups, is(spec.getPortGroups()));
    }

    @Test
    public void testCreateWithSameName() throws Exception {
      NetworkCreateSpec spec1 = createNetworkCreateSpec();
      networkBackend.createNetwork(spec1);
      NetworkCreateSpec spec2 = createNetworkCreateSpec();
      spec2.setPortGroups(new ArrayList<>());
      networkBackend.createNetwork(spec2);

      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(spec1.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(2));
    }

    @Test
    public void testPortGroupAlreadyAddedToNetworkException() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      networkBackend.createNetwork(spec);

      try {
        networkBackend.createNetwork(spec);
        fail("create network should fail");
      } catch (PortGroupsAlreadyAddedToNetworkException ex) {
        assertThat(ex.getMessage(), containsString("Port group PG1 is already added to network Network{id="));
        assertThat(ex.getMessage(), containsString("Port group PG2 is already added to network Network{id="));
      }
    }
  }

  /**
   * Tests {@link NetworkDcpBackend#filter(com.google.common.base.Optional, com.google.common.base.Optional)}}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class FilterTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testFilterNetworks() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      networkBackend.createNetwork(spec);

      List<Network> networks = networkBackend.filter(
          Optional.of(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));
      assertThat(networks.get(0).getName(), is(spec.getName()));
      assertThat(networks.get(0).getDescription(), is(spec.getDescription()));
      assertThat(networks.get(0).getPortGroups(), is(spec.getPortGroups()));

      networks = networkBackend.filter(Optional.of("n2"), Optional.<String>absent());
      assertThat(networks.isEmpty(), is(true));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.<String>absent());
      assertThat(networks.size(), is(1));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.of("PG1"));
      assertThat(networks.size(), is(1));

      networks = networkBackend.filter(Optional.<String>absent(), Optional.of("foo"));
      assertThat(networks.isEmpty(), is(true));

      networks = networkBackend.filter(Optional.of(spec.getName()), Optional.of("PG2"));
      assertThat(networks.size(), is(1));
    }
  }

  /**
   * Tests {@link NetworkDcpBackend#toApiRepresentation(NetworkEntity)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class ToApiRepresentationTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testToApiRepresentation() throws Exception {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      String networkId = task.getEntityId();

      Network network = networkBackend.toApiRepresentation(networkId);
      assertThat(network.getId(), is(networkId));
      assertThat(network.getName(), is(spec.getName()));
      assertThat(network.getDescription(), is(spec.getDescription()));
      assertThat(network.getPortGroups(), is(spec.getPortGroups()));
    }
  }

  /**
   * Tests {@link NetworkDcpBackend#prepareNetworkDelete(String)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class DeleteNetworkTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Mock
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      doReturn(new LinkedList<>()).when(vmBackend).filterByNetwork(any());

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));

      String networkId = task.getEntityId();

      networkBackend.prepareNetworkDelete(networkId);
      networks = networkBackend.filter(
          Optional.fromNullable(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(0));
    }

    @Test
    public void testWhenVmsAreAttached() throws Throwable {
      // mimic Vms existing on the network
      doReturn(ImmutableList.of(new VmEntity())).when(vmBackend).filterByNetwork(any());

      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));

      String networkId = task.getEntityId();

      networkBackend.prepareNetworkDelete(networkId);
      networks = networkBackend.filter(
          Optional.fromNullable(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));
      Network network = networks.get(0);
      assertThat(network.getId(), is(networkId));
      assertThat(network.getState(), is(NetworkState.PENDING_DELETE));
    }

    @Test(expectedExceptions = NetworkNotFoundException.class)
    public void testDeleteOfNonExistingNetwork() throws Exception {
      networkBackend.prepareNetworkDelete(UUID.randomUUID().toString());
    }

    @Test
    public void testDeletePendingDeleteNetwork() throws Exception {
      // mimic Vms existing on the network
      doReturn(ImmutableList.of(new VmEntity())).when(vmBackend).filterByNetwork(any());

      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity task = networkBackend.createNetwork(spec);
      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(spec.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));

      String networkId = task.getEntityId();

      networkBackend.prepareNetworkDelete(networkId);

      try {
        networkBackend.prepareNetworkDelete(networkId);
        fail("delete PENDING_DELETE network should fail");
      } catch (InvalidNetworkStateException e) {
        assertThat(e.getMessage(),
            is(String.format("Invalid operation to delete network %s in state PENDING_DELETE", networkId)));
      }
    }
  }

  /**
   * Tests {@link NetworkDcpBackend#tombstone(NetworkEntity)}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class TombstoneTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    private NetworkEntity entity;

    @Inject
    private TaskBackend taskBackend;

    @Mock
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      doReturn(new LinkedList<>()).when(vmBackend).filterByNetwork(any());

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS, BasicServiceHost.BIND_PORT,
          null, NetworkServiceFactory.SELF_LINK, 10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));
      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);

      TaskEntity task = networkBackend.createNetwork(createNetworkCreateSpec());
      entity = networkBackend.findById(task.getEntityId());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      networkBackend.tombstone(entity);

      TombstoneEntity tombstone = tombstoneBackend.getByEntityId(entity.getId());
      assertThat(tombstone.getEntityId(), is(entity.getId()));
      assertThat(tombstone.getEntityKind(), is(Network.KIND));

      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(entity.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(0));
    }

    @Test
    public void testWhenVmsAreAttached() throws Throwable {
      // mimic Vms existing on the network
      doReturn(ImmutableList.of(new VmEntity())).when(vmBackend).filterByNetwork(any());

      networkBackend.tombstone(entity);
      assertThat(tombstoneBackend.getByEntityId(entity.getId()), nullValue());


      List<Network> networks = networkBackend.filter(
          Optional.fromNullable(entity.getName()), Optional.<String>absent());
      assertThat(networks.size(), is(1));

      Network network = networks.get(0);
      assertThat(network.getId(), is(entity.getId()));
      assertThat(network.getState(), is(NetworkState.READY));
    }

    @Test(enabled = false)
    public void testNonExistingNetwork() throws Exception {
      NetworkEntity missingEntity = new NetworkEntity();
      missingEntity.setId(UUID.randomUUID().toString());

      networkBackend.tombstone(missingEntity);
      assertThat(tombstoneBackend.getByEntityId(missingEntity.getId()), nullValue());
    }
  }

  /**
   * Tests {@link NetworkDcpBackend#updatePortGroups(String, java.util.List)}}.
   */
  @Guice(modules = {HibernateTestModule.class, BackendTestModule.class})
  public static class UpdatePortGroupsTest extends BaseDaoTest {

    private NetworkBackend networkBackend;
    private ApiFeDcpRestClient dcpClient;

    @Inject
    private TaskBackend taskBackend;

    @Inject
    private VmBackend vmBackend;

    @Inject
    private TombstoneBackend tombstoneBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      super.setUp();

      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          NetworkServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new NetworkServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(1));

      networkBackend = new NetworkDcpBackend(dcpClient, taskBackend, vmBackend, tombstoneBackend);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      super.tearDown();

      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      NetworkCreateSpec spec = createNetworkCreateSpec();
      TaskEntity taskEntity = networkBackend.createNetwork(spec);
      String networkId = taskEntity.getEntityId();

      String documentSelfLink = NetworkServiceFactory.SELF_LINK + "/" + networkId;

      NetworkService.State savedState = dcpClient.getAndWait(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.portGroups, is(spec.getPortGroups()));

      List<String> portGroups = new ArrayList<>();
      portGroups.add("New PG1");
      networkBackend.updatePortGroups(networkId, portGroups);
      savedState = dcpClient.getAndWait(documentSelfLink).getBody(NetworkService.State.class);
      assertThat(savedState.portGroups, is(portGroups));
    }

    @Test(expectedExceptions = NetworkNotFoundException.class)
    public void testUpdateNonExistingNetwork() throws Exception {
      networkBackend.updatePortGroups(UUID.randomUUID().toString(), null);
    }
  }

}
