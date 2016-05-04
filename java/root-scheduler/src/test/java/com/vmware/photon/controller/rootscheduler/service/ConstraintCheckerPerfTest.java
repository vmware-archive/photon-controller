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

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Performance tests for constraint checker.
 */
public class ConstraintCheckerPerfTest {
  private static final Logger logger = LoggerFactory.getLogger(ConstraintCheckerPerfTest.class);

  private TestEnvironment cloudStoreTestEnvironment;

  private Random random = new Random();

  @BeforeClass
  public void setUpClass() throws Throwable {
    cloudStoreTestEnvironment = TestEnvironment.create(1);
    int numHosts = 10000;
    int numDatastores = 1000;
    int numDatastoresPerHost = 10;
    int numNetworks = 100;
    int numNetworksPerHost = 2;
    int numAvailabilityZones = 1000;
    int randomInt;

    Map<String, HostService.State> hosts = new HashMap<>();
    Map<String, DatastoreService.State> datastores = new HashMap<>();
    for (int i = 0; i < numDatastores; i++) {
      DatastoreService.State datastore = new DatastoreService.State();
      String datastoreId = new UUID(0, i).toString();
      datastore.id = datastoreId;
      datastore.name = datastoreId;
      datastore.type = "SHARED_VMFS";
      datastore.tags = new HashSet<>();
      datastores.put(datastore.id, datastore);
    }
    for (int i = 0; i < numHosts; i++) {
      HostService.State host = new HostService.State();
      host.hostAddress = "host" + i;
      host.state = HostState.READY;
      host.agentState = AgentState.ACTIVE;
      host.userName = "username";
      host.password = "password";
      host.reportedDatastores = new HashSet<>();
      while (host.reportedDatastores.size() < numDatastoresPerHost) {
        randomInt = random.nextInt(numDatastores);
        host.reportedDatastores.add(new UUID(0, randomInt).toString());
      }
      host.reportedNetworks = new HashSet<>();
      while (host.reportedNetworks.size() < numNetworksPerHost) {
        randomInt = random.nextInt(numNetworks);
        host.reportedNetworks.add(new UUID(0, randomInt).toString());
      }

      randomInt = random.nextInt(numAvailabilityZones);
      host.availabilityZoneId = new UUID(0, randomInt).toString();
      host.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));
      hosts.put(new UUID(0, i).toString(), host);
    }

    for (Map.Entry<String, DatastoreService.State> entry : datastores.entrySet()) {
      DatastoreService.State initialState = entry.getValue();
      initialState.documentSelfLink = entry.getKey();
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(DatastoreServiceFactory.SELF_LINK, initialState);
      assertThat(result.getStatusCode(), is(200));
    }

    for (Map.Entry<String, HostService.State> entry : hosts.entrySet()) {
      HostService.State initialState = entry.getValue();
      initialState.documentSelfLink = entry.getKey();
      Operation result = cloudStoreTestEnvironment.sendPostAndWait(HostServiceFactory.SELF_LINK, initialState);
      assertThat(result.getStatusCode(), is(200));
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
  public Object[][] createDefault() throws Throwable {
    XenonRestClient xenonRestClient = new XenonRestClient(
            cloudStoreTestEnvironment.getServerSet(), Executors.newFixedThreadPool(1));
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreTestEnvironment.getServerSet());
    // This tests does tens of thousands of operation. We only log failures, so we can see what's happening.
    xenonRestClient.start();
    ConstraintChecker cloudStore = new CloudStoreConstraintChecker(cloudStoreHelper);

    return new Object[][] {
      { cloudStore, 1 },
      { cloudStore, 4 },
      { cloudStore, 8 },
    };
  }

  /**
   * Performance tests for constraint checker. Disabled by default.
   */
  @Test(dataProvider = "default", enabled = false)
  public void testPerformance(ConstraintChecker checker, int numThreads) throws Exception {
    // no constraint
    int numRequests = 1000000;

    Stopwatch watch = Stopwatch.createStarted();
    List<Thread> workers = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      Thread worker = new Thread(() -> {
        List<ResourceConstraint> constraints = new LinkedList<>();
        for (int j = 0; j < numRequests; j++) {
          Map<String, ServerAddress> candidates = checker.getCandidatesSync(constraints, 4);
          assertThat(candidates.size(), not(equalTo(0)));
        }
      });
      worker.start();
      workers.add(worker);
    }
    for (Thread worker: workers) {
      worker.join();
    }
    watch.stop();
    double throughput = (double) numThreads * numRequests /
        Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("No constraint");
    logger.info("{} threads", numThreads);
    logger.info("{} requests/thread", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);

    // single datastore constraint
    watch = Stopwatch.createStarted();
    workers = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      Thread worker = new Thread(() -> {
        for (int j = 0; j < numRequests; j++) {
          List<ResourceConstraint> constraints = new LinkedList<>();
          int datastore = random.nextInt(1000);
          String datastoreId = new UUID(0, datastore).toString();
          constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList(datastoreId)));
          checker.getCandidatesSync(constraints, 4);
        }
      });
      worker.start();
      workers.add(worker);
    }
    for (Thread worker: workers) {
      worker.join();
    }
    watch.stop();
    throughput = (double) numThreads * numRequests /
        Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("Single datastore constraint");
    logger.info("{} threads", numThreads);
    logger.info("{} requests/thread", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);

    // single availability zone constraint
    watch = Stopwatch.createStarted();
    workers = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      Thread worker = new Thread(() -> {
        for (int j = 0; j < numRequests; j++) {
          List<ResourceConstraint> constraints = new LinkedList<>();
          int datastore = random.nextInt(1000);
          String az = new UUID(0, datastore).toString();
          constraints.add(new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(az)));
          checker.getCandidatesSync(constraints, 4);
        }
      });
      worker.start();
      workers.add(worker);
    }
    for (Thread worker: workers) {
      worker.join();
    }
    watch.stop();
    throughput = (double) numThreads * numRequests /
        Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("Single availability zone constraint");
    logger.info("{} threads", numThreads);
    logger.info("{} requests/thread", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);
  }
}
