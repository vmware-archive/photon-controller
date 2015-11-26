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

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Task used to Enumerate resources on a given compute host.
 */
public class ResourceEnumerationTaskService extends StatefulService {

  public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES.toMicros(10);

  /**
   * This class defines the document state associated with a single
   * ResourceEnumerationTaskService instance.
   */
  public static class ResourceEnumerationTaskState extends ServiceDocument {
    /**
     * The state of the current task.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Reference URI to the Compute Description that will be used
     * for the compute instances created by the adapter.
     */
    public String computeDescriptionLink;

    /**
     * Reference URI to the resource pool.
     */
    public String resourcePoolLink;

    /**
     * Reference URI to the parent Compute instance.
     */
    public String parentComputeLink;

    /**
     * Enumeration Action Start, stop, refresh.
     */
    public EnumerationAction enumerationAction;

    /**
     * URI reference to resource pool management site.
     */
    public URI adapterManagementReference;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;
  }

  public ResourceEnumerationTaskService() {
    super(ResourceEnumerationTaskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        start.fail(new IllegalArgumentException("body is required"));
        return;
      }

      ResourceEnumerationTaskState state = getBody(start);
      validateState(state);
      start.setBody(state).complete();

      ComputeEnumerateResourceRequest req = new ComputeEnumerateResourceRequest();
      req.resourcePoolLink = state.resourcePoolLink;
      req.computeDescriptionLink = state.computeDescriptionLink;
      req.adapterManagementReference = state.adapterManagementReference;
      req.parentComputeLink = state.parentComputeLink;
      req.enumerationAction = state.enumerationAction;
      req.enumerationTaskReference = UriUtils.buildUri(getHost(),
          state.documentSelfLink);

      // Patch the enumerate service URI from the CHD
      Operation.CompletionHandler descriptionCompletion = (o, ex) -> {
        if (ex != null) {
          sendSelfPatch(TaskState.TaskStage.FAILED, ex);
          start.fail(ex);
          return;
        }

        ComputeDescriptionService.ComputeDescription description = o
            .getBody(ComputeDescriptionService.ComputeDescription.class);
        sendRequest(Operation
            .createPatch(description.enumerationAdapterReference)
            .setTargetReplicated(true)
            .setBody(req));
      };

      URI computeHost = UriUtils.buildUri(getHost(), state.computeDescriptionLink);
      sendRequest(Operation
          .createGet(computeHost)
          .setTargetReplicated(true)
          .setCompletion(descriptionCompletion));
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  public ResourceEnumerationTaskState getBody(Operation op) {
    ResourceEnumerationTaskState body = op.getBody(ResourceEnumerationTaskState.class);
    return body;
  }

  @Override
  public void handlePatch(Operation patch) {
    ResourceEnumerationTaskState body = getBody(patch);
    ResourceEnumerationTaskState currentState = getState(patch);

    logInfo("Moving from %s to %s", currentState.taskInfo.stage.toString(),
        body.taskInfo.stage.toString());
    // go-dcp will actuate the state. When the document is created, the enumeration service in
    // go-dcp will be PATCH'ed with the enumeration request, then it will PATCH back CREATED
    // followed by FINISHED or FAILED when complete
    switch (currentState.taskInfo.stage) {
      case CREATED:
        break;
      case STARTED:
        break;
      case FINISHED:
        logInfo("task is complete");
        break;
      case FAILED:
      case CANCELLED:
        break;
      default:
        break;
    }

    currentState.taskInfo = body.taskInfo;
    patch.setBody(currentState).complete();
  }

  private void sendSelfPatch(TaskState.TaskStage state, Throwable e) {
    ResourceEnumerationTaskState body = new ResourceEnumerationTaskState();
    body.taskInfo.stage = state;
    if (e != null) {
      body.taskInfo.failure = Utils.toServiceErrorResponse(e);
    }

    Operation patch = Operation
        .createPatch(getUri())
        .setBody(body)
        .setCompletion(
            (o, ex) -> {
              if (ex != null) {
                logWarning("Self patch failed: %s", Utils.toString(ex));
              }
            });
    sendRequest(patch);
  }

  public static void validateState(ResourceEnumerationTaskState state) {

    if (state.computeDescriptionLink == null) {
      throw new IllegalArgumentException("computeDescriptionLink is required.");
    }

    if (state.resourcePoolLink == null) {
      throw new IllegalArgumentException("resourcePoolLink is required.");
    }

    if (state.adapterManagementReference == null) {
      throw new IllegalArgumentException("adapterManagementReference is required.");
    }

    if (state.taskInfo == null || state.taskInfo.stage == null) {
      state.taskInfo = new TaskState();
      state.taskInfo.stage = TaskState.TaskStage.CREATED;
    }

    if (state.documentExpirationTimeMicros == 0) {
      state.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + DEFAULT_TIMEOUT_MICROS;
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
