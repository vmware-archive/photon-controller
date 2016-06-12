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
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.testng.Assert.assertEquals;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests the CloudStoreConstraintChecker.
 *
 * These tests are specific to CloudStoreConstraintChecker functionality.
 */
public class CloudStoreConstraintCheckerTest {

  private static final String MGMT_HOST_PREFIX = "mgmt-host-";
  private static final String CLOUD_HOST_PREFIX = "cloud-host-";

  private static final Logger logger = LoggerFactory.getLogger(CloudStoreConstraintCheckerTest.class);

  // We test with two Cloudstore environments. This first one has a single Cloudstore host
  private static final int SMALL_NUMBER_OF_CS_HOSTS = 1;
  private TestEnvironment cloudStoreTestEnvironmentSmall;
  private XenonRestClient cloudstoreClientSmall;
  private CloudStoreHelper cloudstoreHelperSmall;
  private CloudStoreConstraintChecker checkerSmall;

  // The second Cloudstore environment has 3 Cloudstores and eventually, when we work well
  // with clusters larger than our replication factor, it will increase.
  private static final int LARGE_NUMBER_OF_CS_HOSTS = 3;
  private TestEnvironment cloudStoreTestEnvironmentLarge;
  private XenonRestClient cloudstoreClientLarge;
  private CloudStoreHelper cloudstoreHelperLarge;
  private CloudStoreConstraintChecker checkerLarge;

  private static final int PERF_NUM_CANDIDATE_REQUESTS = 10000;
  private static final int PERF_NUM_DESIRED_CANDIDATES = 4;
  private static final int PERF_NUM_HOSTS = 10000;
  private static final int PERF_NUM_DATASTORES = 100;

  private interface PerfTestRunner {
    public void runTest(int iteration);
  }

  @BeforeTest
  public void setUpClass() throws Throwable {
    configureLogging();
    startCloudstore();
  }

  private void configureLogging() {
    // Decrease logging level in a few ways

    // This test doesn't need to show all Xenon operations,
    // just the ones that fail. This keeps the test output manageable
    Logger otherLogger = LoggerFactory.getLogger(XenonRestClient.class);
    ((ch.qos.logback.classic.Logger) otherLogger).setLevel(Level.WARN);

    // We also don't need to see all the host and datastore services that are created
    otherLogger = LoggerFactory.getLogger(HostService.class);
    ((ch.qos.logback.classic.Logger) otherLogger).setLevel(Level.WARN);
    otherLogger = LoggerFactory.getLogger(DatastoreService.class);
    ((ch.qos.logback.classic.Logger) otherLogger).setLevel(Level.WARN);

    // We also don't want to see the full state of Cloudstore at the completion of the test
    otherLogger = LoggerFactory.getLogger(ServiceHostUtils.class);
    ((ch.qos.logback.classic.Logger) otherLogger).setLevel(Level.WARN);
  }

  private void startCloudstore() throws Throwable {
    this.cloudStoreTestEnvironmentSmall = TestEnvironment.create(SMALL_NUMBER_OF_CS_HOSTS);
    this.cloudstoreClientSmall =
        new XenonRestClient(cloudStoreTestEnvironmentSmall.getServerSet(), Executors.newFixedThreadPool(1));
    this.cloudstoreHelperSmall = new CloudStoreHelper(cloudStoreTestEnvironmentSmall.getServerSet());
    cloudstoreClientSmall.start();
    this.checkerSmall = new CloudStoreConstraintChecker(cloudstoreHelperSmall);

    this.cloudStoreTestEnvironmentLarge = TestEnvironment.create(LARGE_NUMBER_OF_CS_HOSTS);
    this.cloudstoreClientLarge =
        new XenonRestClient(cloudStoreTestEnvironmentLarge.getServerSet(), Executors.newFixedThreadPool(1));
    this.cloudstoreHelperLarge = new CloudStoreHelper(cloudStoreTestEnvironmentLarge.getServerSet());
    cloudstoreClientLarge.start();
    this.checkerLarge = new CloudStoreConstraintChecker(cloudstoreHelperLarge);
  }

