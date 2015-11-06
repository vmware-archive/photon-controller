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

import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.chairman.service.Datastore;
import com.vmware.photon.controller.chairman.service.Network;
import com.vmware.photon.controller.resource.gen.DatastoreType;
import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;

import com.google.common.collect.ImmutableList;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests {@link Hierarchy}.
 */
public class HierarchyTest extends PowerMockTestCase {

  private Map<String, Host> hosts;
  private Map<String, Host> rootHosts;
  private Map<String, Scheduler> leafSchedulers;
  private AvailabilityZone az;
  private Set<Datastore> datastores;
  private Set<Network> networks;

  @BeforeMethod
  public void setUp() {
    hosts = new LinkedHashMap();
    rootHosts = new LinkedHashMap<>();
    datastores = new HashSet();
    networks = new HashSet();
    leafSchedulers = new HashMap();
    az = new AvailabilityZone("availability-zone");
  }

  @Test
  public void testAddHost() {

    Hierarchy h = new Hierarchy(0, 0);
    Host host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    Host root = h.addRootHost("addr", 1234);

    // Verify that the host has been added
    assertThat(h.getHosts().get(host.getId()), is(notNullValue()));
    assertThat(host.isDirty(), is(true));
    assertThat(host.isConfigured(), is(false));

    // Verify that the root scheduler host is added
    assertThat(h.getRootScheduler().getOwner(), is(root));
    assertThat(root.isConfigured(), is(false));
    assertThat(root.isDirty(), is(true));

    // Mark all hosts as configured and clean
    h.setAllHostsDirty(false);
    h.setAllHostsConfigured(true);

    assertThat(host.isDirty(), is(false));
    assertThat(host.isConfigured(), is(true));
    assertThat(host.isMissing(), is(false));
    assertThat(host.isDirty(), is(false));
    assertThat(host.isConfigured(), is(true));

    // set the non-root host as missing and Re-add the hosts
    host.setMissing(true);
    host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    root = h.addRootHost("addr", 1234);

    // Verify that the missing host is not missing and its not dirty
    assertThat(host.isDirty(), is(false));
    assertThat(host.isConfigured(), is(false));
    assertThat(host.isMissing(), is(false));

    assertThat(root.isConfigured(), is(false));
  }

  @Test
  public void testManagementHosts() {

    Hierarchy h = new Hierarchy(4, 4);
    h.addHost("mgmt1", az, datastores, networks, true, "addr", 1234);
    Host mHost2 = h.addHost("mgmt2", az, datastores, networks, true, "addr", 1234);
    h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    h.addHost("h2", az, datastores, networks, false, "addr", 1234);

    h.scan();

    Scheduler mgmtLeaf = null;
    Scheduler otherLeaf = null;
    for (Scheduler sch : h.getLeafSchedulers().values()) {
      if (sch.getOwner() != null && sch.getOwner().isManagementOnly()) {
        mgmtLeaf = sch;
      } else {
        otherLeaf = sch;
      }
    }

    // Verify that the management hosts are grouped together
    assertThat(h.getLeafSchedulers().size(), is(2));
    // Verify that the mgmt leaf has all mgmt only hosts
    assertThat(mgmtLeaf, notNullValue());
    // verify that all hosts in the mgmt leaf are mgmt only hosts
    assertThat(mgmtLeaf.getHosts().size(), is(2));
    for (Host host : mgmtLeaf.getHosts().values()) {
      assertThat(host.isManagementOnly(), is(true));
    }

    // mgmt1 is re-added, this time its not management only
    Host mHost1 = h.addHost("mgmt1", az, datastores, networks, false, "addr", 1234);

    h.scan();

    // Verify that host mgmt1 has been moved to the other leaf
    assertThat(mgmtLeaf.getOwner(), is(mHost2));
    assertThat(mgmtLeaf.getHosts().size(), is(1));
    assertThat(otherLeaf.getHosts().size(), is(3));
    assertThat(mHost1.isDirty(), is(true));
    assertThat(mHost2.isDirty(), is(true));
    assertThat(h.getRootScheduler().getOwner().isDirty(), is(true));

    // Remove the only mgmt2
    h.removeHost("mgmt2");
    h.scan();

    assertThat(h.getLeafSchedulers().size(), is(1));
  }

