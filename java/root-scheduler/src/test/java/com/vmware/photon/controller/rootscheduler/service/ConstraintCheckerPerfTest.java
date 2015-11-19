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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Performance tests for constraint checker.
 */
public class ConstraintCheckerPerfTest {
  private static final Logger logger = LoggerFactory.getLogger(ConstraintCheckerPerfTest.class);
  private Random random = new Random();

  @DataProvider(name = "default")
  public Object[][] createDefault() {
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
      datastores.put(new UUID(0, i).toString(), datastore);

    }
    for (int i = 0; i < numHosts; i++) {
      HostService.State host = new HostService.State();
      host.hostAddress = "host" + i;
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
      host.availabilityZone = new UUID(0, randomInt).toString();
      host.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name()));
      hosts.put(new UUID(0, i).toString(), host);

    }
    ConstraintChecker inMemory = new InMemoryConstraintChecker(hosts, datastores);
    return new Object[][]{
        {inMemory, hosts},
    };
  }

  @Test(dataProvider = "default")
  public void testPerformance(ConstraintChecker checker, Map<String, HostService.State> expectedHosts) {
    // no constraint
    List<ResourceConstraint> constraints = new LinkedList<>();
    int numRequests = 1000000;
    Stopwatch watch = Stopwatch.createStarted();
    for (int i = 0; i < numRequests; i++) {
      checker.getCandidates(constraints, 4);
    }
    watch.stop();
    double throughput = (double) numRequests / Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("No constraint");
    logger.info("{} requests", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);

    // single datastore constraint
    watch = Stopwatch.createStarted();
    for (int i = 0; i < numRequests; i++) {
      constraints = new LinkedList<>();
      int datastore = random.nextInt(1000);
      String datastoreId = new UUID(0, datastore).toString();
      constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList(datastoreId)));
      checker.getCandidates(constraints, 4);
    }
    watch.stop();
    throughput = (float) numRequests / Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("Single datastore constraint");
    logger.info("{} requests", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);

    // single availability zone constraint
    watch = Stopwatch.createStarted();
    for (int i = 0; i < numRequests; i++) {
      constraints = new LinkedList<>();
      int datastore = random.nextInt(1000);
      String az = new UUID(0, datastore).toString();
      constraints.add(new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(az)));
      checker.getCandidates(constraints, 4);
    }
    watch.stop();
    throughput = (float) numRequests / Math.max(1, watch.elapsed(TimeUnit.MILLISECONDS)) * 1000;
    logger.info("Single availability zone constraint");
    logger.info("{} requests", numRequests);
    logger.info("{} milliseconds", watch.elapsed(TimeUnit.MILLISECONDS));
    logger.info("{} requests/sec", throughput);
  }
}
