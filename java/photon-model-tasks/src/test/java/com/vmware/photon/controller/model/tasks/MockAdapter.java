/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.FirewallInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.NetworkInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.SnapshotRequest;
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
      MockFailureBootAdapter.class,
      MockSuccessEnumerationAdapter.class,
      MockFailureEnumerationAdapter.class,
      MockSnapshotSuccessAdapter.class,
      MockSnapshotFailureAdapter.class,
      MockNetworkInstanceSuccessAdapter.class,
      MockNetworkInstanceFailureAdapter.class,
      MockFirewallInstanceSuccessAdapter.class,
      MockFirewallInstanceFailureAdapter.class
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
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(computeSubTaskState));
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
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
          computeSubTaskState.taskInfo.failure = ServiceErrorResponse.create(new Exception(), 500);
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(computeSubTaskState));
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
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(computeSubTaskState));
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
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
          computeSubTaskState.taskInfo.failure = ServiceErrorResponse.create(new Exception(), 500);
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(computeSubTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock Enumeration adapter that always succeeds.
   */
  public static class MockSuccessEnumerationAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_success_enumeration_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);
          ResourceEnumerationTaskService.ResourceEnumerationTaskState patchState =
              new ResourceEnumerationTaskService.ResourceEnumerationTaskState();
          patchState.taskInfo = new TaskState();
          patchState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.enumerationTaskReference)
              .setBody(patchState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock Enumeration adapter that always fails.
   */
  public static class MockFailureEnumerationAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_failure_enumeration_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);
          ResourceEnumerationTaskService.ResourceEnumerationTaskState patchState =
              new ResourceEnumerationTaskService.ResourceEnumerationTaskState();
          patchState.taskInfo = new TaskState();
          patchState.taskInfo.stage = TaskState.TaskStage.FAILED;
          sendRequest(Operation
              .createPatch(request.enumerationTaskReference)
              .setBody(patchState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock snapshot adapter that always succeeds.
   */
  public static class MockSnapshotSuccessAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_snapshot_success_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          SnapshotRequest request = op.getBody(SnapshotRequest.class);
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.snapshotTaskReference)
              .setBody(computeSubTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock snapshot adapter that always fails.
   */
  public static class MockSnapshotFailureAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING + "/mock_snapshot_failure_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          SnapshotRequest request = op.getBody(SnapshotRequest.class);
          ComputeSubTaskService.ComputeSubTaskState computeSubTaskState =
              new ComputeSubTaskService.ComputeSubTaskState();
          computeSubTaskState.taskInfo = new TaskState();
          computeSubTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
          sendRequest(Operation
              .createPatch(request.snapshotTaskReference)
              .setBody(computeSubTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock network instance adapter that always succeeds.
   */
  public static class MockNetworkInstanceSuccessAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING +
        "/mock_network_service_success_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          NetworkInstanceRequest request = op.getBody(NetworkInstanceRequest.class);
          ProvisionNetworkTaskService.ProvisionNetworkTaskState provisionNetworkTaskState =
              new ProvisionNetworkTaskService.ProvisionNetworkTaskState();
          provisionNetworkTaskState.taskInfo = new TaskState();
          provisionNetworkTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisionNetworkTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock network instance adapter that always fails.
   */
  public static class MockNetworkInstanceFailureAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING +
        "/mock_network_service_failure_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          NetworkInstanceRequest request = op.getBody(NetworkInstanceRequest.class);
          ProvisionNetworkTaskService.ProvisionNetworkTaskState provisionNetworkTaskState =
              new ProvisionNetworkTaskService.ProvisionNetworkTaskState();
          provisionNetworkTaskState.taskInfo = new TaskState();
          provisionNetworkTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisionNetworkTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock firewall instance adapter that always succeeds.
   */
  public static class MockFirewallInstanceSuccessAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING +
        "/mock_firewall_service_success_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          FirewallInstanceRequest request = op.getBody(FirewallInstanceRequest.class);
          ProvisionFirewallTaskService.ProvisionFirewallTaskState provisionFirewallTaskState =
              new ProvisionFirewallTaskService.ProvisionFirewallTaskState();
          provisionFirewallTaskState.taskInfo = new TaskState();
          provisionFirewallTaskState.taskInfo.stage = TaskState.TaskStage.FINISHED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisionFirewallTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }

  /**
   * Mock firewall instance adapter that always fails.
   */
  public static class MockFirewallInstanceFailureAdapter extends StatelessService {
    public static final String SELF_LINK = UriPaths.PROVISIONING +
        "/mock_firewall_service_failure_adapter";

    @Override
    public void handleRequest(Operation op) {
      if (!op.hasBody()) {
        op.fail(new IllegalArgumentException("body is required"));
        return;
      }
      switch (op.getAction()) {
        case PATCH:
          FirewallInstanceRequest request = op.getBody(FirewallInstanceRequest.class);
          ProvisionFirewallTaskService.ProvisionFirewallTaskState provisionFirewallTaskState =
              new ProvisionFirewallTaskService.ProvisionFirewallTaskState();
          provisionFirewallTaskState.taskInfo = new TaskState();
          provisionFirewallTaskState.taskInfo.stage = TaskState.TaskStage.FAILED;
          sendRequest(Operation
              .createPatch(request.provisioningTaskReference)
              .setBody(provisionFirewallTaskState));
          break;
        default:
          super.handleRequest(op);
      }
    }
  }
}
