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

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Task removes the compute service instances.
 */
public class ResourceRemovalTaskService extends StatefulService {

  public static final long DEFAULT_TIMEOUT_MICROS = TimeUnit.MINUTES.toMicros(10);

  /**
   * SubStage.
   */
  public static enum SubStage {
    WAITING_FOR_QUERY_COMPLETION,
    ISSUING_DELETES,
    FINISHED,
    FAILED
  }

  /**
   * Represents the state of the removal task.
   */
  public static class ResourceRemovalTaskState extends ServiceDocument {

    /**
     * Task state.
     */
    public TaskState taskInfo = new TaskState();

    /**
     * Task sub stage.
     */
    public SubStage taskSubStage;

    /**
     * Query specification used to find the compute resources for removal.
     */
    public QueryTask.QuerySpecification resourceQuerySpec;

    /**
     * Set by service. Link to resource query task.
     */
    public String resourceQueryLink;

    /**
     * For testing instance service deletion.
     */
    public boolean isMockRequest;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;

    /**
     * The error threshold.
     */
    public double errorThreshold;
  }

  public ResourceRemovalTaskService() {
    super(ResourceRemovalTaskState.class);
    super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
    super.toggleOption(Service.ServiceOption.REPLICATION, true);
    super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(Service.ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        start.fail(new IllegalArgumentException("body is required"));
        return;
      }

      ResourceRemovalTaskState state = start.getBody(ResourceRemovalTaskState.class);
      validateState(state);

      if (TaskState.isCancelled(state.taskInfo) || TaskState.isFailed(state.taskInfo)
              || TaskState.isFinished(state.taskInfo)) {
          start.complete();
          return;
      }

      QueryTask q = new QueryTask();
      q.documentExpirationTimeMicros = state.documentExpirationTimeMicros;
      q.querySpec = state.resourceQuerySpec;
      q.documentSelfLink = UUID.randomUUID().toString();
      q.tenantLinks = state.tenantLinks;
      // create the query to find resources
      sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
          .setBody(q)
          .setCompletion((o, e) -> {
            if (e != null) {
              // the task might have expired, with no results every becoming available
              logWarning("Failure retrieving query results: %s", e.toString());
              sendFailureSelfPatch(e);
              return;
            }
          }));

      // we do not wait for the query task creation to know its URI, the URI is created
      // deterministically. The task itself is not complete but we check for that in our state
      // machine
      state.resourceQueryLink = UriUtils.buildUriPath(ServiceUriPaths.CORE_QUERY_TASKS,
          q.documentSelfLink);
      start.complete();

      sendSelfPatch(TaskState.TaskStage.STARTED, state.taskSubStage, null);
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ResourceRemovalTaskState body = patch.getBody(ResourceRemovalTaskState.class);
    ResourceRemovalTaskState currentState = getState(patch);

    if (validateTransitionAndUpdateState(patch, body, currentState)) {
      return;
    }

    patch.complete();

    switch (currentState.taskInfo.stage) {
      case CREATED:
        break;
      case STARTED:
        handleStagePatch(currentState, null);
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
  }

  private void handleStagePatch(ResourceRemovalTaskState currentState, QueryTask queryTask) {

    if (queryTask == null) {
      getQueryResults(currentState);
      return;
    }
    adjustStat(currentState.taskSubStage.toString(), 1);

    switch (currentState.taskSubStage) {
      case WAITING_FOR_QUERY_COMPLETION:
        if (TaskState.isFailed(queryTask.taskInfo)) {
          logWarning("query task failed: %s", Utils.toJsonHtml(queryTask.taskInfo.failure));
          currentState.taskInfo.stage = TaskState.TaskStage.FAILED;
          currentState.taskInfo.failure = queryTask.taskInfo.failure;
          sendSelfPatch(currentState);
          return;
        }
        if (TaskState.isFinished(queryTask.taskInfo)) {

          ResourceRemovalTaskState newState = new ResourceRemovalTaskState();
          if (queryTask.results.documentLinks.size() == 0) {
            newState.taskInfo.stage = TaskState.TaskStage.FINISHED;
            newState.taskSubStage = SubStage.FINISHED;
          } else {
            newState.taskInfo.stage = TaskState.TaskStage.STARTED;
            newState.taskSubStage = SubStage.ISSUING_DELETES;
          }
          sendSelfPatch(newState);
          return;
        }

        logInfo("Resource query not complete yet, retrying");
        getHost().schedule(() -> {
          getQueryResults(currentState);
        }, 1, TimeUnit.SECONDS);
        break;
      case ISSUING_DELETES:
        doInstanceDeletes(currentState, queryTask, null);
        break;
      case FAILED:
        break;
      case FINISHED:
        break;
      default:
        break;
    }
  }