  @Test
  public void testRemoveHost() {
    // Create a leaf with a single child host
    Host host = new Host("h1", az, datastores, networks, "addr", 1234);
    Scheduler leaf = new Scheduler("leaf");
    leaf.setOwner(host);
    leaf.addHost(host);

    hosts.put(host.getId(), host);
    leafSchedulers.put(leaf.getId(), leaf);
    // Initialize the hierarchy with a single leaf
    Hierarchy h = new Hierarchy(1, 0);
    Map<String, Scheduler> leafs = new HashMap();
    leafs.put(leaf.getId(), leaf);
    h.addLeaves(leafs);

    // Verify that the leaf was added to the root scheduler
    assertThat(h.getLeafSchedulers().get(leaf.getId()), is(leaf));
    assertThat(leaf.getParent(), is(h.getRootScheduler()));
    assertThat(leaf.getOwner(), is(host));

    // Re-add the same host with different constraints
    datastores = new HashSet();
    datastores.add(new Datastore("ds1", DatastoreType.LOCAL_VMFS));
    host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);

    // Verify that the host has been removed from the hierarchy, but still
    // exists in the hosts list, which is later processed and added
    assertThat(h.getHosts().get(host.getId()), not(nullValue()));
    assertThat(leaf.getOwner(), is(nullValue()));
    assertThat(leaf.getHosts().get(host.getId()), is(nullValue()));

    // Remove the host from the hierarchy
    h.removeHost(host.getId());