  @AfterTest
  public void tearDownClass() throws Throwable {
    if (cloudStoreTestEnvironmentSmall != null) {
      this.cloudStoreTestEnvironmentSmall.stop();
      this.cloudStoreTestEnvironmentSmall = null;
    }
    if (cloudStoreTestEnvironmentLarge != null) {
      this.cloudStoreTestEnvironmentLarge.stop();
      this.cloudStoreTestEnvironmentLarge = null;
    }
  }

  @DataProvider(name = "environment")
  public Object[][] createDefault() {
    return new Object[][] {
        { "Single-host small Cloudstore", this.cloudStoreTestEnvironmentSmall, this.checkerSmall },
        { "Multi-host large Cloudstore", this.cloudStoreTestEnvironmentLarge, this.checkerLarge },
    };
  }

  /**
   * The CloudStoreConstraintChecker works because all hosts are labeled with a "schedulingConstant", which is a number
   * from 0 to 10,000. We divide the hosts into two groups by picking a random number betweeen 0 and 10,000 and looking
   * for hosts close to those numbers. (Details in CloudStoreConstraintChecker.) This verifies that if we can find hosts
   * at the edges of the scheduling constant range.
   *
   * @throws Throwable
   */
  @Test(dataProvider = "environment")
  public void testSchedulingConstraintBoundaries(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker) throws Throwable {

    logger.info("Testing constraint boundaries with {}", environmentName);

    // Part 1a: Ensure that we can find a host with scheduling constant 0
    List<HostService.State> hosts = createSimpleHostWithSchedulingConstant(0);
    createHosts(cloudStoreEnvironment, hosts);
    verifyHostCount(checker, 1);

    // Part 1b: Ensure that when we delete the host, we can no longer find it
    deleteHosts(cloudStoreEnvironment, hosts);
    verifyNoHosts(checker);

    // Part 2a: Ensure that we can find a host with the maximum scheduling constant (actually, 9999)
    hosts = createSimpleHostWithSchedulingConstant(HostService.MAX_SCHEDULING_CONSTANT - 1);
    createHosts(cloudStoreEnvironment, hosts);
    verifyHostCount(checker, 1);

    // Part 2b: Ensure that when we delete the host, we can no longer find it
    deleteHosts(cloudStoreEnvironment, hosts);
    verifyNoHosts(checker);
  }

  /**
   * Verify that there are no hosts available
   *
   * Note that in when we have multiple hosts and our quorum is less than the total number of hosts (which is the case
   * in cloudStoreTestEnvironmentLarge), some hosts may still report hosts after the delete, for a short while. This may
   * especially be true on a loaded build machine. Therefore we do this as a poll.
   */
  private void verifyNoHosts(CloudStoreConstraintChecker checker) throws Throwable {
    Map<String, ServerAddress> selectedHosts = null;
    for (int i = 0; i < 10000; i++) {
      selectedHosts = checker.getCandidatesSync(null, 1);
      if (selectedHosts.size() == 0) {
        break;
      }
      logger.info("Host " + selectedHosts.keySet().iterator().next() + " not deleted yet, will retry");
      Thread.sleep(1);
    }
    assertThat(selectedHosts.size(), equalTo(0));
  }

