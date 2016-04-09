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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.rootscheduler.ConfigTest;
import com.vmware.photon.controller.rootscheduler.xenon.SchedulerXenonHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/**
 * Tests the HostCache.
 */
public class HostCacheTest {

  private static final String configFilePath = "/config.yml";
  private static final Logger logger = LoggerFactory.getLogger(HostCacheTest.class);

  /**
   * Configuration for testHostCache(). Note that these can be configured via
   * properties. For instance, you can do start the JVM with
   * -Dtest.numDataStores=10 to use 10 datastores
   */
  private int numDatastores = 2;
  private int numManagementHosts = 2;
  private int numCloudHosts = 10;

  private static final String NUM_DATASTORES_PROPERTY = "test.numDataStores";
  private static final String NUM_MANAGEMENT_HOSTS_PROPERTY = "test.numManagementHosts";
  private static final String NUM_CLOUD_HOSTS_PROPERTY = "test.numCloudHosts";

  private TestEnvironment cloudStoreTestEnvironment;
  private XenonRestClient cloudstoreClient;
  private SchedulerXenonHost schedulerHost;

  @BeforeClass
  public void setUpClass() throws Throwable {
    parseProperties();
    startCloudstore();
    startSchedulerHost();
  }

  /**
   * Looks for well-known properties to configure the tests.
   */
  private void parseProperties() {
    Properties properties = System.getProperties();
    for (String name : properties.stringPropertyNames()) {
      if (name.equals(NUM_DATASTORES_PROPERTY)) {
        this.numDatastores = Integer.parseInt(properties.getProperty(NUM_DATASTORES_PROPERTY));
      } else if (name.equals(NUM_MANAGEMENT_HOSTS_PROPERTY)) {
        this.numManagementHosts = Integer.parseInt(properties.getProperty(NUM_MANAGEMENT_HOSTS_PROPERTY));
      } else if (name.equals(NUM_CLOUD_HOSTS_PROPERTY)) {
        this.numCloudHosts = Integer.parseInt(properties.getProperty(NUM_CLOUD_HOSTS_PROPERTY));
      }
    }
  }

  private void startCloudstore() throws Throwable {
    this.cloudStoreTestEnvironment = TestEnvironment.create(1);
    this.cloudstoreClient =
        new XenonRestClient(cloudStoreTestEnvironment.getServerSet(), Executors.newFixedThreadPool(1));
    cloudstoreClient.start();
  }

  private void startSchedulerHost() throws Throwable {
    // We don't use Guice here. We could, but we're moving gradually away from
    // Guice in the scheduler,
    // so we're not adding extra usages of it until necessary.
    Config config = ConfigBuilder.build(Config.class, ConfigTest.class.getResource(configFilePath).getPath());
    this.schedulerHost = new SchedulerXenonHost(config.getXenonConfig(), () -> null, config, null, null);
    this.schedulerHost.start();
    while (!this.schedulerHost.isReady()) {
      Thread.sleep(100);
    }
  }

  @AfterClass
  public void tearDownClass() throws Throwable {
    if (cloudStoreTestEnvironment != null) {
      this.cloudStoreTestEnvironment.stop();
      this.cloudStoreTestEnvironment = null;
    }

    if (this.schedulerHost != null) {
      this.schedulerHost.stop();
      this.schedulerHost = null;
    }
  }

  //@Test()
  // Disabling test due to bug that will take a few days to track down:
  // https://www.pivotaltracker.com/projects/1523359/stories/113849353
  public void testHostCache() throws Throwable {
    List<DatastoreService.State> datastores = createDatastoreDescriptions(this.numDatastores);
    List<HostService.State> managementHosts = createHostDescriptions(this.numManagementHosts, true, datastores);
    List<HostService.State> cloudHosts = createHostDescriptions(this.numCloudHosts, false, datastores);

    logger.info("Making {} datastores...", this.numDatastores);
    createDatastores(datastores);

    logger.info("Making {} management hosts...", this.numManagementHosts);
    createHosts(managementHosts);

    logger.info("Making {} cloud hosts...", this.numCloudHosts);
    createHosts(cloudHosts);

    HostCache hostCache = new HostCache(schedulerHost, cloudstoreClient);

    logger.info("Waiting for host cache to populate...");
    pollForHostCache(hostCache, this.numManagementHosts, this.numCloudHosts, this.numDatastores);
    validateNetworks(hostCache, managementHosts);
    validateNetworks(hostCache, cloudHosts);
    validateDatastores(hostCache, managementHosts);
    validateDatastores(hostCache, cloudHosts);
    validateDatastoreTags(hostCache, managementHosts, datastores);
    validateDatastoreTags(hostCache, cloudHosts, datastores);
    validateZones(hostCache, managementHosts);
    validateZones(hostCache, cloudHosts);

    deleteDatastores(datastores);
    deleteHosts(managementHosts);
    deleteHosts(cloudHosts);

    // Again, we need to poll while the host cache updates
    logger.info("Waiting for host cache to depopulate...");
    pollForHostCache(hostCache, 0, 0, 0);
    assertThat(hostCache.getHostCount(), equalTo(0));
    assertThat(hostCache.getManagementHostCount(), equalTo(0));
    assertThat(hostCache.getDatastoreCount(), equalTo(0));
    validateCleanHosts(hostCache, managementHosts);
    validateCleanHosts(hostCache, cloudHosts);
    validateCleanDatastores(hostCache, datastores);
  }

