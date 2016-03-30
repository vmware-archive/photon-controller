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

package com.vmware.photon.controller.api;

import com.vmware.photon.controller.api.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link DeploymentDeployOperation}.
 */
public class DeploymentDeployOperationTest {
  private Validator validator = new Validator();

  private DeploymentDeployOperation operation;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = false)
  private void dummy() {
  }

  private DeploymentDeployOperation createValidOperation() {
    DeploymentDeployOperation op = new DeploymentDeployOperation();
    op.setDesiredState(DeploymentState.READY.name());
    return op;
  }

  /**
   * Tests for DeploymentDeployOperation#desiredState.
   */
  public class DesiredStateTest {

    @BeforeMethod
    public void setUp() {
      operation = createValidOperation();
    }

    @Test
    public void testDefault() {
      operation = new DeploymentDeployOperation();
      assertThat(operation.getDesiredState(), is(DeploymentState.PAUSED.name()));
    }

    @Test
    public void testValidOperation() {
      ImmutableList<String> violations = validator.validate(operation);

      assertTrue(violations.isEmpty());
    }

    @Test
    public void testInvalidOperation() {
      operation.setDesiredState("foo");
      ImmutableList<String> violations = validator.validate(operation);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0),
          is("desiredState must match \"READY|PAUSED|BACKGROUND_PAUSED\" (was foo)"));
    }
  }
}
