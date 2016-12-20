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

package com.vmware.photon.controller.api.model;

import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link VmOperation}.
 */
public class VmOperationTest {

  private Validator validator = new Validator();

  private VmOperation vmOperation;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  private VmOperation createValidVmOperation() {
    VmOperation vmOperation = new VmOperation();
    vmOperation.setOperation(Operation.START_VM.toString());
    return vmOperation;
  }

  /**
   * Tests for {@link VmOperation#operation}.
   */
  public class OperationTest {

    @BeforeMethod
    public void setUp() {
      vmOperation = createValidVmOperation();
    }

    @Test
    public void testValidOperation() {
      ImmutableList<String> violations = validator.validate(vmOperation);

      assertTrue(violations.isEmpty());
    }

    @Test
    public void testInvalidOperation() {
      vmOperation.setOperation("foo");
      ImmutableList<String> violations = validator.validate(vmOperation);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0),
          is("operation must match \"START_VM|STOP_VM|RESTART_VM|SUSPEND_VM|RESUME_VM\" (was foo)"));
    }
  }

}