  /**
   * Create the descriptions for the datastores that we'll be posting to
   * Cloudstore.
   */
  private List<DatastoreService.State> createDatastoreDescriptions(int numDatastores) {
    List<DatastoreService.State> datastores = new ArrayList<>();

    for (int i = 0; i < numDatastores; i++) {
      String datastoreName = "datastore-" + i;
      String datastoreTag = "tag-" + i;

      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = datastoreName;
      datastore.name = datastoreName;
      datastore.type = "SHARED_VMFS";
      datastore.tags = new HashSet<>(Arrays.asList(datastoreTag));
      datastore.documentSelfLink = datastoreName;
      datastores.add(datastore);
    }
    return datastores;
  }

  /**
   * Create the descriptions for the hosts that we'll be posting to Cloudstore.
   */
  private List<HostService.State> createHostDescriptions(
      int numHosts,
      boolean isManagement,
      List<DatastoreService.State> datastores) throws Throwable {
    InetAddress address = InetAddresses.forString("1.1.1.0");
    List<HostService.State> hosts = new ArrayList<>();
    int numDatastores = datastores.size();

    for (int i = 0; i < numHosts; i++) {
      address = InetAddresses.increment(address);
      String hostName;
      String nwName = "network-" + i;
      String azName = "zone-" + i;

      if (isManagement) {
        hostName = "mgmt-host-" + i;
      } else {
        hostName = "cloud-host-" + i;
      }

      HostService.State host = new HostService.State();
      host.hostAddress = address.toString();
      host.userName = "username";
      host.password = "password";
      host.state = HostState.READY;
      host.reportedDatastores = new HashSet<>(Arrays.asList(datastores.get(i % numDatastores).name));
      host.reportedNetworks = new HashSet<>(Arrays.asList(nwName));
      host.availabilityZoneId = azName;
      host.metadata = new HashMap<>();
      populateHostMetadata(host, isManagement);
      if (isManagement) {
        host.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name()));
      } else {
        host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
      }
      host.documentSelfLink = hostName;
      hosts.add(host);
    }
    return hosts;
  }

  /**
   * Helper for createHostDescriptions.
   */
  private void populateHostMetadata(HostService.State host, boolean isManagement) {
    host.metadata = new HashMap<>();
    if (isManagement) {
      // We don't need the metadata for our test, but it's required by the
      // HostService
      host.metadata.put("MANAGEMENT_DATASTORE", "ds1");
      host.metadata.put("MANAGEMENT_NETWORK_DNS_SERVER", "dns");
      host.metadata.put("MANAGEMENT_NETWORK_GATEWAY", "gateway");
      host.metadata.put("MANAGEMENT_NETWORK_IP", "ip");
      host.metadata.put("MANAGEMENT_NETWORK_NETMASK", "mask");
      host.metadata.put("MANAGEMENT_PORTGROUP", "pg1");
    }
  }

  /**
   * Given the host descriptions created by createHostDescriptions, post them to
   * Cloudstore.
   */
  private void createHosts(List<HostService.State> hosts) throws Throwable {
    for (HostService.State host : hosts) {
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK, host);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the datastore descriptions created by createDatastoreDescriptions,
   * post them to Cloudstore.
   */
  private void createDatastores(List<DatastoreService.State> datastores) throws Throwable {
    for (DatastoreService.State datastore : datastores) {
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, datastore);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the host descriptions created by createHostDescriptions, delete them
   * to from Cloudstore.
   */
  private void deleteHosts(List<HostService.State> hosts) throws Throwable {
    for (HostService.State host : hosts) {
      String hostUri = UriUtils.buildUriPath(HostServiceFactory.SELF_LINK, host.documentSelfLink);
      Operation result = cloudStoreTestEnvironment.sendDeleteAndWait(hostUri);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the datastore descriptions created by createDatastoreDescriptions,
   * delete them from Cloudstore.
   */
  private void deleteDatastores(List<DatastoreService.State> datastores) throws Throwable {
    for (DatastoreService.State datastore : datastores) {
      String hostUri = UriUtils.buildUriPath(DatastoreServiceFactory.SELF_LINK, datastore.documentSelfLink);
      Operation result = cloudStoreTestEnvironment.sendDeleteAndWait(hostUri);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * We have to poll for changes to the HostCache since it is updated
   * asynchronously.
   */
  private void pollForHostCache(HostCache hostCache, int mgmtHostCount, int cloudHostCount, int datastoreCount)
      throws Throwable {

    int totalHostCount = mgmtHostCount + cloudHostCount;
    for (int i = 0; i < 20; i++) {
      if (hostCache.getHostCount() == totalHostCount && hostCache.getManagementHostCount() == mgmtHostCount
          && hostCache.getDatastoreCount() == datastoreCount) {
        break;
      }
      Thread.sleep(500);
    }

    assertThat(String.format("Expected %d hosts, found %d hosts", totalHostCount, hostCache.getHostCount()),
        hostCache.getHostCount(), equalTo(totalHostCount));
    assertThat(String.format("Expected %d management hosts, found %d management hosts", this.numManagementHosts,
            hostCache.getManagementHostCount()),
        hostCache.getManagementHostCount(), equalTo(mgmtHostCount));
    assertThat(String.format("Expected %d datastores, found %d datastores", this.numDatastores,
            hostCache.getDatastoreCount()),
        hostCache.getDatastoreCount(), equalTo(datastoreCount));
  }

  /**
   * Helper method that verifies a predicate by retrying a few times in case the predicate returns false.
   */
  private void pollForState(BooleanSupplier predicate) {
    for (int i = 0; i < 10; i++) {
      if (predicate.getAsBoolean()) {
        break;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException ex) {
        ; // Don't care
      }
    }
  }

  /**
   * Validate that the cache properly associates datastore IDs with the hosts.
   */
  private void validateDatastores(HostCache hostCache, List<HostService.State> hosts) {
    for (HostService.State host : hosts) {
      String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);
      for (String datastoreId : host.reportedDatastores) {
        pollForState(() -> hostCache.getHostsWithDatastore(datastoreId).contains(hostId));
        assertThat(
            String.format("Host %s with datastore %s not in datastore map", hostId, datastoreId),
            hostCache.getHostsWithDatastore(datastoreId),
            hasItem(hostId));
      }
    }
  }

  /**
   * Validate that the cache properly associates datastore tags with the hosts.
   */
  private void validateDatastoreTags(
      HostCache hostCache,
      List<HostService.State> hosts,
      List<DatastoreService.State> datastores) {
    for (HostService.State host : hosts) {
      String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);
      for (String datastoreId : host.reportedDatastores) {
        boolean foundDatastore = false;
        for (DatastoreService.State datastore : datastores) {
          if (datastoreId.equals(datastore.id)) {
            // Validate all tags in this datastore are associated with this host
            foundDatastore = true;
            for (String datastoreTag : datastore.tags) {
              pollForState(() -> hostCache.getHostsWithDatastoreTag(datastoreTag).contains(hostId));
              assertThat(hostCache.getHostsWithDatastoreTag(datastoreTag), hasItem(hostId));
            }
          }
        }
        assertThat(foundDatastore, equalTo(true));
      }
    }
  }

  /**
   * Validate that the cache properly associates network IDs with the hosts.
   */
  private void validateNetworks(HostCache hostCache, List<HostService.State> hosts) {
    for (HostService.State host : hosts) {
      String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);
      for (String networkId : host.reportedNetworks) {
        pollForState(() -> hostCache.getHostsOnNetwork(networkId).contains(hostId));
        assertThat(
            String.format("Host %s with network %s not in network map", hostId, networkId),
            hostCache.getHostsOnNetwork(networkId),
            hasItem(hostId));
      }
    }
  }

  /**
   * Validate that the cache properly associates availability zones with the
   * hosts.
   */
  private void validateZones(HostCache hostCache, List<HostService.State> hosts) {
    for (HostService.State host : hosts) {
      String hostId = ServiceUtils.getIDFromDocumentSelfLink(host.documentSelfLink);
      pollForState(() -> hostCache.getHostsInZone(host.availabilityZoneId).contains(hostId));
      assertThat(
          String.format("Host %s with zone %s not in zone map", hostId, host.availabilityZoneId),
          hostCache.getHostsInZone(host.availabilityZoneId),
          hasItem(hostId));
    }
  }

  /**
   * Validate that the cache no longer knows about the given hosts.
   */
  private void validateCleanHosts(HostCache hostCache, List<HostService.State> hosts) {
    for (HostService.State host : hosts) {
      pollForState(() -> hostCache.getHostsInZone(host.availabilityZoneId).isEmpty());
      assertThat(hostCache.getHostsInZone(host.availabilityZoneId), empty());
      for (String networkId : host.reportedNetworks) {
        pollForState(() -> hostCache.getHostsOnNetwork(networkId).isEmpty());
        assertThat(hostCache.getHostsOnNetwork(networkId), empty());
      }
    }
  }

  /**
   * Validate that the cache no longer knows about the given datastores.
   */
  private void validateCleanDatastores(HostCache hostCache, List<DatastoreService.State> datastores) {
    for (DatastoreService.State datastore : datastores) {
      pollForState(() -> hostCache.getHostsWithDatastore(datastore.id).isEmpty());
      assertThat(hostCache.getHostsWithDatastore(datastore.id), empty());
      if (datastore.tags == null) {
        continue;
      }
      for (String tag : datastore.tags) {
        pollForState(() -> hostCache.getHostsWithDatastoreTag(tag).isEmpty());
        assertThat(hostCache.getHostsWithDatastoreTag(tag), empty());
      }
    }
  }
}
