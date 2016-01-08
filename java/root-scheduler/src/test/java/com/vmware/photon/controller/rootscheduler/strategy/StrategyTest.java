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

package com.vmware.photon.controller.rootscheduler.strategy;

import com.vmware.photon.controller.resource.gen.ResourceConstraint;
import com.vmware.photon.controller.resource.gen.ResourceConstraintType;
import com.vmware.photon.controller.rootscheduler.service.ManagedScheduler;
import com.vmware.photon.controller.scheduler.gen.PlaceParams;

import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link Strategy}.
 */
public class StrategyTest extends PowerMockTestCase {

  /**
   * Extends Strategy for this test.
   */
  public class TestStrategy extends Strategy {
    @Override
    public void init(int value) {
    }

    @Override
    public List<ManagedScheduler> filterChildren(
        PlaceParams placeParams,
        List<ManagedScheduler> schedulers,
        Set<ResourceConstraint> constraintSet) {
      return null;
    }

    public int testValidateConstraints(
        Set<ResourceConstraint> hostResources,
        Set<ResourceConstraint> constraints) {
      return validateConstraints(hostResources, constraints);
    }
  }

  TestStrategy strategy = new TestStrategy();
  Set<ResourceConstraint> hostResources = new HashSet<>();

  @BeforeMethod
  public void setUp() throws Exception {

    ResourceConstraint resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.DATASTORE);
    resource.setValues(Arrays.asList("Datastore1", "Datastore2",
        "Datastore2", "Datastore3"));
    hostResources.add(resource);

    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.NETWORK);
    resource.setValues(Arrays.asList("Vm Network1", "Vm Network2", "Vm Network2", "Vm Network3"));
    hostResources.add(resource);

    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.DATASTORE_TAG);
    resource.setValues(Arrays.asList("Datastore Tag 1"));
    hostResources.add(resource);

    resource = new ResourceConstraint();
    resource.setType(ResourceConstraintType.HOST);
    resource.setValues(Arrays.asList("Host1", "Host2", "Host3", "Host4"));
    hostResources.add(resource);
  }

  @Test
  public void testValidateConstraints() throws Exception {
    // Check OR among values, should succeed
    Set<ResourceConstraint> constraints = new HashSet<>();
    ResourceConstraint constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1", "Vm Network2", "Vm Network10"));
    constraints.add(constraint);
    int res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(Integer.MAX_VALUE));

    // Check AND among constraints, should fail
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1"));
    constraints.add(constraint);
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network10"));
    constraints.add(constraint);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(0));

    // Check OR among values + AND among constraints, should succeed
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1", "Vm Network10"));
    constraints.add(constraint);
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.DATASTORE_TAG);
    constraint.setValues(Arrays.asList("Datastore Tag 10", "Datastore Tag 1"));
    constraints.add(constraint);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(Integer.MAX_VALUE));

    // Check OR among values + AND among constraints, should fail
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1", "Vm Network2"));
    constraints.add(constraint);
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.DATASTORE_TAG);
    constraint.setValues(Arrays.asList("Datastore Tag 10"));
    constraints.add(constraint);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(0));

    // Check OR among values + AND among constraints, should succeed
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1", "Vm Network2", "Vm Network3"));
    constraints.add(constraint);
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.DATASTORE);
    constraint.setValues(Arrays.asList("Datastore1", "Datastore2"));
    constraints.add(constraint);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(Integer.MAX_VALUE));
  }

  @Test
  public void testValidateNegativeConstraints() throws Exception {
    // Check OR among values, should match all the hosts, count should be 4
    Set<ResourceConstraint> constraints = new HashSet<>();
    ResourceConstraint constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.HOST);
    constraint.setValues(Arrays.asList("Host10", "Host11", "Host12"));
    constraint.setNegative(true);
    constraints.add(constraint);
    int res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(4));

    // Check OR among values, should match no hosts, count should be 0
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.HOST);
    constraint.setValues(Arrays.asList("Host1", "Host2", "Host3", "Host4"));
    constraint.setNegative(true);
    constraints.add(constraint);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(0));

    // Check OR among values + AND among constraints, 3 matched network
    // and 2 excluded hosts, should return 2
    constraints = new HashSet<>();
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.NETWORK);
    constraint.setValues(Arrays.asList("Vm Network1", "Vm Network2", "Vm Network3"));
    constraints.add(constraint);
    constraint = new ResourceConstraint();
    constraint.setType(ResourceConstraintType.HOST);
    constraint.setValues(Arrays.asList("Host1", "Host2"));
    constraints.add(constraint);
    constraint.setNegative(true);
    res = strategy.testValidateConstraints(hostResources, constraints);
    assertThat(res, is(2));
  }
}
