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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.IpLeaseService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.scheduler.TaskSchedulerServiceFactory;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseService;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseTask;
import com.vmware.photon.controller.dhcpagent.xenon.service.SubnetIPLeaseTask.SubnetIPLease;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Class implementing service to synchronize IP leases for a subnet
 * from the cloud store to DHCP agent.
 * Service will query subnet IP leases with pagination, and send
 * the update to DHCP agent.
 */
public class SubnetIPLeaseSyncService extends StatefulService {

  public static final String FACTORY_LINK = com.vmware.photon.controller.common.xenon.ServiceUriPaths.HOUSEKEEPER_ROOT
      + "/subnet-ip-lease-sync";
  public static final int DEFAULT_PAGE_LIMIT = 1000;

  public static FactoryService createFactory() {
    return FactoryService.create(SubnetIPLeaseSyncService.class, SubnetIPLeaseSyncService.State.class);
  }

  public SubnetIPLeaseSyncService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, false);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
      State state = start.getBody(State.class);
      initializeState(state);
      validateState(state);
      start.setBody(state).complete();
      processStart(state);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      start.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    try {
      ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
      State currentState = getState(patch);
      State patchState = patch.getBody(State.class);
      URI referer = patch.getReferer();

      validatePatch(currentState, patchState, referer);
      applyPatch(currentState, patchState);
      validateState(currentState);
      patch.complete();
      processPatch(currentState);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patch.fail(t);
    }
  }

  /**
   * Does any additional processing after the start operation has been completed.
   *
   * @param current
   */

  private void processStart(State current) {

    if (current.taskState.stage == TaskState.TaskStage.CREATED) {
      current.taskState.stage = TaskState.TaskStage.STARTED;
      current.taskState.subStage = TaskState.SubStage.CHECK_SUBNET_VERSION;
    }

    if (current.isSelfProgressionDisabled) {
      ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      return;
    }

    sendStageProgressPatch(current);
  }

  /**
   * Does any additional processing after the patch operation has been completed.
   *
   * @param current
   */
  private void processPatch(State current) {
    try {
      switch (current.taskState.stage) {
        case CREATED:
          break;
        case STARTED:
          processStartSubStage(current);
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
   * Process start sub stage for the task.
   *
   * @param current
   */
  private void processStartSubStage(State current) {
    switch(current.taskState.subStage) {
     case CHECK_SUBNET_VERSION:
       checkSubnetVersion(current);
        break;
      case PATCH_SUBNET_STAGED_VERSION:
        patchSubnetStagedVersion(current);
        break;
      case QUERY_IP_LEASES:
        queryIpLeaseDocuments(current);
        break;
      case SYNC_IP_LEASES:
        processIpLeaseDocuments(current);
        break;
      case PATCH_SUBNET_PUSHED_VERSION:
        patchSubnetPushedVersion(current);
        break;
      default:
        this.failTask(
                new IllegalStateException(
                        String.format("Un-expected sub stage: %s", current.taskState.subStage))
        );
    }
  }

  /**
   * Check subnet version before triggering sync.
   *
   * @param current
   */
  private void checkSubnetVersion(State current) {
    try {
      Operation queryDhcpSubnetPagination = Operation
              .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
              .setBody(buildSubnetQuery(current));
      queryDhcpSubnetPagination
              .setCompletion((op, failure) -> {
                if (failure != null) {
                  failTask(failure);
                  return;
                }

                ServiceDocumentQueryResult results = op.getBody(QueryTask.class).results;
                processDhcpSubnetServiceQueryResults(results, current);
              }).sendWith(this);
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Parse DhcpSubnetService query results.
   *
   * @param results
   */
  private void processDhcpSubnetServiceQueryResults(ServiceDocumentQueryResult results, State current) {
    if (results == null || results.documentCount == 0) {
      failTask(new Throwable("Dhcp subnet not found"));
      return;
    }

    if (results.documentCount > 1) {
      failTask(new Throwable(String.format("More than one Dhcp subnet found with the same Id:",
              current.subnetId)));
      return;
    }

    for (Map.Entry<String, Object> doc : results.documents.entrySet()) {
      DhcpSubnetService.State dhcpSubnet = Utils.fromJson(doc.getValue(), DhcpSubnetService.State.class);
      if (dhcpSubnet.versionStaged != null && dhcpSubnet.versionPushed != null &&
              (dhcpSubnet.version <= dhcpSubnet.versionPushed || dhcpSubnet.version <= dhcpSubnet.versionStaged)) {
        current.taskState.stage = com.vmware.xenon.common.TaskState.TaskStage.FINISHED;
      } else {
        current.dhcpAgentEndpoint = dhcpSubnet.dhcpAgentEndpoint;
        current.subnetDocumentSelfLink = dhcpSubnet.documentSelfLink;

        current.subnetIPLease = new SubnetIPLease();
        current.subnetIPLease.subnetId = current.subnetId;
        current.subnetIPLease.ipToMACAddressMap = new HashMap<>();
        current.subnetIPLease.version = dhcpSubnet.version;
        current.subnetIPLease.subnetOperation = SubnetIPLeaseTask.SubnetOperation.UPDATE;
        current.operationPatch = new DhcpSubnetService.VersionOperationPatch(
                DhcpSubnetService.VersionOperationPatch.Kind.PatchStagedVersion,
                dhcpSubnet.version, null);

        current.taskState.subStage = TaskState.SubStage.PATCH_SUBNET_STAGED_VERSION;
      }

      sendStageProgressPatch(current);
    }
  }

  /**
   * Patch subnet staged version.
   *
   * @param current
   */
  private void patchSubnetStagedVersion(State current) {
    try {
      Operation queryDhcpSubnet = Operation
              .createPatch(UriUtils.buildUri(getHost(), current.subnetDocumentSelfLink))
              .setBody(current.operationPatch);
      queryDhcpSubnet
              .setCompletion((op, failure) -> {
                if (failure != null) {
                  failTask(failure);
                  return;
                }

                current.taskState.subStage = TaskState.SubStage.QUERY_IP_LEASES;
                sendStageProgressPatch(current);
              }).sendWith(this);
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Patch subnet pushed version.
   *
   * @param current
   */
  private void patchSubnetPushedVersion(final State current) {
    try {
      Operation queryDhcpSubnet = Operation
              .createPatch(UriUtils.buildUri(getHost(), current.subnetDocumentSelfLink))
              .setBody(current.operationPatch);
      queryDhcpSubnet
              .setCompletion((op, failure) -> {
                if (failure != null) {
                  failTask(failure);
                  return;
                }

                finishTask(current);
              }).sendWith(this);
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Querying subnet IP leases for sync.
   *
   * @param current
   */
  private void queryIpLeaseDocuments(final State current) {
    try {
      Operation queryIpLeasePagination = Operation
        .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
        .setBody(buildIpLeaseQuery(current));
      queryIpLeasePagination
        .setCompletion((op, failure) -> {
          if (failure != null) {
            failTask(failure);
            return;
          }

          ServiceDocumentQueryResult results = op.getBody(QueryTask.class).results;

          if (results.nextPageLink != null) {
            current.nextPageLink = results.nextPageLink;
          }

          current.taskState.subStage = TaskState.SubStage.SYNC_IP_LEASES;
          sendStageProgressPatch(current);
        }).sendWith(this);
    } catch (Throwable e) {
      failTask(e);
    }
  }

  /**
   * Retrieves the first page of IpLeaseService and starts the subsequent processing.
   *
   * @param current
   */
  private void processIpLeaseDocuments(final State current) {
    if (current.nextPageLink == null) {
      this.triggerSubnetIPLeaseService(current);
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
        processIpLeaseServiceQueryResults(op.getBody(QueryTask.class), current);
      })
      .sendWith(this);
  }

  /**
   * Triggers a DHC agent SubnetIPLeaseService.
   *
   * @param current
   */
  protected void triggerSubnetIPLeaseService(State current) {
    // build completion handler
    Operation.CompletionHandler handler = (Operation acknowledgeOp, Throwable failure) -> {
      if (failure != null) {
        // we could not start an SubnetIPLease service. Something went wrong. Fail
        // the current task and stop processing.
        RuntimeException e = new RuntimeException(
                String.format("Failed to send DHCP agent subnet IP lease request %s", failure));
        failTask(e);
      }

      ServiceUtils.logInfo(SubnetIPLeaseSyncService.this, "DHCP agent SubnetIPLeaseService %s, is triggered",
              acknowledgeOp.getBody(SubnetIPLeaseTask.class).documentSelfLink);
      current.operationPatch.versionPushed = current.operationPatch.versionStaged;
      current.operationPatch.kind = DhcpSubnetService.VersionOperationPatch.Kind.PatchPushedVersion;
      current.taskState.subStage = TaskState.SubStage.PATCH_SUBNET_PUSHED_VERSION;
      sendStageProgressPatch(current);
    };

    // build SubnetIPLease service start state
    SubnetIPLeaseTask subnetIPLeaseTask = this.buildSubnetIPLeaseState(current);

    // start service
    this.startSubnetIPLeaseService(current, subnetIPLeaseTask, handler);
  }

  /**
   * Starts DHCP agent SubnetIPLease service.
   *
   * @param current
   * @param startState
   * @param handler
   * @return
   */
  private void startSubnetIPLeaseService(State current, final SubnetIPLeaseTask startState,
                                         final Operation.CompletionHandler handler) {
    Operation copyOperation = Operation
            .createPost(UriUtils.buildUri(current.dhcpAgentEndpoint + SubnetIPLeaseService.FACTORY_LINK))
            .setBody(startState)
            .setCompletion(handler);
    this.sendRequest(copyOperation);
  }

  /**
   * Builds DHCP agent SubnetIPLease service start state.
   *
   * @param current
   * @return
   */
  private SubnetIPLeaseTask buildSubnetIPLeaseState(final State current) {
    SubnetIPLeaseTask startState = new SubnetIPLeaseTask();
    startState.subnetIPLease = current.subnetIPLease;

    startState.documentExpirationTimeMicros = current.documentExpirationTimeMicros;

    return startState;
  }

  /**
   * Parse IpLeaseService query results.
   *
   * @param queryTask
   */
  private void processIpLeaseServiceQueryResults(QueryTask queryTask, State current) {
   if (queryTask != null && queryTask.results != null && queryTask.results.documentCount > 0) {
      for (Map.Entry<String, Object> doc : queryTask.results.documents.entrySet()) {
        IpLeaseService.State ipLease = Utils.fromJson(doc.getValue(), IpLeaseService.State.class);
        if (ipLease.ownerVmId != null && !ipLease.ownerVmId.isEmpty()) {
          current.subnetIPLease.ipToMACAddressMap.put(ipLease.ip, ipLease.macAddress);
        }
      }
      sendStageProgressPatch(current);
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
   * @param current Supplies the current state object.
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
   * @param current Supplies the current state object.
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
    ServiceUtils.logInfo(this, "Finished sending subnet ip leases.");
    if (patch.taskState == null) {
      patch.taskState = new TaskState();
    }

    patch.taskState.stage = TaskState.TaskStage.FINISHED;
    this.sendStageProgressPatch(patch);
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

  private QueryTask buildSubnetQuery(State s) {
    QueryTask.Query kindClause = new QueryTask.Query()
            .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
            .setTermMatchValue(Utils.buildKind(DhcpSubnetService.State.class));

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    QueryTask.Query subnetIdClause = new QueryTask.Query()
            .setTermPropertyName("subnetId")
            .setTermMatchValue(s.subnetId);

    querySpec.query
            .addBooleanClause(kindClause)
            .addBooleanClause(subnetIdClause);

    querySpec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    return QueryTask.create(querySpec).setDirect(true);
  }

  /**
   * Service execution stages.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {
    /**
     * The execution substage.
     */
    public SubStage subStage;

    /**
     * Execution sub-stage.
     */
    public static enum SubStage {
      CHECK_SUBNET_VERSION,
      PATCH_SUBNET_STAGED_VERSION,
      QUERY_IP_LEASES,
      SYNC_IP_LEASES,
      PATCH_SUBNET_PUSHED_VERSION,
    }
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
     * Dhcp subnet version operation patch.
     */
    public DhcpSubnetService.VersionOperationPatch operationPatch;

    /**
     * Document self link to subnet document.
     */
    public String subnetDocumentSelfLink;

    /**
     * Endpoint for communicating with DHCP agent.
     * Endpoint includes: IP, port and protocol.
     */
    public String dhcpAgentEndpoint;

    /**
     * Subnet IP lease details.
     */
    public SubnetIPLease subnetIPLease;

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
    public boolean isSelfProgressionDisabled;
  }
}
