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

package com.vmware.photon.controller.cloudstore.xenon.task;

import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.IpLeaseService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Class implementing service to delete dangling IpLeaseService from the cloud store.
 * Service will query IpLeaseService with pagination, and delete the dangling IpLeaseService.
 */
public class IpLeaseDeleteService extends StatefulService {

  public static final String FACTORY_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.CLOUDSTORE_ROOT
      + "/ip-leases-deletes";
  public static final int DEFAULT_PAGE_LIMIT = 1000;

  public static FactoryService createFactory() {
    return FactoryService.create(IpLeaseDeleteService.class, IpLeaseDeleteService.State.class);
  }

  public IpLeaseDeleteService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  public static State buildStartPatch() {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = TaskState.TaskStage.STARTED;
    return s;
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State state = start.getBody(State.class);
    initializeState(state);
    validateState(state);
    processStart(state);

    start.setBody(state).complete();
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    URI referer = patch.getReferer();

    validatePatch(currentState, patchState, referer);
    applyPatch(currentState, patchState);
    validateState(currentState);
    patch.complete();
    processPatch(currentState);
  }

  /**
   * Does any additional processing after the start operation has been completed.
   *
   * @param current
   */

  private void processStart(final State current) {
    if (current.isSelfProgressionDisabled) {
      ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      return;
    }

    try {
      if (!isFinalStage(current) && current.nextPageLink == null) {
        Operation queryIpLeasePagination = Operation
            .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
            .setBody(buildIpLeaseQuery(current));
        queryIpLeasePagination
            .setCompletion(((op, failure) -> {
              if (failure != null) {
                failTask(failure);
                return;
              }
              ServiceDocumentQueryResult results = op.getBody(QueryTask.class).results;
              if (results.nextPageLink != null) {
                current.nextPageLink = results.nextPageLink;
                sendStageProgressPatch(current);
              } else {
                ServiceUtils.logInfo(this, "No ip lease found, deleting DhcpSubnetService.");
                Operation deleteOperation = Operation
                    .createDelete(UriUtils.buildUri(getHost(), DhcpSubnetService.FACTORY_LINK + "/" + current.subnetId))
                    .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
                deleteOperation.setCompletion(
                    (operation, ex) -> {
                      if (ex != null) {
                        if (operation.getStatusCode() != 404) {
                          finishTask(current);
                          return;
                        }
                        failTask(ex);
                        return;
                      }
                      finishTask(current);
                    }
                ).sendWith(this);
              }
            })).sendWith(this);
      } else {
        sendStageProgressPatch(current);
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(final State current) {
    try {
      switch (current.taskState.stage) {
        case CREATED:
          break;
        case STARTED:
          processIpLeaseDocuments(current);
          break;

        case FAILED:
        case FINISHED:
        case CANCELLED:
          break;

        default:
          this.failTask(
              new IllegalStateException(
                  String.format("Un-expected stage: %s", current.taskState.stage))
          );
      }
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Retrieves the first page of IpLeaseService and kicks of the subsequent processing.
   *
   * @param current
   */
  private void processIpLeaseDocuments(final State current) {
    if (current.nextPageLink == null) {
      finishTask(current);
      return;
    }

    Operation getOnePageOfIpLeaseDocuments =
        Operation.createGet(UriUtils.buildUri(getHost(), current.nextPageLink));
    getOnePageOfIpLeaseDocuments
        .setCompletion((op, throwable) -> {
          if (throwable != null) {
            failTask(throwable);
            return;
          }
          current.nextPageLink = op.getBody(QueryTask.class).results.nextPageLink;
          List<IpLeaseService.State> ipLeaseList =
              parseIpLeaseServiceQueryResults(op.getBody(QueryTask.class));
          deleteIpLeaseDocuments(current, ipLeaseList);
        })
        .sendWith(this);
  }

  /**
   * Delete the ip lease documents.
   *
   * @param ipLeaseList
   */
  private void deleteIpLeaseDocuments(final State current, List<IpLeaseService.State> ipLeaseList) {
    if (ipLeaseList.size() == 0) {
      ServiceUtils.logInfo(this, "No Ip Lease documents found any more.");
      finishTask(current);
      return;
    }

    for (IpLeaseService.State ipLease : ipLeaseList) {
      deleteIpLease(ipLease);
    }
    finishTask(current);
  }

  /**
   * Delete one ip lease document.
   *
   * @param state
   */
  private void deleteIpLease(IpLeaseService.State state) {
    String ipLeaseId = ServiceUtils
        .getIDFromDocumentSelfLink(state.documentSelfLink);
    Operation deleteOperation = Operation
        .createDelete(UriUtils.buildUri(getHost(), IpLeaseService.FACTORY_LINK + "/" + ipLeaseId))
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
    deleteOperation.setCompletion(
        (operation, ex) -> {
          if (ex != null) {
            failTask(ex);
          }
          ServiceUtils.logInfo(this, "Ip Lease document %s has been released.", ipLeaseId);
        }
    ).sendWith(this);
  }

  /**
   * Parse IpLeaseServiec query results.
   *
   * @param result
   */
  private List<IpLeaseService.State> parseIpLeaseServiceQueryResults(QueryTask result) {
    List<IpLeaseService.State> ipLeaseList = new LinkedList<>();

    if (result != null && result.results != null && result.results.documentCount > 0) {
      for (Map.Entry<String, Object> doc : result.results.documents.entrySet()) {
        ipLeaseList.add(
            Utils.fromJson(doc.getValue(), IpLeaseService.State.class));
      }
    }

    return ipLeaseList;
  }

  /**
   * Determines if the task is in a final state.
   *
   * @param s
   * @return
   */
  private boolean isFinalStage(State s) {
    return s.taskState.stage == TaskState.TaskStage.FINISHED ||
        s.taskState.stage == TaskState.TaskStage.FAILED ||
        s.taskState.stage == TaskState.TaskStage.CANCELLED;
  }

  private void finishTask(final State patch) {
    ServiceUtils.logInfo(this, "Finished deleting unreleased ip leases.");
    if (patch.taskState == null) {
      patch.taskState = new TaskState();
    }
    patch.taskState.stage = TaskState.TaskStage.FINISHED;

    this.sendStageProgressPatch(patch);
  }

  /**
   * Moves the service into the FAILED state.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    this.sendStageProgressPatch(buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * Send a patch message to ourselves to update the execution stage.
   *
   * @param state
   */
  private void sendStageProgressPatch(State state) {
    if (state.isSelfProgressionDisabled) {
      ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      return;
    }

    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(state);
    this.sendRequest(patch);
  }

  /**
   * Build a state object that can be used to submit a stage progress
   * self patch.
   *
   * @param stage
   * @param e
   * @return
   */
  private State buildPatch(TaskState.TaskStage stage, Throwable e) {
    State s = new State();
    s.taskState = new TaskState();
    s.taskState.stage = stage;

    if (e != null) {
      s.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return s;
  }

  /**
   * Initialize state with defaults.
   *
   * @param current
   */
  private void initializeState(State current) {
    InitializationUtils.initialize(current);

    if (current.documentExpirationTimeMicros <= 0) {
      current.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }
  }

  /**
   * Validate service state coherence.
   *
   * @param current
   */
  private void validateState(State current) {
    ValidationUtils.validateState(current);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   */
  private State applyPatch(State current, State patch) {
    ServiceUtils.logInfo(this, "Moving to stage %s", patch.taskState.stage);
    if (patch.nextPageLink == null) {
      current.nextPageLink = null;
    }
    PatchUtils.patchState(current, patch);
    return current;
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param current Supplies the start state object.
   * @param patch   Supplies the patch state object.
   * @param referer
   */
  private void validatePatch(State current, State patch, URI referer) {
    checkNotNull(current.taskState.stage);
    checkNotNull(patch.taskState.stage);

    if (current.taskState.stage != TaskState.TaskStage.CREATED &&
        referer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage, ignores patch from TaskSchedulerService");
    }

    ValidationUtils.validatePatch(current, patch);

    // Patches cannot be applied to documents in terminal states.
    checkState(current.taskState.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patch.taskState.stage.ordinal() >= current.taskState.stage.ordinal());
  }

  private QueryTask buildIpLeaseQuery(State s) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(IpLeaseService.State.class));

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    QueryTask.Query subnetIdClause = new QueryTask.Query()
        .setTermPropertyName("subnetId")
        .setTermMatchValue(s.subnetId);

    querySpec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(subnetIdClause);

    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    querySpec.resultLimit = s.pageLimit;
    return QueryTask.create(querySpec).setDirect(true);
  }

  /**
   * Durable service state data.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * Service execution stage.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * Subnet ID.
     */
    @NotNull
    public String subnetId;
    /**
     * The link to next page.
     */
    public String nextPageLink;

    /**
     * The page limit for querying IpLeaseService.
     */
    @DefaultInteger(value = DEFAULT_PAGE_LIMIT)
    public int pageLimit;

    /**
     * Flag that controls if we should self patch to make forward progress.
     */
    @DefaultBoolean(value = false)
    public Boolean isSelfProgressionDisabled;
  }
}