  private void doInstanceDeletes(
      ResourceRemovalTaskState currentState,
      QueryTask queryTask,
      String subTaskLink) {

    int resourceCount = queryTask.results.documentLinks.size();
    if (subTaskLink == null) {
      createSubTaskForDeleteCallbacks(currentState, resourceCount, queryTask);
      return;
    }

    logInfo("Starting delete of %d compute resources using sub task %s", resourceCount,
        subTaskLink);
    // for each compute resource link in the results, expand it with the description, and issue
    // a DELETE request to its associated instance service.

    for (String resourceLink : queryTask.results.documentLinks) {
      URI u = ComputeService.ComputeStateWithDescription.buildUri(UriUtils.buildUri(
          getHost(),
          resourceLink));
      sendRequest(Operation
          .createGet(u)
          .setCompletion((o, e) -> {
            if (e != null) {
              // we do not fail task if one delete failed ...
              // send a FINISHED patch which is what a sub task would do. Since the
              // current state
              // is still at REMOVING_RESOURCES, we will just increment a counter
              ResourceRemovalTaskState subTaskPatchBody = new ResourceRemovalTaskState();
              subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.FAILED;
              sendPatch(UriUtils.buildUri(getHost(), subTaskLink), subTaskPatchBody);
              return;
            }
            sendInstanceDelete(resourceLink, subTaskLink, o, currentState.isMockRequest);
          }));
    }
    sendRequest(Operation.createDelete(this, currentState.resourceQueryLink));
  }

