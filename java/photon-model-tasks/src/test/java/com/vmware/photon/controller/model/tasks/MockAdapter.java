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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeBootRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;

/**
 * Mock adapters used by photon model task tests.
 */
public class MockAdapter {
  public static final Class[] FACTORIES = {
      MockSuccessInstanceAdapter.class,
      MockFailureInstanceAdapter.class,
      MockSuccessBootAdapter.class,
      MockFailureBootAdapter.class
  };

  /**
   * Mock instance adapter that always succeeds.
   */
  public static class MockSuccessInstanceAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_success_instance_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);
          ProvisionComputeTaskService.ProvisionComputeTaskState provisioningTaskBody =
              new ProvisionComputeTaskService.ProvisionComputeTaskState();
          provisioningTaskBody.taskInfo = new TaskState();
          provisioningTaskBody.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisioningTaskBody));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock instance adapter that always fails.
   */
  public static class MockFailureInstanceAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_failure_instance_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);
          ProvisionComputeTaskService.ProvisionComputeTaskState provisioningTaskBody =
              new ProvisionComputeTaskService.ProvisionComputeTaskState();
          provisioningTaskBody.taskInfo = new TaskState();
          provisioningTaskBody.taskInfo.stage = TaskState.TaskStage.FAILED;
          provisioningTaskBody.taskInfo.failure = ServiceErrorResponse.create(new Exception(), 500);
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisioningTaskBody));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock boot adapter that always succeeds.
   */
  public static class MockSuccessBootAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_success_boot_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeBootRequest request = op.getBody(ComputeBootRequest.class);
          ProvisionComputeTaskService.ProvisionComputeTaskState provisioningTaskBody =
              new ProvisionComputeTaskService.ProvisionComputeTaskState();
          provisioningTaskBody.taskInfo = new TaskState();
          provisioningTaskBody.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisioningTaskBody));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock boot adapter that always fails.
   */
  public static class MockFailureBootAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_failure_boot_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeBootRequest request = op.getBody(ComputeBootRequest.class);
          ProvisionComputeTaskService.ProvisionComputeTaskState provisioningTaskBody =
              new ProvisionComputeTaskService.ProvisionComputeTaskState();
          provisioningTaskBody.taskInfo = new TaskState();
          provisioningTaskBody.taskInfo.stage = TaskState.TaskStage.FAILED;
          provisioningTaskBody.taskInfo.failure = ServiceErrorResponse.create(new Exception(), 500);
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisioningTaskBody));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }
}
