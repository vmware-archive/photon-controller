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

import com.vmware.photon.controller.api.AgentState;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.xenon.common.Operation;

import ch.qos.logback.classic.Level;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Test the Constraint Checkers.
 *
 * These are tests that are common to both kinds of constraint checkers. Since the CloudStoreConstraintChecker has more
 * functionality, there are tests specific to it in a separate class: CloudStoreConstraintCheckerTest.
 */
public class ConstraintCheckerTest {

  private static final Logger logger = LoggerFactory.getLogger(ConstraintCheckerTest.class);

  private TestEnvironment cloudStoreTestEnvironment;

  private Map<String, HostService.State> expectedHosts = new HashMap<>();

  private boolean testAllSelected = false;

  private static final String TEST_ALL_SELECTED_PROPERTY = "test.testAllSelected";

  /**
   * Default host data.
   *
   * - 10 hosts: host0, host1, ..., host9
   * - 1 network per host: host0 => nw0, host1 => nw1, ..., host9 => nw9
   * - 1 datastore per host: host0 => ds0, host1 => ds1, ..., host9 => ds9
   * - each host with a unique availability zone: host0 => az0, host1 => az1, ..., host8 => az8
   *   except for host9, which doesn't have the availability zone set.
   * - 1 tag per datastore: ds0 => dstag0, ds1 => dstag1, ..., ds9 => dstag9
   * - 5 management hosts host0, host2, host4, host6, host8
   */
  @BeforeClass
  public void setUpClass() throws Throwable {
    // Disable host ping: we have fake hosts and don't want them to be marked as missing
    HostService.setInUnitTests(true);
    parseProperties();

    // Decrease logging level level. This test does 60,000+ operations, causing tons
    // of log spam. Not that we will still see warnings and errors when there are problems.
    Logger xenonLogger = LoggerFactory.getLogger(XenonRestClient.class);
    ((ch.qos.logback.classic.Logger) xenonLogger).setLevel(Level.WARN);
    Logger datastoreLogger = LoggerFactory.getLogger(DatastoreService.class);
    ((ch.qos.logback.classic.Logger) datastoreLogger).setLevel(Level.WARN);
    Logger hostLogger = LoggerFactory.getLogger(HostService.class);
    ((ch.qos.logback.classic.Logger) hostLogger).setLevel(Level.WARN);

    cloudStoreTestEnvironment = TestEnvironment.create(1);
    Map<String, DatastoreService.State> datastores = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      String hostName = "host" + i;
      String dsName = "ds" + i;
      String dsTag = "dstag" + i;
      String nwName = "nw" + i;
      String azName = null;
      if (i != 9) {
        azName = "az" + i;
      }
      HostService.State host = new HostService.State();
      host.hostAddress = hostName;
      host.userName = "username";
      host.password = "password";
      host.state = HostState.READY;
      host.agentState = AgentState.ACTIVE;
      host.reportedDatastores = new HashSet<>(Arrays.asList(dsName));
      host.reportedNetworks = new HashSet<>(Arrays.asList(nwName));
      host.availabilityZoneId = azName;
      host.metadata = new HashMap<>();
      // We dramatically increase the delay before we ping
      // We don't want pings to fail (since these are fake hosts) and break
      // our test since we only use valid hosts.
      host.triggerIntervalMillis = new Long(1024 * 1000);
      if (i % 2 == 0) {
        host.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name()));
        host.metadata.put("MANAGEMENT_DATASTORE", "ds1");
        host.metadata.put("MANAGEMENT_NETWORK_DNS_SERVER", "dns");
        host.metadata.put("MANAGEMENT_NETWORK_GATEWAY", "gateway");
        host.metadata.put("MANAGEMENT_NETWORK_IP", "ip");
        host.metadata.put("MANAGEMENT_NETWORK_NETMASK", "mask");
        host.metadata.put("MANAGEMENT_PORTGROUP", "pg1");
      } else {
        host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
      }
      expectedHosts.put(hostName, host);

      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = dsName;
      datastore.name = dsName;
      datastore.type = "SHARED_VMFS";
      datastore.tags = new HashSet<>(Arrays.asList(dsTag));
      datastores.put(dsName, datastore);
    }

    logger.info("Creating datastores...");
    for (Map.Entry<String, DatastoreService.State> entry : datastores.entrySet()) {
      DatastoreService.State initialState = entry.getValue();
      initialState.documentSelfLink = entry.getKey();
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, initialState);
      assertThat(result.getStatusCode(), is(200));
    }

    logger.info("Creating hosts...");
    for (Map.Entry<String, HostService.State> entry : expectedHosts.entrySet()) {
      HostService.State initialState = entry.getValue();
      initialState.documentSelfLink = entry.getKey();
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK, initialState);
      assertThat(result.getStatusCode(), is(200));
    }
  }

  /**
   * Looks for well-known properties to configure the tests.
   */
  private void parseProperties() {
    Properties properties = System.getProperties();
    for (String name : properties.stringPropertyNames()) {
      if (name.equals(TEST_ALL_SELECTED_PROPERTY)) {
        this.testAllSelected = Boolean.parseBoolean(properties.getProperty(TEST_ALL_SELECTED_PROPERTY));
      }
    }
  }

  @AfterClass
  public void tearDownClass() throws Throwable {
    if (null != cloudStoreTestEnvironment) {
      cloudStoreTestEnvironment.stop();
      cloudStoreTestEnvironment = null;
    }
  }

  @DataProvider(name = "default")
  public Object[][] createDefault() {
    XenonRestClient xenonRestClient = new XenonRestClient(
        cloudStoreTestEnvironment.getServerSet(), Executors.newFixedThreadPool(1));
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreTestEnvironment.getServerSet());
    // This tests does tens of thousands of operation. We only log failures, so we can see what's happening.
    xenonRestClient.start();
    return new Object[][]{
        { new CloudStoreConstraintChecker(cloudStoreHelper) },
    };
  }

  @Test(dataProvider = "default")
  public void testDefault(ConstraintChecker checker) {
    logger.info("Testing that {} can find hosts...", checker.getClass().getSimpleName());
    Set<String> hosts = getManagementHosts(checker, 5);
    assertThat(hosts, containsInAnyOrder("host0", "host2", "host4", "host6", "host8"));

    hosts = getHosts(checker, 5);
    assertThat(hosts, containsInAnyOrder("host1", "host3", "host5", "host7", "host9"));

    // In the test below we expect the host that is currently being looped over gets picked by the scheduler based on
    // the constraint. Since we pick only cloud hosts for VM placement, we loop over only the odd numbered hosts
    // which are cloud hosts.
    for (int i = 1; i < expectedHosts.size(); i += 2) {
      String hostName = "host" + i;
      String dsName = "ds" + i;
      String dsTag = "dstag" + i;
      String nwName = "nw" + i;
      String azName = "az" + i;
      hosts = getHostsWithDatastore(checker, dsName, expectedHosts.size());
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = getHostsWithDatastoreTag(checker, dsTag, expectedHosts.size());
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = getHostsWithNetwork(checker, nwName, expectedHosts.size());
      assertThat(hosts, containsInAnyOrder(hostName));
      if (i != 9) {
        hosts = getHostsInAvailabilityZone(checker, azName, expectedHosts.size());
        assertThat(hosts, containsInAnyOrder(hostName));
        hosts = getHostsNotInAvailabilityZone(checker, azName, expectedHosts.size());
        assertEquals(hosts,
            Sets.filter(getCloudHosts().keySet(), Predicates.not(Predicates.equalTo(hostName))));
      } else {
        hosts = getHostsInAvailabilityZone(checker, azName, expectedHosts.size());
        assertThat(hosts.size(), is(0));
        hosts = getHostsNotInAvailabilityZone(checker, azName, expectedHosts.size());
        assertEquals(hosts, getCloudHosts().keySet());
      }
    }
  }
  @Test(dataProvider = "default")
  public void testSingleConstraint(ConstraintChecker checker) {
    logger.info("Testing that {} can find candidates for single constraints...", checker.getClass().getSimpleName());
    Map<String, ServerAddress> allHosts = checker.getCandidatesSync(Collections.emptyList(), expectedHosts.size());
    assertEquals(allHosts.keySet(), getCloudHosts().keySet());
    for (Map.Entry<String, ServerAddress> entry: allHosts.entrySet()) {
      assertThat(entry.getKey(), is(entry.getValue().getHost()));
      assertThat(entry.getValue().getPort(), is(ConstraintChecker.DEFAULT_AGENT_PORT));
    }

    // In the test below we expect the host that is currently being looped over gets picked by the scheduler based on
    // the constraint. Since we pick only cloud hosts for VM placement, we loop over only the odd numbered hosts
    // which are cloud hosts.
    for (int i = 1; i < expectedHosts.size(); i += 2) {
      String hostName = "host" + i;
      ServerAddress address = new ServerAddress(hostName, ConstraintChecker.DEFAULT_AGENT_PORT);

      // host
      ResourceConstraint constraint = new ResourceConstraint(ResourceConstraintType.HOST, Arrays.asList(hostName));
      List<ResourceConstraint> constraints = new LinkedList<>();
      constraints.add(constraint);
      Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, 10);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      constraint = new ResourceConstraint(ResourceConstraintType.HOST, Arrays.asList(hostName));
      constraint.setNegative(true);
      constraints = new LinkedList<>();
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, expectedHosts.size());
      assertThat(candidates.size(), is(getCloudHosts().size() - 1));
      assertEquals(candidates,
          Maps.filterKeys(candidates, Predicates.not(Predicates.equalTo(hostName))));

      // datastore
      String dsName = "ds" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList(dsName));
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // datastore tag
      String dsTag = "dstag" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, Arrays.asList(dsTag));
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // network
      String nwName = "nw" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.NETWORK, Arrays.asList(nwName));
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // availability zone
      String azName = "az" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(azName));
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, 2);
      if (i != 9) {
        assertEquals(candidates, ImmutableMap.of(hostName, address));
      } else {
        assertThat(candidates.size(), is(0));
      }
      // negative availability zone
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(azName));
      constraint.setNegative(true);
      constraints.add(constraint);
      candidates = checker.getCandidatesSync(constraints, expectedHosts.size());
      if (i != 9) {
        assertThat(candidates.size(), is(getCloudHosts().size() - 1));
        assertEquals(candidates,
            Maps.filterKeys(candidates, Predicates.not(Predicates.equalTo(hostName))));
      } else {
        assertThat(candidates.size(), is(getCloudHosts().size()));
        assertThat(candidates.keySet(), is(getCloudHosts().keySet()));
      }
    }
  }

  @Test(dataProvider = "default")
  public void testNoConstraint(ConstraintChecker checker) {
    // expect to get all the hosts without any constraint.
    logger.info("Testing that {} can find all candidates...", checker.getClass().getSimpleName());
    Map<String, ServerAddress> allHosts = checker.getCandidatesSync(Collections.emptyList(), expectedHosts.size());
    logger.info("Testing that {} can find 10 candidates...", checker.getClass().getSimpleName());
    List<ResourceConstraint> constraints = new LinkedList<>();
    Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, 10);
    assertEquals(candidates, allHosts);

    // verify that the candidates get picked randomly by picking a single candidate many
    // times and verifying that everybody gets picked. This is not deterministic, so we only
    // run this test when requested
    if (this.testAllSelected) {
      logger.info("Testing {} that all hosts are selected (non-deterministic)", checker.getClass().getSimpleName());

      Boolean[] selectedHost = new Boolean[10];
      boolean foundAll = false;
      int numAttempts = 0;
      Arrays.fill(selectedHost,  false);
      for (int i = 0; i < 10000; i++) {
        Map<String, ServerAddress> candidate = checker.getCandidatesSync(constraints, 1);
        assertThat(candidate.size(), is(1));
        // Extract the host index from the name, which is the string "hostN", where N is the number of the host.
        for (String hostname : candidate.keySet()) {
          int hostIndex = Integer.parseInt(hostname.substring(4));
          selectedHost[hostIndex] = true;
        }
        if (Arrays.stream(selectedHost).allMatch(selected -> selected == true)) {
          foundAll = true;
          numAttempts = i + 1;
          break;
        }
      }
      assertEquals(foundAll, true);
      if (foundAll) {
        logger.info("Took {} attempts to select all hosts", numAttempts);
      }
    }
  }

  @Test(dataProvider = "default")
  public void testNoMatch(ConstraintChecker checker) {
    logger.info("Testing that {} won't find a match when there isn't one...", checker.getClass().getSimpleName());
    // non-existent datastore
    List<ResourceConstraint> constraints = new LinkedList<>();
    ResourceConstraint constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList("invalid"));
    constraints.add(constraint);
    assertTrue(checker.getCandidatesSync(constraints, 2).isEmpty());

    // existing datastore and network, but there is no host with both resources.
    constraints = new LinkedList<>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList("ds1")));
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, Arrays.asList("nw2")));
    assertTrue(checker.getCandidatesSync(constraints, 2).isEmpty());
  }

  private Map<String, HostService.State> getCloudHosts() {
    return expectedHosts.entrySet().stream()
        .filter(map -> map.getValue().usageTags.contains(UsageTag.CLOUD.name()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Set<String> getManagementHosts(ConstraintChecker checker, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.MANAGEMENT_ONLY, Collections.singletonList("unused"));
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }

  private Set<String> getHosts(ConstraintChecker checker, int numCandidates) {
    return checker.getCandidatesSync(Collections.emptyList(), numCandidates).keySet();
  }

  private Set<String> getHostsWithDatastore(ConstraintChecker checker, String datastoreId, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE, Collections.singletonList(datastoreId));
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }

  private Set<String> getHostsWithDatastoreTag(ConstraintChecker checker, String datastoreTag, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE_TAG, Collections.singletonList(datastoreTag));
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }

  private Set<String> getHostsWithNetwork(ConstraintChecker checker, String networkId, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.NETWORK, Collections.singletonList(networkId));
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }

  private Set<String> getHostsInAvailabilityZone(ConstraintChecker checker, String zoneId, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE, Collections.singletonList(zoneId));
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }

  private Set<String> getHostsNotInAvailabilityZone(ConstraintChecker checker, String zoneId, int numCandidates) {
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE, Collections.singletonList(zoneId));
    constraint.setNegative(true);
    return checker.getCandidatesSync(Collections.singletonList(constraint), numCandidates).keySet();
  }
}