  /**
   * Before we proceed with issuing DELETE requests to the instance services we must create a sub
   * task that will track the DELETE completions. The instance service will issue a PATCH with
   * TaskStage.FINISHED, for every PATCH we send it, to delete the compute resource
   */
  private void createSubTaskForDeleteCallbacks(ResourceRemovalTaskState currentState,
                                               int resourceCount,
                                               QueryTask queryTask) {
    ComputeSubTaskService.ComputeSubTaskState subTaskInitState = new ComputeSubTaskService.ComputeSubTaskState();
    ResourceRemovalTaskState subTaskPatchBody = new ResourceRemovalTaskState();
    subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.FINISHED;
    subTaskPatchBody.taskSubStage = SubStage.FINISHED;
    // tell the sub task with what to patch us, on completion
    subTaskInitState.parentPatchBody = Utils.toJson(subTaskPatchBody);
    subTaskInitState.parentTaskLink = getSelfLink();
    subTaskInitState.completionsRemaining = resourceCount;
    subTaskInitState.errorThreshold = currentState.errorThreshold;
    subTaskInitState.tenantLinks = currentState.tenantLinks;
    Operation startPost = Operation
        .createPost(this, UUID.randomUUID().toString())
        .setBody(subTaskInitState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                logWarning("Failure creating sub task: %s", Utils.toString(e));
                this.sendSelfPatch(TaskState.TaskStage.FAILED, SubStage.FAILED, e);
                return;
              }
              ComputeSubTaskService.ComputeSubTaskState body = o
                  .getBody(ComputeSubTaskService.ComputeSubTaskState.class);
              // continue with deletes, passing the sub task link
              doInstanceDeletes(currentState, queryTask, body.documentSelfLink);
            });
    getHost().startService(startPost, new ComputeSubTaskService());
  }

  private void sendInstanceDelete(String resourceLink, String subTaskLink, Operation o,
                                  boolean isMockRequest) {
    ComputeService.ComputeStateWithDescription chd = o
        .getBody(ComputeService.ComputeStateWithDescription.class);
    ComputeInstanceRequest deleteReq = new ComputeInstanceRequest();
    deleteReq.computeReference = UriUtils.buildUri(getHost(), resourceLink);
    deleteReq.provisioningTaskReference = UriUtils.buildUri(getHost(), subTaskLink);
    deleteReq.requestType = ComputeInstanceRequest.InstanceRequestType.DELETE;
    deleteReq.isMockRequest = isMockRequest;
    sendRequest(Operation
        .createPatch(chd.description.instanceAdapterReference)
        .setBody(deleteReq)
        .setCompletion(
            (deleteOp, e) -> {
              if (e != null) {
                logWarning("PATCH to instance service %s, failed: %s",
                    deleteOp.getUri(), e.toString());
                ResourceRemovalTaskState subTaskPatchBody = new ResourceRemovalTaskState();
                subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.FAILED;
                sendPatch(UriUtils.buildUri(getHost(), subTaskLink),
                    subTaskPatchBody);
                return;
              }
            }));
  }

  public void getQueryResults(
      ResourceRemovalTaskState currentState) {
    sendRequest(Operation.createGet(this, currentState.resourceQueryLink)
        .setCompletion((o, e) -> {
          if (e != null) {
            // the task might have expired, with no results every becoming available
            logWarning("Failure retrieving query results: %s", e.toString());
            sendFailureSelfPatch(e);
            return;
          }

          QueryTask rsp = o.getBody(QueryTask.class);
          handleStagePatch(currentState, rsp);
        }));
  }

  private boolean validateTransitionAndUpdateState(Operation patch,
                                                   ResourceRemovalTaskState body,
                                                   ResourceRemovalTaskState currentState) {

    TaskState.TaskStage currentStage = currentState.taskInfo.stage;
    SubStage currentSubStage = currentState.taskSubStage;

    if (body.taskInfo == null || body.taskInfo.stage == null) {
      patch.fail(new IllegalArgumentException("taskInfo and stage are required"));
      return true;
    }

    if (currentStage.ordinal() > body.taskInfo.stage.ordinal()) {
      patch.fail(new IllegalArgumentException("stage can not move backwards:"
          + body.taskInfo.stage));
      return true;
    }

    if (currentStage.ordinal() == body.taskInfo.stage.ordinal() &&
        (body.taskSubStage == null
            || currentSubStage.ordinal() > body.taskSubStage.ordinal())) {
      patch.fail(new IllegalArgumentException("subStage can not move backwards:"
          + body.taskSubStage));
      return true;
    }

    currentState.taskInfo.stage = body.taskInfo.stage;
    currentState.taskSubStage = body.taskSubStage;

    logInfo("Moving from %s(%s) to %s(%s)",
        currentSubStage,
        currentStage,
        body.taskSubStage, currentState.taskInfo.stage);

    return false;
  }

  private void sendFailureSelfPatch(Throwable e) {
    sendSelfPatch(TaskState.TaskStage.FAILED, SubStage.FAILED, e);
  }

  private void sendSelfPatch(TaskState.TaskStage stage, SubStage subStage, Throwable e) {
    ResourceRemovalTaskState body = new ResourceRemovalTaskState();
    body.taskInfo = new TaskState();
    body.taskInfo.stage = stage;
    body.taskSubStage = subStage;
    if (e != null) {
      body.taskInfo.failure = Utils.toServiceErrorResponse(e);
      logWarning("Patching to failed: %s", Utils.toString(e));
    }
    sendSelfPatch(body);
  }

  private void sendSelfPatch(Object body) {
    sendPatch(getUri(), body);
  }

  private void sendPatch(URI uri, Object body) {
    Operation patch = Operation
        .createPatch(uri)
        .setBody(body)
        .setCompletion(
            (o, ex) -> {
              if (ex != null) {
                logWarning("Self patch failed: %s", Utils.toString(ex));
              }
            });
    sendRequest(patch);
  }

  public static void validateState(ResourceRemovalTaskState state) {
    if (state.resourceQuerySpec == null) {
      throw new IllegalArgumentException(
          "resourceQuerySpec is required");
    }

    if (state.taskInfo == null || state.taskInfo.stage == null) {
      state.taskInfo = new TaskState();
      state.taskInfo.stage = TaskState.TaskStage.CREATED;
    }

    if (state.taskSubStage == null) {
      state.taskSubStage = SubStage.WAITING_FOR_QUERY_COMPLETION;
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
