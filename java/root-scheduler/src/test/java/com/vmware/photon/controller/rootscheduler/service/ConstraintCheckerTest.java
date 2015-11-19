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
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test Constraint Checker.
 */
public class ConstraintCheckerTest {
  /**
   * Default data provider.
   *
   * - 10 hosts: host0, host1, ..., host9
   * - 1 network per host: host0 => nw0, host1 => nw1, ..., host9 => nw9
   * - 1 datastore per host: host0 => ds0, host1 => ds1, ..., host9 => ds9
   * - each host with a unique availability zone: host0 => az0, host1 => az1, ..., host9 => az9
   * - 1 tag per datastore: ds0 => dstag0, ds1 => dstag1, ..., ds9 => dstag9
   * - 5 management hosts host0, host2, host4, host6, host8
   */

  @DataProvider(name = "default")
  public Object[][] createDefault() {
    Map<String, HostService.State> hosts = new HashMap<>();
    Map<String, DatastoreService.State> datastores = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      String hostName = "host" + i;
      String dsName = "ds" + i;
      String dsTag = "dstag" + i;
      String nwName = "nw" + i;
      String azName = "az" + i;
      HostService.State host = new HostService.State();
      host.hostAddress = hostName;
      host.reportedDatastores = new HashSet<>(Arrays.asList(dsName));
      host.reportedNetworks = new HashSet<>(Arrays.asList(nwName));
      host.availabilityZone = azName;
      if (i % 2 == 0) {
        host.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name()));
      } else {
        host.usageTags = new HashSet<>();
      }
      hosts.put(hostName, host);

      DatastoreService.State datastore = new DatastoreService.State();
      datastore.tags = new HashSet<>(Arrays.asList(dsTag));
      datastores.put(dsName, datastore);
    }
    ConstraintChecker inMemory = new InMemoryConstraintChecker(hosts, datastores);
    return new Object[][]{
        {inMemory, hosts},
    };
  }

  @Test(dataProvider = "default")
  public void testDefault(ConstraintChecker checker, Map<String, HostService.State> expectedHosts) {
    Set<String> hosts = checker.getManagementHosts();
    assertThat(hosts, containsInAnyOrder("host0", "host2", "host4", "host6", "host8"));

    hosts = checker.getHosts();
    assertEquals(hosts, expectedHosts.keySet());

    for (int i = 0; i < expectedHosts.size(); i++) {
      String hostName = "host" + i;
      String dsName = "ds" + i;
      String dsTag = "dstag" + i;
      String nwName = "nw" + i;
      String azName = "az" + i;
      hosts = checker.getHostsWithDatastore(dsName);
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = checker.getHostsWithDatastoreTag(dsTag);
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = checker.getHostsWithNetwork(nwName);
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = checker.getHostsInAvailabilityZone(azName);
      assertThat(hosts, containsInAnyOrder(hostName));
      hosts = checker.getHostsNotInAvailabilityZone(azName);
      assertEquals(hosts,
                   Sets.filter(expectedHosts.keySet(), Predicates.not(Predicates.equalTo(hostName))));
    }
  }
  @Test(dataProvider = "default")
  public void testSingleConstraint(ConstraintChecker checker, Map<String, HostService.State> expectedHosts) {
    Map<String, ServerAddress> allHosts = checker.getHostMap();
    assertEquals(allHosts.keySet(), expectedHosts.keySet());
    for (Map.Entry<String, ServerAddress> entry: allHosts.entrySet()) {
      assertThat(entry.getKey(), is(entry.getValue().getHost()));
      assertThat(entry.getValue().getPort(), is(ConstraintChecker.DEFAULT_AGENT_PORT));
    }

    for (int i = 0; i < expectedHosts.size(); i++) {
      String hostName = "host" + i;
      ServerAddress address = new ServerAddress(hostName, ConstraintChecker.DEFAULT_AGENT_PORT);

      // host
      ResourceConstraint constraint = new ResourceConstraint(ResourceConstraintType.HOST, Arrays.asList(hostName));
      List<ResourceConstraint> constraints = new LinkedList<>();
      constraints.add(constraint);
      Map<String, ServerAddress> candidates = checker.getCandidates(constraints, 10);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      constraint = new ResourceConstraint(ResourceConstraintType.HOST, Arrays.asList(hostName));
      constraint.setNegative(true);
      constraints = new LinkedList<>();
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, expectedHosts.size());
      assertThat(candidates.size(), is(expectedHosts.size() - 1));
      assertEquals(candidates,
          Maps.filterKeys(candidates, Predicates.not(Predicates.equalTo(hostName))));

      // datastore
      String dsName = "ds" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList(dsName));
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // datastore tag
      String dsTag = "dstag" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, Arrays.asList(dsTag));
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // network
      String nwName = "nw" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.NETWORK, Arrays.asList(nwName));
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // availability zone
      String azName = "az" + i;
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(azName));
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, 2);
      assertEquals(candidates, ImmutableMap.of(hostName, address));

      // negative availability zone
      constraints = new LinkedList<>();
      constraint = new ResourceConstraint(ResourceConstraintType.AVAILABILITY_ZONE, Arrays.asList(azName));
      constraint.setNegative(true);
      constraints.add(constraint);
      candidates = checker.getCandidates(constraints, expectedHosts.size());
      assertThat(candidates.size(), is(expectedHosts.size() - 1));
      assertEquals(candidates,
                   Maps.filterKeys(candidates, Predicates.not(Predicates.equalTo(hostName))));
    }
  }

  @Test(dataProvider = "default")
  public void testNoConstraint(ConstraintChecker checker, Map<String, HostService.State> expectedHosts) {
    // expect to get all the hosts without any constraint.
    Map<String, ServerAddress> allHosts = checker.getHostMap();
    List<ResourceConstraint> constraints = new LinkedList<>();
    Map<String, ServerAddress> candidates = checker.getCandidates(constraints, 10);
    assertEquals(candidates, allHosts);

    // verify that the candidates get picked randomly by picking a single candidate many
    // times and verifying that eveybody gets picked. This is not deterministic.
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < 10000; i++) {
      Map<String, ServerAddress> candidate = checker.getCandidates(constraints, 1);
      assertThat(candidate.size(), is(1));
      hosts.addAll(candidate.keySet());
    }
    assertThat(hosts.size(), is(expectedHosts.size()));
  }

  @Test(dataProvider = "default")
  public void testNoMatch(ConstraintChecker checker, Map<String, HostService.State> expectedHosts) {
    // non-existent datastore
    List<ResourceConstraint> constraints = new LinkedList<>();
    ResourceConstraint constraint = new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList("invalid"));
    constraints.add(constraint);
    assertTrue(checker.getCandidates(constraints, 2).isEmpty());

    // existing datastore and network, but there is no host with both resources.
    constraints = new LinkedList<>();
    constraints.add(new ResourceConstraint(ResourceConstraintType.DATASTORE, Arrays.asList("ds1")));
    constraints.add(new ResourceConstraint(ResourceConstraintType.NETWORK, Arrays.asList("nw2")));
    assertTrue(checker.getCandidates(constraints, 2).isEmpty());
  }
}
