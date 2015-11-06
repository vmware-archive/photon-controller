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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests {@link Scheduler}.
 */
public class SchedulerTest {

  private Host owner;
  private Scheduler parent;
  private Set<Datastore> datastores;
  private Set<Network> networks;

  @BeforeMethod
  public void setUp() throws Exception {
    owner = new Host("host", new AvailabilityZone("availability-zone"), "host", 1234);
    owner.setDirty(false);
    parent = new Scheduler("parent");
    parent.setOwner(owner);
    datastores = new HashSet<>();
    networks = new HashSet<>();
  }

  @Test
  public void testAddChild() {
    Scheduler child = new Scheduler("child");
    parent.addChild(child);

    assertThat(parent.getChildren().size(), is(1));
    assertThat(parent.getChildren(), hasEntry("child", child));
    assertThat(child.getParent(), is(parent));
    assertThat(owner.isDirty(), is(true));
  }

  /**
   * Make sure that we can build partial hierarchy when the owner is not available.
   */
  @Test
  public void testAddChildNoOwner() {
    Scheduler child = new Scheduler("child");
    parent.clearOwner();
    parent.addChild(child);

    assertThat(parent.getChildren().size(), is(1));
    assertThat(parent.getChildren(), hasEntry("child", child));
    assertThat(child.getParent(), is(parent));
  }

  @Test
  public void testRemoveChild() {
    Scheduler child = new Scheduler("child");
    parent.addChild(child);
    owner.setDirty(false);

    {
      boolean result = parent.removeChild(child);
      assertThat(result, is(true));
      assertThat(parent.getChildren().size(), is(0));
      assertThat(child.getParent(), is(nullValue()));
      assertThat(owner.isDirty(), is(true));
    }

    {
      boolean result = parent.removeChild(child);
      assertThat(result, is(false));
    }
  }

  @Test
  public void testAddHost() {
    Host host = new Host("child", owner.getAvailabilityZone(), "child", 1234);
    parent.addHost(host);

    assertThat(parent.getHosts().size(), is(1));
    assertThat(parent.getHosts(), hasEntry("child", host));
    assertThat(host.getParentScheduler(), is(parent));
    assertThat(owner.isDirty(), is(true));
  }

  @Test
  public void testRemoveHost() {
    Host host = new Host("child", owner.getAvailabilityZone(), "child", 1234);
    parent.addHost(host);
    owner.setDirty(false);

    {
      boolean result = parent.removeHost(host);
      assertThat(result, is(true));
      assertThat(parent.getHosts().size(), is(0));
      assertThat(host.getParentScheduler(), is(nullValue()));
      assertThat(owner.isDirty(), is(true));
    }

    {
      boolean result = parent.removeHost(host);
      assertThat(result, is(false));
    }
  }

  @Test
  public void testRemoveAllChildren() {
    Scheduler child1 = new Scheduler("sc1");
    Scheduler child2 = new Scheduler("sc2");

    parent.addChild(child1);
    parent.addChild(child2);

    parent.removeAllChildren();

    assertThat(child1.getParent(), is(nullValue()));
    assertThat(child2.getParent(), is(nullValue()));
    assertThat(parent.getChildren().size(), is(0));
    assertThat(parent.getOwner().isDirty(), is(true));
  }

  @Test
  public void testRemoveAllHosts() {
    Host host1 = new Host("foo", new AvailabilityZone("baz"), "foo", 1234);
    Host host2 = new Host("bar", new AvailabilityZone("baz"), "bar", 1234);
    host1.setDirty(false);
    host2.setDirty(false);

    parent.addHost(host1);
    parent.addHost(host2);

    parent.removeAllHosts();

    assertThat(host1.getParentScheduler(), is(nullValue()));
    assertThat(host2.getParentScheduler(), is(nullValue()));
    assertThat(host1.isDirty(), is(false));
    assertThat(host2.isDirty(), is(false));
    assertThat(parent.getOwner().isDirty(), is(true));
  }

  @Test
  public void testAddHostWithConstraints() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo", new AvailabilityZone("baz"), datastores, networks, "foo", 1234);
    parent.addHost(host1);

    assertThat(parent.getConstraintSet().size(), is(3));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));

    assertThat(parent.getConstraintSet().contains(tConst), is(true));
  }

  @Test
  public void testAddHostWithGrandParent() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo", new AvailabilityZone("baz"), datastores, networks, "foo", 1234);
    parent.addHost(host1);
    Scheduler grandparent = new Scheduler("grandparent");

    grandparent.addChild(parent);

    assertThat(grandparent.getConstraintSet().size(), is(3));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(grandparent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(grandparent.getConstraintSet().contains(tConst), is(true));
  }

  @Test
  public void testLeafSchedulerWithMultipleHostConstraints() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo1", new AvailabilityZone("baz"), datastores, networks, "foo1", 1234);
    Host host2 = new Host("foo2", new AvailabilityZone("baz"), datastores, networks, "foo2", 1234);
    parent.addHost(host1);
    parent.addHost(host2);

    assertThat(parent.getConstraintSet().size(), is(4));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host2.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));
  }

  @Test
  public void testGarndParentWithTwoSchedulers() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo1", new AvailabilityZone("baz"), datastores, networks, "foo1", 1234);

    Set<Datastore> datastores2 = new HashSet<>();
    datastores2.add(new Datastore("datastore2", DatastoreType.SHARED_VMFS));
    Host host2 = new Host("foo2", new AvailabilityZone("baz"), datastores2, networks, "foo2", 1234);

    Scheduler grandParent = new Scheduler("grandParent");
    Scheduler child1 = new Scheduler("child1");
    Scheduler child2 = new Scheduler("child2");

    child1.addHost(host1);
    child2.addHost(host2);

    grandParent.addChild(child1);
    grandParent.addChild(child2);

    assertThat(grandParent.getConstraintSet().size(), is(5));
    assertThat(child1.getConstraintSet().size(), is(3));
    assertThat(child2.getConstraintSet().size(), is(3));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host2.getId()));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore2"));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));
  }

  @Test
  public void testSchedulerRemoveHost() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo1", new AvailabilityZone("baz"), datastores, networks, "foo1", 1234);
    Host host2 = new Host("foo2", new AvailabilityZone("baz"), datastores, networks, "foo2", 1234);
    parent.addHost(host1);
    parent.addHost(host2);

    assertThat(parent.getConstraintSet().size(), is(4));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host2.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    parent.removeHost(host1);

    assertThat(parent.getConstraintSet().size(), is(3));

    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host2.getId()));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(parent.getConstraintSet().contains(tConst), is(true));
  }

  @Test
  public void testAddHostWithRemoveChildScheduler() {
    datastores.add(new Datastore("datastore1", DatastoreType.SHARED_VMFS));
    Host host1 = new Host("foo", new AvailabilityZone("baz"), datastores, networks, "foo", 1234);
    parent.addHost(host1);
    Scheduler grandParent = new Scheduler("grandParent");

    grandParent.addChild(parent);

    assertThat(grandParent.getConstraintSet().size(), is(3));

    ResourceConstraint tConst;
    tConst = new ResourceConstraint(ResourceConstraintType.HOST, ImmutableList.of(host1.getId()));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));

    tConst = new ResourceConstraint(ResourceConstraintType.DATASTORE, ImmutableList.of("datastore1"));
    assertThat(grandParent.getConstraintSet().contains(tConst), is(true));

    grandParent.removeChild(parent);

    assertThat(grandParent.getConstraintSet().size(), is(0));
  }
}
