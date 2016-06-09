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

package com.vmware.photon.controller.deployer.xenon.workflow;

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.configuration.ZookeeperServer;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.BuildRuntimeConfigurationTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.BuildRuntimeConfigurationTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class implements a DCP micro-service which performs the task of
 * building configuration for all the containers.
 */
public class BuildContainersConfigurationWorkflowService extends StatefulService {

  /**
   * N.B. This value is used in data processing when applying mustache templates in
   * {@link com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskService}.
   */
  public static final String MUSTACHE_KEY_ZOOKEEPER_INSTANCES = "ZOOKEEPER_INSTANCES";

  /**
   * This class defines the state of a {@link BuildContainersConfigurationWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages for a {@link BuildContainersConfigurationWorkflowService}
     * task.
     */
    public enum SubStage {
      BUILD_RUNTIME_CONFIGURATION,
      SET_ZOOKEEPER_INSTANCES,
    }

    /**
     * This value defines the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link BuildContainersConfigurationWorkflowService}
   * task.
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
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the optional document self-link of the parent task service to be
     * notified on completion.
     */
    @Immutable
    public String parentTaskServiceLink;

    /**
     * This value represents the optional patch body to be sent to the parent task service on
     * successful completion.
     */
    @Immutable
    public String parentPatchBody;

    /**
     * This value represents the document self-link of the {@link DeploymentService} document to be
     * updated with the runtime state generated by this task.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the optional document self-link of the host service document
     * representing the host which is currently being added to the system. If this value is not
     * specified, then all hosts will be processed by the current task.
     */
    @Immutable
    public String hostServiceLink;
  }

  public BuildContainersConfigurationWorkflowService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation startOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    if (!startOp.hasBody()) {
      startOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State startState = startOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      validateState(startState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, e);
      return;
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);

    try {
      validatePatch(currentState, patchState);
      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, e);
      return;
    }

    patchOp.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      } else {
        TaskUtils.notifyParentTask(this, currentState.taskState, currentState.parentTaskServiceLink,
            currentState.parentPatchBody);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void validateTaskState(TaskState taskState) {
    ValidationUtils.validateTaskStage(taskState);
    switch (taskState.stage) {
      case CREATED:
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(taskState.subStage == null);
        break;
      case STARTED:
        checkState(taskState.subStage != null);
        switch (taskState.subStage) {
          case BUILD_RUNTIME_CONFIGURATION:
          case SET_ZOOKEEPER_INSTANCES:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case BUILD_RUNTIME_CONFIGURATION:
        processBuildRuntimeConfigurationSubStage(currentState);
        break;
      case SET_ZOOKEEPER_INSTANCES:
        processSetZookeeperInstancesSubStage(currentState);
        break;
    }
  }

  //
  // BUILD_RUNTIME_CONFIGURATION sub-stage routines
  //

  private void processBuildRuntimeConfigurationSubStage(State currentState) {

    if (currentState.hostServiceLink == null) {
      queryContainers(currentState, null);
      return;
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> vmServiceLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(vmServiceLinks.size() == 1);
                queryContainers(currentState, vmServiceLinks.iterator().next());
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void queryContainers(State currentState, String vmServiceLink) {

    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(ContainerService.State.class);

    if (vmServiceLink != null) {
      queryBuilder.addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, vmServiceLink);
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(queryBuilder.build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                startAggregatorService(currentState, o.getBody(QueryTask.class).results.documentLinks);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void startAggregatorService(State currentState, List<String> containerServiceLinks) {

    ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
    startState.parentTaskLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false,
        buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SET_ZOOKEEPER_INSTANCES));
    startState.pendingCompletionCount = containerServiceLinks.size();
    startState.errorThreshold = 0.0;

    sendRequest(Operation
        .createPost(this, ChildTaskAggregatorFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                startChildTasks(currentState, containerServiceLinks,
                    o.getBody(ServiceDocument.class).documentSelfLink);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void startChildTasks(State currentState,
                               List<String> containerServiceLinks,
                               String aggregatorServiceLink) {

    List<Operation> startOps = containerServiceLinks.stream()
        .map((containerServiceLink) -> {
          BuildRuntimeConfigurationTaskService.State startState = new BuildRuntimeConfigurationTaskService.State();
          startState.parentTaskServiceLink = aggregatorServiceLink;
          startState.deploymentServiceLink = currentState.deploymentServiceLink;
          startState.containerServiceLink = containerServiceLink;
          return Operation.createPost(this, BuildRuntimeConfigurationTaskFactoryService.SELF_LINK).setBody(startState);
        })
        .collect(Collectors.toList());

    OperationJoin
        .create(startOps)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
              }
            })
        .sendWith(this);
  }

  //
  // SET_ZOOKEEPER_INSTANCES sub-stage routines
  //

  private void processSetZookeeperInstancesSubStage(State currentState) {

    if (currentState.hostServiceLink == null) {
      queryZookeeperTemplate(currentState, null);
      return;
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> vmServiceLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(vmServiceLinks.size() == 1);
                queryZookeeperTemplate(currentState, vmServiceLinks.iterator().next());
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void queryZookeeperTemplate(State currentState, String vmServiceLink) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerTemplateService.State.class)
            .addFieldClause(ContainerTemplateService.State.FIELD_NAME_NAME,
                ContainersConfig.ContainerType.Zookeeper.name())
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                List<String> templateServiceLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(templateServiceLinks.size() == 1);
                queryZookeeperContainers(currentState, vmServiceLink, templateServiceLinks.iterator().next());
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void queryZookeeperContainers(State currentState,
                                        String vmServiceLink,
                                        String templateServiceLink) {

    QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
        .addKindFieldClause(ContainerService.State.class)
        .addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK, templateServiceLink);

    if (vmServiceLink != null) {
      queryBuilder.addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, vmServiceLink);
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(queryBuilder.build())
        .addOptions(EnumSet.of(
            QueryTask.QuerySpecification.QueryOption.BROADCAST,
            QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                getDeploymentState(currentState, o.getBody(QueryTask.class).results.documents);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void getDeploymentState(State currentState, Map<String, Object> containerServiceDocuments) {

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                setZookeeperInstances(containerServiceDocuments, o.getBody(DeploymentService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void setZookeeperInstances(Map<String, Object> containerDocuments, DeploymentService.State deploymentState) {

    List<ZookeeperServer> zookeeperServers = deploymentState.zookeeperIdToIpMap.entrySet().stream()
        .map((entry) -> new ZookeeperServer("server." + entry.getKey() + "=" + entry.getValue() + ":2888:3888"))
        .collect(Collectors.toList());

    String zookeeperInstances = new Gson().toJson(zookeeperServers);

    OperationJoin
        .create(containerDocuments.values().stream().map(
            (containerDocument) -> {
              ContainerService.State containerState = Utils.fromJson(containerDocument, ContainerService.State.class);
              ContainerService.State patchState = new ContainerService.State();
              patchState.dynamicParameters = containerState.dynamicParameters;
              patchState.dynamicParameters.put(MUSTACHE_KEY_ZOOKEEPER_INSTANCES, zookeeperInstances);
              return Operation.createPatch(this, containerState.documentSelfLink).setBody(patchState);
            }))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
              } else {
                sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
              }
            })
        .sendWith(this);
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage));
  }

  private void failTask(Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }

  private void failTask(Collection<Throwable> failures) {
    ServiceUtils.logSevere(this, failures);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    return buildPatch(taskStage, subStage, null);
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage taskStage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;
    patchState.taskState.subStage = subStage;
    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
