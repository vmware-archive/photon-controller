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
import com.vmware.photon.controller.clustermanager.tasks.ClusterWaitTaskService;

import com.google.common.util.concurrent.FutureCallback;
import org.testng.annotations.Test;
import static org.testng.AssertJUnit.fail;

import java.util.HashMap;
import java.util.UUID;

/**
 * Implements tests for {@link WorkersNodeRollout}.
 */
public class WorkersNodeRolloutTests {

  private NodeRolloutInput buildValidInput() {
    NodeRolloutInput input = new NodeRolloutInput();
    input.nodeCount = 100;
    input.diskFlavorName = "Disk-flavor";
    input.vmFlavorName = "vm-flavor";
    input.imageId = UUID.randomUUID().toString();
    input.projectId = UUID.randomUUID().toString();
    input.nodeType = NodeType.MesosWorker;
    input.clusterId = UUID.randomUUID().toString();
    input.nodeProperties = new HashMap<>();
    input.nodeProperties.put("key", "value");
    input.serverAddress = "leaderIp";
    return input;
  }

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests getVmName method.
   */
  public class ValidationTests {

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullService() {
      WorkersNodeRollout rollout = new WorkersNodeRollout();
      rollout.run(null, buildValidInput(), new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(NodeRolloutResult result) {
          fail("Expected to throw NullPointerException");
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Expected to throw NullPointerException");
        }
      });
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullNodeRolloutInput() {
      WorkersNodeRollout rollout = new WorkersNodeRollout();
      rollout.run(new ClusterWaitTaskService(), null, new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(NodeRolloutResult result) {
          fail("Expected to throw NullPointerException");
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Expected to throw NullPointerException");
        }
      });
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullServerAddress() {
      NodeRolloutInput input = buildValidInput();
      input.serverAddress = null;

      WorkersNodeRollout rollout = new WorkersNodeRollout();
      rollout.run(new ClusterWaitTaskService(), input, new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(NodeRolloutResult result) {
          fail("Expected to throw NullPointerException");
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Expected to throw NullPointerException");
        }
      });
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testInvalidNodeRolloutInput() {
      NodeRolloutInput input = buildValidInput();
      input.projectId = null;

      WorkersNodeRollout rollout = new WorkersNodeRollout();
      rollout.run(new ClusterWaitTaskService(), input, new FutureCallback<NodeRolloutResult>() {
        @Override
        public void onSuccess(NodeRolloutResult result) {
          fail("Expected to throw NullPointerException");
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Expected to throw NullPointerException");
        }
      });
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullCallback() {
      WorkersNodeRollout rollout = new WorkersNodeRollout();
      rollout.run(null, buildValidInput(), null);
    }
  }
}
