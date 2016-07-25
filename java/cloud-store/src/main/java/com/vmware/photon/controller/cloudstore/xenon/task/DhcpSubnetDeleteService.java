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
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
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

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Class implementing service to delete dangling DhcpSubnetService from the cloud store.
 * Service will query DhcpSubnetService with pagination,
 * and deletes the IpLeaseService associated with the deleted DhcpSubnetService,
 * and delete the dangling DhcpSubnetService documents as well.
 */
public class DhcpSubnetDeleteService extends StatefulService {

  public static final String FACTORY_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.CLOUDSTORE_ROOT
      + "/dhcp-subnet-deletes";

  public static final int DEFAULT_PAGE_LIMIT = 1000;

  public static FactoryService createFactory() {
    return FactoryService.create(DhcpSubnetDeleteService.class, DhcpSubnetDeleteService.State.class);
  }

  public DhcpSubnetDeleteService() {
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

    validatePatch(currentState, patchState);
    applyPatch(currentState, patchState);
    validateState(currentState);
    patch.complete();
    processPatch(currentState);
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
   */
  private void validatePatch(State current, State patch) {
    ValidationUtils.validatePatch(current, patch);
    ValidationUtils.validateTaskStageProgression(current.taskState, patch.taskState);
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
        Operation queryDhcpSubnetsPagination = Operation
            .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
            .setBody(buildDhcpSubnetQuery(current));
        queryDhcpSubnetsPagination
            .setCompletion(((op, failure) -> {
              if (failure != null) {
                failTask(failure);
                return;
              }
              ServiceDocumentQueryResult results = op.getBody(QueryTask.class).results;
              if (results.nextPageLink != null) {
                current.nextPageLink = results.nextPageLink;
              } else {
                ServiceUtils.logInfo(this, "No DhcpSubnets found.");
              }
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
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(final State current) {
    try {
      switch (current.taskState.stage) {
        case STARTED:
          processDhcpSubnetsDocuments(current);
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
   * Retrieves the first page of DhcpSubnets and kicks of the subsequent processing.
   *
   * @param current
   */
  private void processDhcpSubnetsDocuments(final State current) {
    if (current.nextPageLink == null) {
      finishTask(current);
      return;
    }

    Operation getOnePageOfDhcpSubnetsDocuments =
        Operation.createGet(UriUtils.buildUri(getHost(), current.nextPageLink));
    getOnePageOfDhcpSubnetsDocuments
        .setCompletion((op, throwable) -> {
          if (throwable != null) {
            failTask(throwable);
            return;
          }
          current.nextPageLink = op.getBody(QueryTask.class).results.nextPageLink;

          List<DhcpSubnetService.State> dhcpSubnetList =
              parseDhcpSubnetServiceQueryResults(op.getBody(QueryTask.class));

          triggerIpLeaseDeleteService(current, dhcpSubnetList);
        })
        .sendWith(this);
  }

  /**
   * Triggers IpLeaseDeleteService for one DHCP subnet list.
   *
   * @param current
   * @param dhcpSubnetList
   */
  private void triggerIpLeaseDeleteService(final State current, List<DhcpSubnetService.State> dhcpSubnetList) {
    if (dhcpSubnetList.size() == 0) {
      ServiceUtils.logInfo(this, "No DhcpSubnets found any more.");
      sendStageProgressPatch(current);
      return;
    }

    for (DhcpSubnetService.State state : dhcpSubnetList) {
      triggerIpLeaseDeleteService(ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink));
    }
    sendStageProgressPatch(current);
  }

  /**
   * Triggers IpLeaseDeleteService for one DHCP subnet.
   *
   * @param subnetId
   */
  private void triggerIpLeaseDeleteService(String subnetId) {
    IpLeaseDeleteService.State ipLeaseDeleteServiceState = new IpLeaseDeleteService.State();
    ipLeaseDeleteServiceState.subnetId = subnetId;
    ipLeaseDeleteServiceState.documentSelfLink = subnetId;

    Operation triggerIpLeaseDeleteOperation = Operation
        .createPost(UriUtils.buildUri(getHost(), IpLeaseDeleteService.FACTORY_LINK))
        .setBody(ipLeaseDeleteServiceState);
    triggerIpLeaseDeleteOperation
        .setCompletion(((op, failure) -> {
          IpLeaseDeleteService.State response = op.getBody(IpLeaseDeleteService.State.class);
          if (failure != null) {
            ServiceUtils.logWarning(this, "Triggering one IpLeaseDeleteService (subnetId %s) failed with %s",
                subnetId, failure);
            return;
          }

          ServiceUtils.logInfo(this, "Creating one IpLeaseDeleteService succeeded with subnetId %s",
              response.subnetId);
        })).sendWith(this);
  }

  private void finishTask(final State patch) {
    ServiceUtils.logInfo(this, "Finished deleting unreleased DhcpSubnets.");
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

  private QueryTask buildDhcpSubnetQuery(State s) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DhcpSubnetService.State.class));

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    QueryTask.Query flagClause = new QueryTask.Query()
        .setTermPropertyName("doGarbageCollection")
        .setTermMatchValue("true");

    querySpec.query
        .addBooleanClause(kindClause)
        .addBooleanClause(flagClause);

    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    querySpec.resultLimit = s.pageLimit;
    return QueryTask.create(querySpec).setDirect(true);
  }

  private List<DhcpSubnetService.State> parseDhcpSubnetServiceQueryResults(QueryTask result) {
    List<DhcpSubnetService.State> dhcpSubnetList = new LinkedList<>();

    if (result != null && result.results != null && result.results.documentCount > 0) {
      for (Map.Entry<String, Object> doc : result.results.documents.entrySet()) {
        dhcpSubnetList.add(
            Utils.fromJson(doc.getValue(), DhcpSubnetService.State.class));
      }
    }

    return dhcpSubnetList;
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
     * The page limit for querying DhcpSubnetService.
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
