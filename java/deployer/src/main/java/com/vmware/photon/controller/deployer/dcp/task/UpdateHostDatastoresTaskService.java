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

package com.vmware.photon.controller.deployer.dcp.task;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.ExceptionUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class implements a DCP microservice which performs the task of provisioning an agent.
 */
public class UpdateHostDatastoresTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link UpdateHostDatastoresTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the document link to the {@link HostService} instance representing the ESX host on which
     * the agent should be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;
  }

  public UpdateHostDatastoresTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start operation for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
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
    ServiceUtils.logInfo(this, "Handling patch operation for service %s", getSelfLink());
    State startState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        gethostState(currentState);
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
      ServiceUtils.logInfo(this, "Moving to state %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void gethostState(State currentState) {
    HostUtils.getCloudStoreHelper(this)
      .createGet(currentState.hostServiceLink)
      .setCompletion((op, t) ->{
        if (t != null) {
          failTask(t);
          return;
        }
        getHostConfig(currentState, op.getBody(HostService.State.class));
      })
      .sendWith(this);
  }

  private void getHostConfig(final State currentState, final HostService.State hostState) {
    AsyncMethodCallback<Host.AsyncClient.get_host_config_call> handler
      = new AsyncMethodCallback<Host.AsyncClient.get_host_config_call>() {

      @Override
      public void onComplete(Host.AsyncClient.get_host_config_call call) {
        try {
          GetConfigResponse configResponse = call.getResult();
          HostClient.ResponseValidator.checkGetConfigResponse(configResponse);
          processHostConfig(configResponse.getHostConfig(), currentState.hostServiceLink);
        } catch (Throwable t) {
          failTask(t);
        }
      }

      @Override
      public void onError(Exception e) {
        failTask(e);
      }
    };

    try {
      HostUtils.getHostClient(this).setIpAndPort(hostState.hostAddress, ServicePortConstants.AGENT_PORT);
      HostUtils.getHostClient(this).getHostConfig(handler);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processHostConfig(HostConfig hostConfig, String hostServiceLink) {
    Set<String> imageDatastoreIds = getOrElse(hostConfig.getImage_datastore_ids(), new HashSet<String>());

    Collection<Datastore> imageDatastores = getOrElse(hostConfig.getDatastores(), new ArrayList<Datastore>()).stream()
        .filter(datastore -> imageDatastoreIds.contains(datastore.getId()))
        .collect(Collectors.toSet());
    Collection<Datastore> regularDatastores = getOrElse(hostConfig.getDatastores(), new ArrayList<Datastore>()).stream()
        .filter(datastore -> !imageDatastoreIds.contains(datastore.getId()))
        .collect(Collectors.toSet());

    Collection<DatastoreService.State> imageDatastoreStates = generateDatastoreStates(imageDatastores, true);
    Collection<DatastoreService.State> regularDatastoreStates = generateDatastoreStates(regularDatastores, false);

    Collection<Operation> operations = generateDatastorePosts(imageDatastoreStates);
    operations.addAll(generateDatastorePosts(regularDatastoreStates));
    operations.add(generateHostupdatePatch(hostServiceLink, imageDatastoreStates, regularDatastoreStates));

    OperationJoin.create(operations)
      .setCompletion((ops, ts) -> {
        ts = removeEntityExistsErrors(ops, ts);
        if (ts != null && !ts.isEmpty()) {
          failTask(ExceptionUtils.createMultiException(ts.values()));
          return;
        }
        sendStageProgressPatch(TaskState.TaskStage.FINISHED);
      })
      .sendWith(this);
  }

  private <T> T getOrElse(T value, T alternative) {
    if (value == null) {
      return alternative;
    }
    return value;
  }

  private Map<Long, Throwable> removeEntityExistsErrors(Map<Long, Operation> ops, Map<Long, Throwable> ts) {
    for (Operation op : ops.values()) {
      if (op.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
        ts.remove(op.getId());
      }
    }
    return ts;
  }

  private Operation generateHostupdatePatch(
      String hostServiceLink,
      Collection<DatastoreService.State> imageDatastores,
      Collection<DatastoreService.State> regularDatastoreStates) {
    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    HostService.State host = new HostService.State();
    host.reportedDatastores = regularDatastoreStates.stream().map(ds -> ds.id).collect(Collectors.toSet());
    host.reportedImageDatastores = imageDatastores.stream().map(ds -> ds.id).collect(Collectors.toSet());

    List<DatastoreService.State> allDatastores = new ArrayList<>(imageDatastores);
    allDatastores.addAll(regularDatastoreStates);

    host.datastoreServiceLinks = allDatastores.stream()
        .collect(Collectors.toMap(
            k -> {
              return k.name;
              },
            v -> {
              return DatastoreServiceFactory.SELF_LINK + "/" + v.id;
              }));

    return cloudStoreHelper.createPatch(hostServiceLink)
        .setBody(host);
  }

  private Collection<Operation> generateDatastorePosts(Collection<DatastoreService.State> datastoreStates) {
    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    return datastoreStates.stream()
        .map(datastore -> cloudStoreHelper.createPost(DatastoreServiceFactory.SELF_LINK)
            .setBody(datastore))
        .collect(Collectors.toSet());
  }

  private Collection<DatastoreService.State> generateDatastoreStates(
      Collection<Datastore> imageDatastores,
      boolean isImageDatastore) {
    return imageDatastores.stream()
        .map(datastore -> toDatastoreService(datastore, isImageDatastore))
        .collect(Collectors.toSet());
  }

  private DatastoreService.State toDatastoreService(Datastore datastore, boolean isImageDatastore) {
    DatastoreService.State state = new DatastoreService.State();
    state.id = datastore.getId();
    state.isImageDatastore = isImageDatastore;
    state.name = datastore.getName();
    state.tags = datastore.getTags();
    state.type = datastore.getType().name();
    state.documentSelfLink = datastore.getId();
    return state;
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch with stage %s", taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