    // Verify that the host doesn't exist in the hierarchy,
    // i.e. removed from the host list and the corresponding
    // leaf scheduler
    assertThat(h.getHosts().get(host.getId()), is(nullValue()));
    assertThat(leaf.getOwner(), is(nullValue()));
    assertThat(leaf.getHosts().get(host.getId()), is(nullValue()));
  }

  @Test
  public void testScanHostAdd() {

    Hierarchy h = new Hierarchy(1, 2);

    // Add a single host to an empty hierarchy
    Host host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    Host root = h.addRootHost("addr", 1234);
    h.setAllHostsDirty(false);
    h.setAllHostsConfigured(true);

    h.scan();
    // Verify that a new leaf has been created and added to the root scheduler children
    assertThat(h.getRootScheduler().getChildren().size(), is(1));
    assertThat(host.isDirty(), is(true));
    assertThat(host.isConfigured(), is(false));
    assertThat(root.isDirty(), is(true));
    assertThat(root.isConfigured(), is(false));
    assertThat(host.getParentScheduler().getOwner(), is(host));

    // Add another host
    h.setAllHostsDirty(false);
    h.setAllHostsConfigured(true);

    Host host2 = h.addHost("h2", az, datastores, networks, false, "addr2", 12345);

    h.scan();

    // The new host should be added to the same leaf and the leaf owner
    // shouldn't change
    assertThat(h.getRootScheduler().getChildren().size(), is(1));
    assertThat(host.isDirty(), is(true));
    assertThat(host.isConfigured(), is(false));
    assertThat(root.isDirty(), is(true));
    assertThat(root.isConfigured(), is(false));
    assertThat(host.getParentScheduler().getOwner(), is(host));
    assertThat(host.getParentScheduler().getHosts().get(host2.getId()), is(host2));

    // Mark host as missing, host2 should become the new leaf owner
    host.setMissing(true);
    h.scan();
    assertThat(host.getParentScheduler().getOwner(), is(host2));

    // Make host2 possible missing and mark host as not missing,
    // the new owner should become host
    host.setMissing(false);
    host2.setDirty(false);
    host2.setConfigured(false);

    h.scan();
    assertThat(host.getParentScheduler().getOwner(), is(host));

    // Mark host2 as available and remove host1 from the hierarchy
    // host2 should be the new owner
    host2.setDirty(false);
    host2.setConfigured(true);
    h.removeHost(host.getId());
    h.scan();

    assertThat(root.getSchedulers().size(), is(1));
    assertThat(host2.getParentScheduler().getParent(), is(h.getRootScheduler()));
    assertThat(host2.getParentScheduler().getOwner(), is(host2));
  }

  @Test
  public void testAddFull() {
    // Create a hierarchy with 1 host and 1 leaf only
    Hierarchy h = new Hierarchy(1, 1);
    Host host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    h.addRootHost("addr", 1234);
    h.addHost("h2", az, datastores, networks, false, "addr", 1234);

    h.scan();

    // Since the rootFanout and leafFanout are set to one, Only one host and
    // one leaf should be in the hierarchy
    assertThat(h.getHosts().size(), is(2));
    assertThat(h.getLeafSchedulers().size(), is(1));
    assertThat(h.getRootScheduler().getChildren().size(), is(1));
    Scheduler leaf = h.getLeafSchedulers().values().iterator().next();
    assertThat(leaf.getHosts().get(host.getId()), is(host));
  }

  @Test
  public void testLeafSplit() {
    Hierarchy h = new Hierarchy(2, 1);
    h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    h.addRootHost("addr", 1234);
    h.addHost("h2", az, datastores, networks, false, "addr", 1234);

    h.scan();
    // Since the fanout of the root is 2 and the leaf fanout is 1, then
    // we expect two have two leafs with one host each
    assertThat(h.getLeafSchedulers().size(), is(2));

    // Remove host1
    h.removeHost("h1");
    h.scan();

    // Since the removal of h1 will cause its leaf scheduler to be empty
    // we expect the leaf to be removed from the hierarchy
    assertThat(h.getLeafSchedulers().size(), is(1));
    assertThat(h.getHosts().size(), is(1));
  }

  @Test
  public void testMarkHosts() {
    Hierarchy h = new Hierarchy(2, 1);
    Host host = h.addHost("h1", az, datastores, networks, false, "addr", 1234);

    assertThat(host.isMissing(), is(false));

    // Mark host as missing
    h.markMissing(host.getId());

    // Verify that its missing
    assertThat(host.isMissing(), is(true));

    // Mark host as resurrected
    h.markResurrected(host.getId());

    // Verify that the host is no longer missing
    assertThat(host.isMissing(), is(false));
  }

  @Test
  public void testGetHosts() {
    Hierarchy h = new Hierarchy(2, 5);
    Host root = h.addRootHost("addr", 1234);
    Host host1 = h.addHost("h1", az, datastores, networks, false, "addr", 1234);
    Host host2 = h.addHost("h2", az, datastores, networks, false, "addr", 1234);
    Host host3 = h.addHost("h3", az, datastores, networks, false, "addr", 1234);
    Host host4 = h.addHost("h4", az, datastores, networks, false, "addr", 1234);

    h.scan();

    // Enumerate all the possibilities that can cause a host to be in one of
    // lists only, or in both lists
    host1.setDirty(true);
    host1.setConfigured(false);

    host2.setDirty(false);
    host2.setConfigured(false);

    host3.setDirty(false);
    host3.setConfigured(true);

    host4.setMissing(true);

    Map<String, Host> needToConfigure = h.getNonConfiguredHosts();
    Map<String, Host> dirtyHosts = h.getDirtyHosts();

    assertThat(needToConfigure.get(host1.getId()), is(host1));
    assertThat(dirtyHosts.get(host1.getId()), is(host1));

    assertThat(needToConfigure.get(host2.getId()), is(host2));
    assertThat(dirtyHosts.get(host2.getId()), is(nullValue()));

    assertThat(needToConfigure.get(host3.getId()), is(nullValue()));
    assertThat(dirtyHosts.get(host3.getId()), is(nullValue()));

    assertThat(needToConfigure.get(host4.getId()), is(host4));

    assertThat(needToConfigure.get(root.getId()), is(root));
  }

  @Test
  public void testAddLeafs() {
    Hierarchy h = new Hierarchy(2, 5);

    // Add a leaf with a single child host
    Host h1 = new Host("h1", az, "addr", 1234);
    Scheduler leaf1 = new Scheduler("leaf");
    leaf1.addHost(h1);
    Map<String, Scheduler> leafs = new HashMap();
    leafs.put(leaf1.getId(), leaf1);
    h.addLeaves(leafs);
    // Run a scan
    h.scan();

    // Verify that the leaf has been added to the hierarchy,
    // the leaf has been inserted into the root schedulers children
    // and that the leaf's child host was added to the hierarchy
    assertThat(h.getHosts().get(h1.getId()), is(h1));
    assertThat(h.getLeafSchedulers().get(leaf1.getId()), is(leaf1));
    assertThat(h.getRootScheduler().getChildren().get(leaf1.getId()), is(leaf1));
    assertThat(leaf1.getOwner(), is(h1));
  }

  @Test
  public void testAddingHostWithDatastoreTags() {
    Set<String> tags = new HashSet();
    String tagId1 = "tag1";
    tags.add(tagId1);
    Datastore dsWithTags = new Datastore("ds1", DatastoreType.LOCAL_VMFS, tags);
    datastores.add(dsWithTags);
    Hierarchy h = new Hierarchy(2, 5);
    Host root = h.addRootHost("addr", 1234);
    Host host1 = h.addHost("h1", az, datastores, networks, false, "addr", 1234);

    Scheduler rootSch = h.getRootScheduler();
    assertThat(rootSch.getConstraintSet().size(), is(0));

    // After the scan, the tag should appear as a constraint in the root and leaf schedulers
    h.scan();

    Scheduler leaf = host1.getParentScheduler();
    ResourceConstraint rs1 = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, ImmutableList.of(tagId1));
    assertThat(rootSch.getConstraintSet().contains(rs1), is(true));
    assertThat(leaf.getConstraintSet().contains(rs1), is(true));

    // Re-add the host with a new tag
    String tagId2 = "tag2";
    Set<Datastore> newDs = new HashSet();
    ResourceConstraint rs2 = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, ImmutableList.of(tagId2));
    Set<String> tags2 = new HashSet(tags);
    tags2.add(tagId2);
    dsWithTags = new Datastore("ds1", DatastoreType.LOCAL_VMFS, tags2);
    newDs.add(dsWithTags);

    host1 = h.addHost("h1", az, newDs, networks, false, "addr", 1234);

    // Verify that the new tag has been propagated to the root scheduler
    assertThat(rootSch.getConstraintSet().contains(rs1), is(true));
    assertThat(leaf.getConstraintSet().contains(rs1), is(true));
    assertThat(rootSch.getConstraintSet().contains(rs2), is(true));
    assertThat(leaf.getConstraintSet().contains(rs2), is(true));

    // Add another host with a different tag
    String tagId3 = "tag3";
    Set<Datastore> newDs2 = new HashSet();
    ResourceConstraint rs3 = new ResourceConstraint(ResourceConstraintType.DATASTORE_TAG, ImmutableList.of(tagId2));
    Set<String> tags3 = new HashSet(tags);
    tags2.add(tagId2);
    dsWithTags = new Datastore("ds1", DatastoreType.LOCAL_VMFS, tags2);
    newDs2.add(dsWithTags);

    Host host2 = h.addHost("h2", az, newDs2, networks, false, "addr", 1234);

    h.scan();

    // Verify that the new tag has been propagated to the root scheduler
    assertThat(rootSch.getConstraintSet().contains(rs1), is(true));
    assertThat(leaf.getConstraintSet().contains(rs1), is(true));
    assertThat(rootSch.getConstraintSet().contains(rs2), is(true));
    assertThat(leaf.getConstraintSet().contains(rs2), is(true));
    assertThat(rootSch.getConstraintSet().contains(rs3), is(true));
    assertThat(leaf.getConstraintSet().contains(rs3), is(true));

    // Remove both hosts
    h.removeHost(host1.getId());
    h.removeHost(host2.getId());

    // Verify that the tags have been removed
    assertThat(rootSch.getConstraintSet().isEmpty(), is(true));
    assertThat(leaf.getConstraintSet().isEmpty(), is(true));
  }
}
