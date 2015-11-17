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

package com.vmware.photon.controller.cloudstore.dcp.monitors;

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestHelper;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.zookeeper.HostChangeListener;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.photon.controller.resource.gen.NetworkType;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;


/**
 * Tests CloudStoreMonitor triggers.
 */

public class CloudStoreMonitorTest {

  private TestEnvironment cloudStoreMachine;
  private DcpRestClient client;
  private int scanPeriod = 1000;
  private CloudStoreMonitor monitor;
  private String defaultDatastoreType = "SHARED_VMFS";

  @BeforeMethod
  public void setUpTest() throws Throwable {
    cloudStoreMachine = TestEnvironment.create(1);
    client = new DcpRestClient(cloudStoreMachine.getServerSet(), Executors.newFixedThreadPool(1));
    client.start();
    monitor = new CloudStoreMonitor(client, Executors.newSingleThreadScheduledExecutor(),
        scanPeriod);
  }

  @AfterMethod
  public void tearDownTest() throws Throwable {
    if (null != cloudStoreMachine) {
      cloudStoreMachine.stop();
      cloudStoreMachine = null;
    }

    if (null != client) {
      client.stop();
      client = null;
    }
  }

  @Test
  public void testCloudStoreMonitor() throws Throwable {

    HostService.State host1 = TestHelper.getHostServiceStartState();
    Set<String> networks = new HashSet();
    networks.add("Net1");
    host1.reportedNetworks = networks;
    Set<String> datastores = new HashSet();
    String datastore1Id = "datastore1";
    datastores.add(datastore1Id);
    host1.reportedDatastores = datastores;

    for (String ds : datastores) {
      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = ds;
      datastore.name = ds;
      datastore.type = defaultDatastoreType;
      datastore.documentSelfLink = getDatastoreUri(ds);

      client.post(DatastoreServiceFactory.SELF_LINK, datastore);
    }

    List<HostConfig> addedHosts = new ArrayList();
    List<HostConfig> deletedHosts = new ArrayList();
    List<String> missingHosts = new ArrayList();

    monitor.addChangeListener(new HostChangeListener() {

      public void onHostAdded(String id, HostConfig hostConfig) {
        addedHosts.add(hostConfig);
      }

      public void onHostRemoved(String id, HostConfig hostConfig) {
        deletedHosts.add(hostConfig);
      }

      public void onHostUpdated(String id, HostConfig hostConfig) {
        // no op
      }

      public void hostMissing(String id) {
        missingHosts.add(id);
      }
    });

    // Create host
    host1.agentState = AgentState.ACTIVE;
    String hostId = UUID.randomUUID().toString();
    host1.documentSelfLink = getHostResUri(hostId);
    client.post(HostServiceFactory.SELF_LINK, host1);

    monitor.refreshCache();

    HostConfig config = getHostConfig(host1);
    assertThat(addedHosts.size(), is(1));
    assertThat(addedHosts.get(0), is(config));
    assertThat(deletedHosts.size(), is(0));
    assertThat(missingHosts.size(), is(0));

    // Update host. Adding a new datastore
    DatastoreService.State datastore = new DatastoreService.State();
    String newDs = "newds";
    datastore.id = newDs;
    datastore.name = newDs;
    datastore.type = "SHARED_VMFS";
    datastore.documentSelfLink = getDatastoreUri(newDs);
    client.post(DatastoreServiceFactory.SELF_LINK, datastore);

    HostService.State hostUpdate = new HostService.State();
    hostUpdate.reportedDatastores = datastores;
    hostUpdate.reportedDatastores.add(newDs);
    client.patch(host1.documentSelfLink, hostUpdate);

    monitor.refreshCache();

    Datastore tDatastore = new Datastore();
    tDatastore.setName(datastore.name);
    tDatastore.setId(datastore.id);
    tDatastore.setType(DatastoreType.valueOf(datastore.type));
    config.addToDatastores(tDatastore);

    assertThat(deletedHosts.size(), is(0));
    assertThat(addedHosts.size(), is(2));
    Set<Datastore> newSet = new HashSet(addedHosts.get(1).getDatastores());
    Set<Datastore> expectedSet = new HashSet(config.getDatastores());
    assertThat(newSet, is(expectedSet));
    assertThat(missingHosts.size(), is(0));

    // Mark host as missing
    HostService.State hostMissing = new HostService.State();
    hostMissing.agentState = AgentState.MISSING;
    client.patch(host1.documentSelfLink, hostMissing);

    monitor.refreshCache();

    assertThat(addedHosts.size(), is(2));
    assertThat(deletedHosts.size(), is(0));
    assertThat(missingHosts.size(), is(1));
    assertThat(missingHosts, contains(config.getAgent_id()));

    // Mark host resurrected
    HostService.State hostResurrected = new HostService.State();
    hostResurrected.agentState = AgentState.ACTIVE;
    client.patch(host1.documentSelfLink, hostResurrected);

    monitor.refreshCache();

    assertThat(addedHosts.size(), is(3));
    assertThat(deletedHosts.size(), is(0));
    assertThat(addedHosts.get(2).getAgent_id(), is(config.getAgent_id()));
    assertThat(missingHosts.size(), is(1));

    // Remove host
    client.delete(host1.documentSelfLink, null);
    monitor.refreshCache();

    assertThat(addedHosts.size(), is(3));
    assertThat(deletedHosts.size(), is(1));
    assertThat(missingHosts.size(), is(1));
  }

  private String getHostResUri(String id) {
    return HostServiceFactory.SELF_LINK + "/" + id;
  }

  private String getDatastoreUri(String id) {
    return DatastoreServiceFactory.SELF_LINK + "/" + id;
  }

  private HostConfig getHostConfig(HostService.State state) {
    HostConfig config = new HostConfig();
    String[] paths = state.documentSelfLink.split("/");
    config.setAgent_id(paths[paths.length - 1]);
    config.setAvailability_zone(CloudStoreMonitor.DEFAULT_AVAILABILITY_ZONE);
    config.setAddress(new ServerAddress(state.hostAddress, CloudStoreMonitor.DEFAULT_AGENT_PORT));

    for (String dsId : state.reportedDatastores) {
      Datastore ds = new Datastore();
      ds.setId(dsId);
      ds.setName(dsId);
      ds.setType(DatastoreType.valueOf(defaultDatastoreType));
      config.addToDatastores(ds);
    }

    for (String networkId : state.reportedNetworks) {
      Network net = new Network(networkId);
      net.addToTypes(NetworkType.VM);
      config.addToNetworks(net);
    }
    return config;
  }
}
