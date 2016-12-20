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
package com.vmware.photon.controller.clustermanager.rolloutplans;

import com.vmware.photon.controller.clustermanager.servicedocuments.NodeType;

import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.UUID;

/**
 * Implements tests for {@link NodeRolloutInput}.
 */
public class NodeRolloutInputTests {

  private NodeRolloutInput buildValidInput() {
    NodeRolloutInput input = new NodeRolloutInput();
    input.nodeCount = 100;
    input.diskFlavorName = "Disk-flavor";
    input.vmFlavorName = "vm-flavor";
    input.imageId = UUID.randomUUID().toString();
    input.projectId = UUID.randomUUID().toString();
    input.nodeType = NodeType.MesosMaster;
    input.clusterId = UUID.randomUUID().toString();
    input.nodeProperties = new HashMap<>();
    input.nodeProperties.put("key", "value");
    return input;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests getVmName method.
   */
  public class ValidateTest {

    @Test
    public void testSuccess() {
      NodeRolloutInput input = buildValidInput();
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullClusterId() {
      NodeRolloutInput input = buildValidInput();
      input.clusterId = null;
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullImageId() {
      NodeRolloutInput input = buildValidInput();
      input.imageId = null;
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullProjectId() {
      NodeRolloutInput input = buildValidInput();
      input.projectId = null;
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullDiskFlavorName() {
      NodeRolloutInput input = buildValidInput();
      input.diskFlavorName = null;
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullVmFlavorName() {
      NodeRolloutInput input = buildValidInput();
      input.vmFlavorName = null;
      input.validate();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNodeProperties() {
      NodeRolloutInput input = buildValidInput();
      input.nodeProperties = null;
      input.validate();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidNodeCount() {
      NodeRolloutInput input = buildValidInput();
      input.nodeCount = 1001;
      input.validate();
    }
  }
}