  /**
   * Verify that the required number of hosts are picked by the scheduler.
   *
   * Note that when we have multiple hosts and our quorum is less than the total number of hosts (which is the case
   * in cloudStoreTestEnvironmentLarge), some Cloudstore hosts may not have the newly created hosts, for a short
   * while. This may especially be true on a loaded build machine. Therefore we do this as a poll.
   */
  private void verifyHostCount(CloudStoreConstraintChecker checker, int hostCount) throws Throwable {
    Map<String, ServerAddress> selectedHosts = null;
    for (int i = 0; i < 10000; i++) {
      selectedHosts = checker.getCandidatesSync(null, 1);
      if (selectedHosts.size() == hostCount) {
        logger.info("Selected hosts: " + selectedHosts.keySet().iterator().next());
        break;
      }
      logger.info("Host not replicated yet, will retry");
      Thread.sleep(1);
    }
    assertThat(selectedHosts.size(), equalTo(hostCount));
  }

  private List<HostService.State> createSimpleHostWithSchedulingConstant(
      int schedulingConstant) throws Throwable {
    List<HostService.State> hosts = new ArrayList<>();

    HostService.State host = new HostService.State();
    host.schedulingConstant = new Long(schedulingConstant);
    host.hostAddress = "1.1.1.1";
    host.userName = "username";
    host.password = "password";
    host.state = HostState.READY;
    host.agentState = AgentState.ACTIVE;
    host.reportedNetworks = new HashSet<>(Arrays.asList("network-1"));
    host.availabilityZoneId = "zone-1";
    host.metadata = new HashMap<>();
    host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
    host.documentSelfLink = "cloud-host-" + UUID.randomUUID() + "-" + schedulingConstant;
    hosts.add(host);

    return hosts;
  }

