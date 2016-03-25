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

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Implements allocation of containers for a specified container type.
 */
public class CreateContainerSpecTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link CreateContainerSpecLayoutTaskService} instance.
   */
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {
    /**
     * Task State.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * Pointer to container template service entity.
     */
    @NotNull
    @Immutable
    public String containerTemplateDocumentLink;

    /**
     * List of pointers to docker vm entities.
     */
    @NotNull
    @Immutable
    public List<String> dockerVmDocumentLinks;

    /**
     * VM service link for non-replicated service.
     */
    @Nullable
    @Immutable
    public String singletonVmServiceLink;
  }

  public CreateContainerSpecTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed for the current service instance.
   *
   * @param start
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service");
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

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

  /**
   * This method is called when a patch operation is performed on the current service.
   *
   * @param patch
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateStartState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        loadContainerTemplateService(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies current state object.
   */
  protected void validateStartState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState
   * @param patchState
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
    }

    startState.taskState = patchState.taskState;
    return startState;
  }

  private void loadContainerTemplateService(final State currentState) {

    sendRequest(Operation
        .createGet(this, currentState.containerTemplateDocumentLink)
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            ContainerTemplateService.State templateState = operation.getBody(ContainerTemplateService.State.class);
            loadExistingContainers(currentState, templateState);
          } catch (Throwable t) {
            failTask(t);
          }
        }));
  }

  private void loadExistingContainers(
      final State currentState,
      final ContainerTemplateService.State containerTemplateServiceState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query containerTemplateLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
        .setTermMatchValue(currentState.containerTemplateDocumentLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerTemplateLinkClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            try {
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(CreateContainerSpecTaskService.this, documentLinks);
              getExistingContainers(currentState, containerTemplateServiceState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getExistingContainers(
      final State currentState,
      final ContainerTemplateService.State containerTemplateState,
      Collection<String> documentLinks) {

    if (documentLinks.isEmpty()) {
      createContainerServices(currentState, containerTemplateState, null);
      return;
    }

    OperationJoin
        .create(documentLinks.stream()
            .map(documentLink -> Operation.createGet(this, documentLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Set<String> vmServiceLinks = ops.values().stream()
                .map(operation -> operation.getBody(ContainerService.State.class).vmServiceLink)
                .collect(Collectors.toSet());
            createContainerServices(currentState, containerTemplateState, vmServiceLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void createContainerServices(State currentState,
                                       ContainerTemplateService.State containerTemplateState,
                                       Set<String> vmServiceLinks) {

    List<ContainerService.State> containersToCreate = new ArrayList<>();
    if (containerTemplateState.isReplicated) {
      for (String vmServiceLink : currentState.dockerVmDocumentLinks) {
        if (null == vmServiceLinks || !vmServiceLinks.contains(vmServiceLink)) {
          ContainerService.State startState = new ContainerService.State();
          startState.containerTemplateServiceLink = containerTemplateState.documentSelfLink;
          startState.vmServiceLink = vmServiceLink;
          containersToCreate.add(startState);
        }
      }
    } else {
      if (null == vmServiceLinks || 0 == vmServiceLinks.size()) {
        ContainerService.State startState = new ContainerService.State();
        startState.containerTemplateServiceLink = containerTemplateState.documentSelfLink;
        startState.vmServiceLink = currentState.singletonVmServiceLink;
        containersToCreate.add(startState);
      }
    }

    scheduleCreateContainerServices(containersToCreate);
  }

  private void scheduleCreateContainerServices(List<ContainerService.State> containersToCreate) {
    if (containersToCreate.size() == 0) {
      sendStageProgressPatch(TaskState.TaskStage.FINISHED);
      return;
    }

    final AtomicInteger pendingResponses = new AtomicInteger(containersToCreate.size());
    final List<Throwable> failures = new ArrayList<>();

    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation completedOp, Throwable failure) {
        if (failure != null) {
          synchronized (failures) {
            failures.add(failure);
          }
        }

        if (0 == pendingResponses.decrementAndGet()) {
          if (failures.size() > 0) {
            failTask(failures.get(0));
          } else {
            sendStageProgressPatch(TaskState.TaskStage.FINISHED);
          }
        }
      }
    };

    for (ContainerService.State containerService : containersToCreate) {
      Operation createContainerServiceRequest = Operation
          .createPost(UriUtils.buildUri(getHost(), ContainerFactoryService.SELF_LINK))
          .setBody(containerService)
          .setCompletion(handler);
      sendRequest(createContainerServiceRequest);
    }
  }

  /**
   * This method sends a patch operation to the current service instance
   * to move to a new state.
   *
   * @param stage
   */
  private void sendStageProgressPatch(com.vmware.xenon.common.TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "sendStageProgressPatch %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
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

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
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
  protected State buildPatch(com.vmware.xenon.common.TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new com.vmware.xenon.common.TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }
}
