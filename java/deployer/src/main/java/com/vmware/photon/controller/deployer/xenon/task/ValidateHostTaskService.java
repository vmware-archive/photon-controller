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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotEmpty;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.deployengine.HostManagementVmAddressValidator;
import com.vmware.photon.controller.deployer.deployengine.HttpFileServiceClient;
import com.vmware.photon.controller.deployer.service.exceptions.ExistHostWithSameAddressException;
import com.vmware.photon.controller.deployer.service.exceptions.InvalidLoginException;
import com.vmware.photon.controller.deployer.service.exceptions.ManagementVmAddressAlreadyInUseException;
import com.vmware.photon.controller.deployer.xenon.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;

import javax.annotation.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a DCP micro-service which validates and adds ESX host.
 */
public class ValidateHostTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link ValidateHostTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current result code for the task.
     */
    public ResultCode resultCode;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum ResultCode {
      ExistHostWithSameAddress,
      InvalidLogin,
      ManagementVmAddressAlreadyInUse
    }
  }


  /**
   * This class defines the document state associated with a single
   * {@link ValidateHostTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the IP of the host.
     */
    @NotNull
    @Immutable
    public String hostAddress;

    /**
     * This value represents the user name to use when authenticating to the
     * host.
     */
    @NotNull
    @Immutable
    public String userName;

    /**
     * This value represents the password to use when authenticating to the
     * host.
     */
    @NotNull
    @Immutable
    public String password;

    /**
     * This value represents the usage tag associated with the host.
     */
    @NotEmpty
    @Immutable
    public Set<String> usageTags;

    /**
     * This value represents the metadata of the host.
     */
    @Immutable
    public Map<String, String> metadata;

    /**
     * This value represents the list of available networks to the host.
     */
    public Set<String> networks;

    /**
     * This value represents the list of available datastores to the host.
     */
    public Set<String> dataStores;
  }

  public ValidateHostTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        validateHost(startState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  /**
   * This method verifies host.
   *
   * @param state Supplies state
   * @throws Throwable
   */
  private void validateHost(State state) throws Throwable {
    Map<String, String> criteria = new HashMap<>();
    criteria.put(HostService.State.FIELD_NAME_HOST_ADDRESS, state.hostAddress);

    Map<String, String> exclusionCriteria = new HashMap<>();
    String id = ServiceUtils.getIDFromDocumentSelfLink(state.documentSelfLink);
    String path = HostServiceFactory.SELF_LINK + "/" + id;

    exclusionCriteria.put(ServiceDocument.FIELD_NAME_SELF_LINK, path);

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);

    // process inclusions.
    buildQuerySpecification(querySpecification, criteria, false);
    // process exclusions.
    buildQuerySpecification(querySpecification, exclusionCriteria, true);

    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);
    CloudStoreHelper cloudStoreHelper = ((DeployerXenonServiceHost) getHost()).getCloudStoreHelper();
    URI uri = cloudStoreHelper.getCloudStoreURI(null);

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          if (!QueryTaskUtils.getBroadcastQueryDocumentLinks(operation).isEmpty()) {
            throw new ExistHostWithSameAddressException(
                String.format("Find existing HostService entity with the same hostAddress %s", state.hostAddress));
          }
          validateHostCredentials(state);
        } catch (ExistHostWithSameAddressException t) {
          failTask(t, TaskState.ResultCode.ExistHostWithSameAddress);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation queryOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(uri, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setReferer(UriUtils.buildUri(getHost(), getSelfLink()))
        .setCompletion(completionHandler);

    sendRequest(queryOperation);
  }

  private QueryTask.QuerySpecification buildQuerySpecification(
      QueryTask.QuerySpecification querySpecification,
      Map<String, String> criteria,
      Boolean exclude) {

    if (criteria != null) {
      for (Map.Entry<String, String> criterion : criteria.entrySet()) {
        String fieldName = criterion.getKey();
        String expectedValue = criterion.getValue();

        if (fieldName.equals(HostService.State.FIELD_NAME_USAGE_TAGS)) {
          fieldName = QueryTask.QuerySpecification.buildCollectionItemName(
              HostService.State.FIELD_NAME_USAGE_TAGS);
        }

        QueryTask.Query query = new QueryTask.Query()
            .setTermPropertyName(fieldName)
            .setTermMatchValue(expectedValue);

        if (exclude) {
          //this designates the criteria to be an exclusion
          query.occurance = QueryTask.Query.Occurance.MUST_NOT_OCCUR;
        }

        querySpecification.query.addBooleanClause(query);
      }
    }

    return querySpecification;
  }

  /**
   * This method verifies host credentials.
   *
   * @param state Supplies state.
   * @throws Throwable
   */
  private void validateHostCredentials(State state) throws Throwable {

    HttpFileServiceClient httpFileServiceClient = HostUtils.getHttpFileServiceClientFactory(this)
        .create(state.hostAddress, state.userName, state.password);
    ListenableFutureTask<Integer> futureTask = ListenableFutureTask
        .create(httpFileServiceClient.getDirectoryListingOfDatastores());
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
      @Override
      public void onSuccess(@Nullable Integer result) {
        try {
          if (hasManagementVmAddress(state)) {
            validateManagementVmAddress(state);
          } else {
            sendStageProgressPatch(TaskState.TaskStage.FINISHED);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (t instanceof InvalidLoginException) {
          failTask(t, TaskState.ResultCode.InvalidLogin);
        } else {
          failTask(t);
        }
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  private boolean hasManagementVmAddress(State state) {
    return state.usageTags != null &&
        state.usageTags.contains(UsageTag.MGMT.name()) &&
        state.metadata != null &&
        state.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP) != null;
  }

  /**
   * This method verifies host's address.
   *
   * @param state Supplies state.
   * @throws Throwable
   */
  private void validateManagementVmAddress(State state) throws Throwable {

    HostManagementVmAddressValidator hostManagementVmAddressValidator =
        HostUtils.getHostManagementVmAddressValidatorFactory(this)
            .create(state.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP));
    ListenableFutureTask<Boolean> futureTask = ListenableFutureTask.create(hostManagementVmAddressValidator);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Boolean> futureCallback = new FutureCallback<Boolean>() {
      @Override
      public void onSuccess(@Nullable Boolean result) {
        sendStageProgressPatch(TaskState.TaskStage.FINISHED);
      }

      @Override
      public void onFailure(Throwable t) {
        if (t instanceof ManagementVmAddressAlreadyInUseException) {
          failTask(t, TaskState.ResultCode.ManagementVmAddressAlreadyInUse);
        } else {
          failTask(t);
        }
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param taskStage
   */
  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch with stage %s", taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e
   * @param resultCode
   */
  private void failTask(Throwable e, TaskState.ResultCode resultCode) {
    ServiceUtils.logSevere(this, e);
    State patchState = buildPatch(TaskState.TaskStage.FAILED, e);
    patchState.taskState.resultCode = resultCode;
    TaskUtils.sendSelfPatch(this, patchState);
  }

  /**
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage
   * @param e
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }
}
