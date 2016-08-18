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

import com.vmware.photon.controller.cloudstore.xenon.entity.IpLeaseService;
import com.vmware.photon.controller.cloudstore.xenon.entity.VmServiceFactory;
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
 * Class implementing a periodically triggered service to clean up IpLeaseService,
 * in the case that vm has been deleted,
 * but the ownerVmId in IpLeaseService has not been cleared.
 */
public class IpLeaseCleanerService extends StatefulService {

  public static final String FACTORY_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.CLOUDSTORE_ROOT
      + "/ip-leases-cleaners";

  public static final int DEFAULT_PAGE_LIMIT = 1000;

  public static FactoryService createFactory() {
    return FactoryService.create(IpLeaseCleanerService.class, IpLeaseCleanerService.State.class);
  }

  public IpLeaseCleanerService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State state = start.getBody(State.class);
    initializeState(state);
    validateState(state);
    start.setBody(state).complete();

    processStart(state);
  }

  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patch);
    State patchState = patch.getBody(State.class);
    URI referrer = patch.getReferer();

    validatePatch(currentState, patchState, referrer);
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
              current.nextPageLink = results.nextPageLink;
              sendStageProgressPatch(current);
            })).sendWith(this);
      } else {
        sendStageProgressPatch(current);
      }
    } catch (Throwable e) {
      failTask(e);
    }
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
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(final State current) {
    try {
      switch (current.taskState.stage) {
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
          cleanIpLeaseDocuments(current, ipLeaseList);
        })
        .sendWith(this);
  }

  /**
   * Clean the ip lease documents.
   *
   * @param ipLeaseList
   */
  private void cleanIpLeaseDocuments(final State current, List<IpLeaseService.State> ipLeaseList) {
    if (ipLeaseList.size() == 0) {
      ServiceUtils.logInfo(this, "No Ip Lease documents found any more.");
      finishTask(current);
      return;
    }

    for (IpLeaseService.State ipLease : ipLeaseList) {
      cleanIpLease(ipLease);
    }
    sendStageProgressPatch(current);
  }

  /**
   * Clean one ip lease document.
   *
   * @param state
   */
  private void cleanIpLease(IpLeaseService.State state) {
    String ipLeaseId = ServiceUtils
        .getIDFromDocumentSelfLink(state.documentSelfLink);

    Operation getVmOperation =
        Operation
            .createGet(UriUtils.buildUri(getHost(), VmServiceFactory.SELF_LINK + "/" + state.ownerVmId))
            .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));
    getVmOperation.setCompletion(
        (operation, ex) -> {
          if (operation.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
            IpLeaseService.IpLeaseOperationPatch patch =
                new IpLeaseService.IpLeaseOperationPatch(
                    IpLeaseService.IpLeaseOperationPatch.Kind.RELEASE, state.ownerVmId, null);
            Operation patchOperation = Operation
                .createPatch(UriUtils.buildUri(getHost(), IpLeaseService.FACTORY_LINK + "/" + ipLeaseId))
                .setBody(patch)
                .setReferer(UriUtils.buildUri(getHost(), getSelfLink()));

            patchOperation.setCompletion(
                (op, t) -> {
                  if (t != null) {
                    failTask(t);
                  }
                  ServiceUtils.logInfo(this, "Ip Lease document %s has been released.", ipLeaseId);
                }
            ).sendWith(this);
          }
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
        IpLeaseService.State ipLease = Utils.fromJson(doc.getValue(), IpLeaseService.State.class);
        if (ipLease.ownerVmId != null) {
          ipLeaseList.add(ipLease);
        }
      }
    }

    return ipLeaseList;
  }

  private QueryTask buildIpLeaseQuery(State s) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(IpLeaseService.State.class));

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    querySpec.query
        .addBooleanClause(kindClause);

    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    querySpec.resultLimit = s.pageLimit;
    return QueryTask.create(querySpec).setDirect(true);
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

    State patchState = new State();
    if (state.nextPageLink != null) {
      patchState.nextPageLink = state.nextPageLink;
    }

    if (state.taskState == null) {
      patchState.taskState.stage = TaskState.TaskStage.STARTED;
    } else {
      patchState.taskState = state.taskState;
    }

    Operation patch = Operation
        .createPatch(UriUtils.buildUri(getHost(), getSelfLink()))
        .setBody(patchState);
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
   */
  private void validatePatch(State current, State patch, URI referrer) {
    checkNotNull(current.taskState.stage);
    checkNotNull(patch.taskState.stage);

    if (current.taskState.stage != TaskState.TaskStage.CREATED &&
        referrer.getPath().contains(TaskSchedulerServiceFactory.SELF_LINK)) {
      throw new IllegalStateException("Service is not in CREATED stage, ignores patch from TaskSchedulerService");
    }

    ValidationUtils.validatePatch(current, patch);

    // Patches cannot be applied to documents in terminal states.
    checkState(current.taskState.stage.ordinal() < TaskState.TaskStage.FINISHED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patch.taskState.stage.ordinal() >= current.taskState.stage.ordinal());
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
    @DefaultTaskState(value = TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * The link to next page.
     */
    public String nextPageLink;

    /**
     * The page limit for querying IpCleanerService.
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