  @Test(dataProvider = "environment")
  private void testHostTypeQueries(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker) throws Throwable {

    logger.info("Testing host type boundaries with {}", environmentName);

    List<DatastoreService.State> datastores = createDatastoreDescriptions(10);
    List<HostService.State> cloudHosts = createHostDescriptions(10, false, datastores);
    List<HostService.State> managementHosts = createHostDescriptions(10, true, datastores);

    logger.info("Making 10 datastores...");
    createDatastores(cloudStoreEnvironment, datastores);

    logger.info("Making 10 cloud hosts...");
    createHosts(cloudStoreEnvironment, cloudHosts);

    logger.info("Making 10 management hosts...");
    createHosts(cloudStoreEnvironment, managementHosts);

    Map<String, ServerAddress> selectedHosts;
    ResourceConstraint constraint;

    // Verify we can find just management hosts
    constraint = new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY, null);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(2));
    for (String hostId : selectedHosts.keySet()) {
      assertThat(hostId, startsWith(MGMT_HOST_PREFIX));
    }

    // Verify we can find just cloud hosts
    constraint = new ResourceConstraint(ResourceConstraintType.MANAGEMENT_ONLY, null);
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(2));
    for (String hostId : selectedHosts.keySet()) {
      assertThat(hostId, startsWith(CLOUD_HOST_PREFIX));
    }

    // Verify we can find only cloud hosts when the resource constraint list is null
    selectedHosts = checker.getCandidatesSync(null, 2);
    assertThat(selectedHosts.size(), equalTo(2));
    for (String hostId : selectedHosts.keySet()) {
      assertThat(hostId, startsWith(CLOUD_HOST_PREFIX));
    }

    // Verify we can find just the cloud host which has the datastore and not the management host which has the same
    // datastore when MANAGEMENT_ONLY constraint is not set
    List<String> datastoreList = new ArrayList<>();
    datastoreList.addAll(cloudHosts.get(0).reportedDatastores);
    constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, datastoreList);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 1);
    assertThat(selectedHosts.size(), equalTo(1));
    for (String hostId : selectedHosts.keySet()) {
      assertThat(hostId, startsWith(CLOUD_HOST_PREFIX));
    }

    deleteDatastores(cloudStoreEnvironment, datastores);
    deleteHosts(cloudStoreEnvironment, cloudHosts);
    deleteHosts(cloudStoreEnvironment, managementHosts);
  }

  /**
   * Test that a constraint with multiple values works.
   *
   * When a single constraint (e.g. network) has multiple values, it means "OR". For instance, if there are multiple
   * networks listed, any of them can work.
   *
   * For each of the constraints that can have multiple values, we validate they work
   */
  @Test(dataProvider = "environment")
  private void testMultipleValues(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker) throws Throwable {

    logger.info("Testing multiple values with {}", environmentName);

    List<DatastoreService.State> datastores = createDatastoreDescriptions(10);
    List<HostService.State> hosts = createHostDescriptions(10, false, datastores);

    logger.info("Making 10 datastores...");
    createDatastores(cloudStoreEnvironment, datastores);

    logger.info("Making 10 cloud hosts...");
    createHosts(cloudStoreEnvironment, hosts);

    HostService.State host0 = hosts.get(0);
    HostService.State host1 = hosts.get(1);
    ServerAddress host0Address = new ServerAddress(host0.hostAddress, host0.agentPort);
    ServerAddress host1Address = new ServerAddress(host1.hostAddress, host1.agentPort);
    DatastoreService.State datastore0 = datastores.get(0);
    DatastoreService.State datastore1 = datastores.get(1);

    // Part 1a: Test multiple values for allowed networks
    logger.info("Testing multiple values for networks...");
    ResourceConstraint constraint = new ResourceConstraint(
        ResourceConstraintType.NETWORK,
        Arrays.asList(getHostNetwork(host0), getHostNetwork(host1)));
    Map<String, ServerAddress> selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), hasItem(host0Address));
    assertThat(selectedHosts.values(), hasItem(host1Address));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 1b: Test multiple values for disallowed networks
    constraint = new ResourceConstraint(
        ResourceConstraintType.NETWORK,
        Arrays.asList(getHostNetwork(host0), getHostNetwork(host1)));
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);

    assertThat(selectedHosts.values(), not(hasItem(host0Address)));
    assertThat(selectedHosts.values(), not(hasItem(host1Address)));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 1c: Test cannot satisfy network constraint
    constraint = new ResourceConstraint(
        ResourceConstraintType.NETWORK,
        Arrays.asList("non_existent_network"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(0));

    // Part 2a: Test multiple values for allowed availability zones
    logger.info("Testing multiple values for availability zones...");
    constraint = new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE,
        Arrays.asList(host0.availabilityZoneId, host1.availabilityZoneId));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), hasItem(host0Address));
    assertThat(selectedHosts.values(), hasItem(host1Address));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 2b: Test multiple values for disallowed networks
    constraint = new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE,
        Arrays.asList(host0.availabilityZoneId, host1.availabilityZoneId));
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), not(hasItem(host0Address)));
    assertThat(selectedHosts.values(), not(hasItem(host1Address)));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 2c: Test cannot satisfy availability zone constraint
    constraint = new ResourceConstraint(
        ResourceConstraintType.AVAILABILITY_ZONE,
        Arrays.asList("non_existent_availability_zone"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(0));

    // Part 3a: Test multiple values for allowed hosts
    // Note that the selfLink is actually just the host id (which is what we want) because it's
    // what we posted, not what's there. When we post an id for the selfLink, Xenon converts
    // it into a full selfLink (e.g. /photon/cloudstore/hosts/ID)
    logger.info("Testing multiple values for allowed hosts...");
    constraint = new ResourceConstraint(
        ResourceConstraintType.HOST,
        Arrays.asList(host0.documentSelfLink, host1.documentSelfLink));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), hasItem(host0Address));
    assertThat(selectedHosts.values(), hasItem(host1Address));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 3b: Test multiple values for disallowed hosts
    constraint = new ResourceConstraint(
        ResourceConstraintType.HOST,
        Arrays.asList(host0.documentSelfLink, host1.documentSelfLink));
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), not(hasItem(host0Address)));
    assertThat(selectedHosts.values(), not(hasItem(host1Address)));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 3c: Test cannot satisfy host constraint
    constraint = new ResourceConstraint(
        ResourceConstraintType.HOST,
        Arrays.asList("non_existent_host"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(0));

    // Part 4a: Test multiple values for allowed datastores
    logger.info("Testing multiple values for datastores...");
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE,
        Arrays.asList(getHostDatastore(host0), getHostDatastore(host1)));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), hasItem(host0Address));
    assertThat(selectedHosts.values(), hasItem(host1Address));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 4b: Test multiple values for disallowed datastores
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE,
        Arrays.asList(getHostDatastore(host0), getHostDatastore(host1)));
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), not(hasItem(host0Address)));
    assertThat(selectedHosts.values(), not(hasItem(host1Address)));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 4c: Test cannot satisfy datastore constraint
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE,
        Arrays.asList("non_existent_datastore"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(0));

    // Part 5a: Test multiple values for allowed datastore tags
    logger.info("Testing multiple values for datastore tags...");
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE_TAG,
        Arrays.asList(getDatastoreTag(datastore0), getDatastoreTag(datastore1)));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), hasItem(host0Address));
    assertThat(selectedHosts.values(), hasItem(host1Address));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 5b: Test multiple values for disallowed datastore tags
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE_TAG,
        Arrays.asList(getDatastoreTag(datastore0), getDatastoreTag(datastore1)));
    constraint.setNegative(true);
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.values(), not(hasItem(host0Address)));
    assertThat(selectedHosts.values(), not(hasItem(host1Address)));
    assertThat(selectedHosts.size(), equalTo(2));

    // Part 5c: Test cannot satisfy datastore tags
    constraint = new ResourceConstraint(
        ResourceConstraintType.DATASTORE_TAG,
        Arrays.asList("non_existent_tag"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(0));

    // Part 6c: Test VIRTUAL_NETWORK constraints are ignored
    constraint = new ResourceConstraint(
        ResourceConstraintType.VIRTUAL_NETWORK,
        Arrays.asList("network-1", "network-2"));
    selectedHosts = checker.getCandidatesSync(Arrays.asList(constraint), 2);
    assertThat(selectedHosts.size(), equalTo(2));

    deleteDatastores(cloudStoreEnvironment, datastores);
    deleteHosts(cloudStoreEnvironment, hosts);
  }

  @Test(dataProvider = "environment")
  private void testAllHostsPicked(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker) throws Throwable {
    logger.info("Testing that all hosts are selected in {}", environmentName);

    List<DatastoreService.State> datastores = createDatastoreDescriptions(10);
    List<HostService.State> hosts = createHostDescriptions(10, false, datastores);

    logger.info("Making 10 datastores...");
    createDatastores(cloudStoreEnvironment, datastores);

    logger.info("Making 10 cloud hosts...");
    createHosts(cloudStoreEnvironment, hosts);

    List<ResourceConstraint> constraints = new LinkedList<>();
    Boolean[] selectedHost = new Boolean[10];
    boolean foundAll = false;
    int numAttempts = 0;
    Arrays.fill(selectedHost, false);

    for (int i = 0; i < 10000; i++) {
      Map<String, ServerAddress> candidate = checker.getCandidatesSync(constraints, 1);
      assertThat(candidate.size(), is(1));
      // Extract the host index from the name, which is the string "hostN", where N is the number of the host.
      for (String hostname : candidate.keySet()) {
        String[] hostnameSubstrings = hostname.split("-");
        int hostIndex = Integer.parseInt(hostnameSubstrings[hostnameSubstrings.length - 1]);
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

    deleteDatastores(cloudStoreEnvironment, datastores);
    deleteHosts(cloudStoreEnvironment, hosts);
  }

  private static class PerfResult {
    int numThreads;
    int numHosts;
    int numDatastores;
    double throughputNoConstraint;
    double throughputDatastoreConstraint;
    double throughputComplexConstraint;
  }

  /**
   * This is for testing performance. It's disabled by default because it's a long-running test. Also, it reports
   * numbers, and shouldn't fail
   *
   * We currently run tests 10 times against 1000 - 10000 hosts, so we can compare results.
   */
  @Test(dataProvider = "environment", enabled = false)
  private void testPerformance(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker) throws Throwable {

    logger.info("Setting up performance tests for {}, with {} hosts & {} datastores",
        environmentName, PERF_NUM_HOSTS, PERF_NUM_DATASTORES);
    List<DatastoreService.State> datastores = createDatastoreDescriptions(PERF_NUM_DATASTORES);
    List<HostService.State> hosts = createHostDescriptions(PERF_NUM_HOSTS, false, datastores);
    createDatastores(cloudStoreEnvironment, datastores);
    createHosts(cloudStoreEnvironment, hosts);

    List<PerfResult> perfResults = new ArrayList<>();
    for (int numThreads = 1; numThreads <= 8; numThreads *= 2) {
      PerfResult perfResult = testPerformance(environmentName, cloudStoreEnvironment, checker, numThreads, hosts);
      perfResults.add(perfResult);
    }

    for (PerfResult perfResult : perfResults) {
      logger.info("==================================================");
      logger.info("Environment: {}", environmentName);
      logger.info("Threads: {}", perfResult.numThreads);
      logger.info("Number of Hosts: {}", perfResult.numHosts);
      logger.info("Number of Datastores: {}", perfResult.numDatastores);
      logger.info("Throughput with no constraints: {}", perfResult.throughputNoConstraint);
      logger.info("Throughput with datastore constraint: {}", perfResult.throughputDatastoreConstraint);
      logger.info("Throughput with complex constraint: {}", perfResult.throughputComplexConstraint);
    }

    logger.info("Cleaning up {}", environmentName);
    deleteDatastores(cloudStoreEnvironment, datastores);
    deleteHosts(cloudStoreEnvironment, hosts);
  }

  /**
   * Helper for performance testing. Runs one test against a given number of hosts and datastores.
   */
  private PerfResult testPerformance(
      String environmentName,
      TestEnvironment cloudStoreEnvironment,
      CloudStoreConstraintChecker checker,
      int numThreads,
      List<HostService.State> hosts) throws Throwable {

    PerfResult perfResult = new PerfResult();

    perfResult.numHosts = PERF_NUM_HOSTS;
    perfResult.numDatastores = PERF_NUM_DATASTORES;
    perfResult.numThreads = numThreads;
    perfResult.throughputNoConstraint =
        testPerformanceNoConstraint(environmentName, checker, numThreads);
    perfResult.throughputDatastoreConstraint =
        testPerformanceDatastoreConstraint(environmentName, checker, numThreads, hosts);
    perfResult.throughputComplexConstraint =
        testPerformanceComplexConstraint(environmentName, checker, numThreads, hosts);

    return perfResult;
  }

  /**
   * Test performance when there are no resource constraints.
   */
  private double testPerformanceNoConstraint(
      String environmentName,
      CloudStoreConstraintChecker checker,
      int numThreads) throws Throwable {

    logger.info("Running no-constraint perf test with {} threads...", numThreads);

    return runPerfTest(numThreads, (iteration) -> {
      List<ResourceConstraint> constraints = new LinkedList<>();
      Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, PERF_NUM_DESIRED_CANDIDATES);
      assertThat(candidates, not(equalTo(null)));
    });
  }

  /**
   * Test performance with a single constraint for a datastore.
   */
  private double testPerformanceDatastoreConstraint(
      String environmentName,
      CloudStoreConstraintChecker checker,
      int numThreads,
      List<HostService.State> hosts) throws Throwable {

    logger.info("Running datastore constraint perf test with {} threads...", numThreads);

    int numHosts = hosts.size();

    return runPerfTest(numThreads, (iteration) -> {
      List<ResourceConstraint> constraints = new LinkedList<>();
      ResourceConstraint datastoreConstraint = new ResourceConstraint();
      String datastore = getHostDatastore(hosts.get(iteration % numHosts));
      datastoreConstraint.setType(ResourceConstraintType.DATASTORE);
      datastoreConstraint.setValues(Arrays.asList(datastore));
      constraints.add(datastoreConstraint);
      Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, PERF_NUM_DESIRED_CANDIDATES);
      assertThat(candidates, not(equalTo(null)));
    });
  }

  /**
   * Test performance with a single constraint for a datastore.
   */
  private double testPerformanceComplexConstraint(
      String environmentName,
      CloudStoreConstraintChecker checker,
      int numThreads,
      List<HostService.State> hosts) throws Throwable {

    logger.info("Running complex constraint perf test with {} threads...", numThreads);

    int numHosts = hosts.size();

    return runPerfTest(numThreads, (iteration) -> {
      List<ResourceConstraint> constraints = new LinkedList<>();
      // Datastore constraint
      ResourceConstraint datastoreConstraint = new ResourceConstraint();
      String datastore = getHostDatastore(hosts.get(iteration % numHosts));
      datastoreConstraint.setType(ResourceConstraintType.DATASTORE);
      datastoreConstraint.setValues(Arrays.asList(datastore));

      // Don't allow one particular host
      ResourceConstraint hostConstraint = new ResourceConstraint();
      String host = hosts.get(0).documentSelfLink;
      hostConstraint.setType(ResourceConstraintType.HOST);
      hostConstraint.setNegative(true);
      hostConstraint.setValues(Arrays.asList(host));

      // Don't allow management host
      ResourceConstraint hostTypeConstraint = new ResourceConstraint();
      hostTypeConstraint.setType(ResourceConstraintType.MANAGEMENT_ONLY);
      hostTypeConstraint.setNegative(true);

      constraints.add(datastoreConstraint);
      constraints.add(hostConstraint);
      constraints.add(hostTypeConstraint);

      Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, PERF_NUM_DESIRED_CANDIDATES);
      assertThat(candidates, not(equalTo(null)));
    });
  }

  /**
   * Helper to run a perf test. This encapsulates teh logic of running a perf test. It creates the requested number of
   * threads, then invokes the provided test PERF_NUM_CANDIDATE_REQUESTS times. It returns the requests per second that
   * result.
   */
  private double runPerfTest(
      int numThreads,
      PerfTestRunner testRunner) throws Throwable {

    List<Thread> threads = new ArrayList<>(numThreads);

    Stopwatch stopwatch = Stopwatch.createStarted();
    for (int thread = 0; thread < numThreads; thread++) {

      Thread worker = new Thread(() -> {
        for (int i = 0; i < PERF_NUM_CANDIDATE_REQUESTS; i++) {
          testRunner.runTest(i);
        }
      });
      worker.start();
      threads.add(worker);
    }
    for (Thread worker : threads) {
      worker.join();
    }

    stopwatch.stop();
    long elapsedMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    double elapsedSeconds = (double) elapsedMillis / (double) 1000;
    double throughput = (PERF_NUM_CANDIDATE_REQUESTS * numThreads) / elapsedSeconds;
    logger.info("Got {} requests/second", throughput);
    return throughput;
  }

  /**
   * Create the descriptions for the datastores that we'll be posting to Cloudstore.
   */
  private List<DatastoreService.State> createDatastoreDescriptions(int numDatastores) {
    List<DatastoreService.State> datastores = new ArrayList<>();

    for (int i = 0; i < numDatastores; i++) {
      String datastoreName = "datastore-" + UUID.randomUUID();
      String datastoreTag = "tag-" + i;
      String datastoreTag2 = "tag-extra-" + i;

      DatastoreService.State datastore = new DatastoreService.State();
      datastore.id = datastoreName;
      datastore.name = datastoreName;
      datastore.type = "SHARED_VMFS";
      datastore.tags = new HashSet<>(Arrays.asList(datastoreTag, datastoreTag2));
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
    InetAddress address;
    List<HostService.State> hosts = new ArrayList<>();
    int numDatastores = datastores.size();

    if (isManagement) {
      address = InetAddresses.forString("100.1.1.0");
    } else {
      address = InetAddresses.forString("200.1.1.0");
    }

    for (int i = 0; i < numHosts; i++) {
      address = InetAddresses.increment(address);
      String hostName;
      String nwName = "network-" + i;
      String azName = "zone-" + i;

      if (isManagement) {
        hostName = MGMT_HOST_PREFIX + UUID.randomUUID() + "-" + i;
      } else {
        hostName = CLOUD_HOST_PREFIX + UUID.randomUUID() + "-" + i;
      }

      HostService.State host = new HostService.State();
      host.hostAddress = address.toString();
      host.agentPort = 8835;
      host.userName = "username";
      host.password = "password";
      host.state = HostState.READY;
      host.agentState = AgentState.ACTIVE;
      host.reportedDatastores = new HashSet<>(Arrays.asList(datastores.get(i % numDatastores).name));
      host.reportedNetworks = new HashSet<>(Arrays.asList(nwName));
      host.availabilityZoneId = azName;
      host.metadata = new HashMap<>();
      // Equally distribute scheduling constant to make randomness test succeed
      // For example, if i = 1, numHosts = 10, schedulingConstant = 1 * (10000 - 1)/10 = 999
      host.schedulingConstant = i * (long) Math.floor((HostService.MAX_SCHEDULING_CONSTANT - 1) / numHosts);
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
   * Given the host descriptions created by createHostDescriptions, post them to Cloudstore.
   */
  private void createHosts(TestEnvironment cloudStoreEnvironment, List<HostService.State> hosts) throws Throwable {
    for (HostService.State host : hosts) {
      Operation result = cloudStoreEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK, host);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the datastore descriptions created by createDatastoreDescriptions, post them to Cloudstore.
   */
  private void createDatastores(TestEnvironment cloudStoreEnvironment, List<DatastoreService.State> datastores)
      throws Throwable {
    for (DatastoreService.State datastore : datastores) {
      Operation result = cloudStoreEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, datastore);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the host descriptions created by createHostDescriptions, delete them to from Cloudstore.
   */
  private void deleteHosts(TestEnvironment cloudStoreEnvironment, List<HostService.State> hosts) throws Throwable {
    for (HostService.State host : hosts) {
      String hostUri = UriUtils.buildUriPath(HostServiceFactory.SELF_LINK, host.documentSelfLink);
      Operation result = cloudStoreEnvironment.sendDeleteAndWait(hostUri);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * Given the datastore descriptions created by createDatastoreDescriptions, delete them from Cloudstore.
   */
  private void deleteDatastores(TestEnvironment cloudStoreEnvironment, List<DatastoreService.State> datastores)
      throws Throwable {
    for (DatastoreService.State datastore : datastores) {
      String hostUri = UriUtils.buildUriPath(DatastoreServiceFactory.SELF_LINK, datastore.documentSelfLink);
      Operation result = cloudStoreEnvironment.sendDeleteAndWait(hostUri);
      assertThat(result.getStatusCode(), equalTo(200));
    }
  }

  /**
   * For a single host, return a network the host reports.
   */
  private String getHostNetwork(HostService.State host) {
    for (String network : host.reportedNetworks) {
      return network;
    }
    return null;
  }

  /**
   * For a single host, return a datastore the host reports.
   */
  private String getHostDatastore(HostService.State host) {
    for (String datastore : host.reportedDatastores) {
      return datastore;
    }
    return null;
  }

  /**
   * For a single datastore, return a datastore tag it reports.
   */
  private String getDatastoreTag(DatastoreService.State datastore) {
    for (String tag : datastore.tags) {
      return tag;
    }
    return null;
  }

}
