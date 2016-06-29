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

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.healthcheck.HealthChecker;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.xenon.constant.ServiceFileConstants;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon task service which brings up a Photon Controller service inside a container and
 * verifies that the service has been started successfully.
 */
public class CreateContainerTaskService extends StatefulService {

  public static final String HAPROXY_CONF_DIR = "/etc/haproxy";
  public static final String LIGHTWAVE_CONF_DIR = "/var/lib/vmware/config";

  @VisibleForTesting
  protected static final String ENV_COMMON_ENABLE_AUTH = "ENABLE_AUTH";
  @VisibleForTesting
  protected static final String ENV_MGMT_API_SWAGGER_LOGIN_URL = "SWAGGER_LOGIN_URL";
  @VisibleForTesting
  protected static final String ENV_MGMT_API_SWAGGER_LOGOUT_URL = "SWAGGER_LOGOUT_URL";
  @VisibleForTesting
  protected static final String ENV_MGMT_UI_LOGIN_URL = "MGMT_UI_LOGIN_URL";
  @VisibleForTesting
  protected static final String ENV_MGMT_UI_LOGOUT_URL = "MGMT_UI_LOGOUT_URL";

  /**
   * This class defines the state of a {@link CreateContainerTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This class defines the possible sub-stages for a task.
     */
    public enum SubStage {
      CREATE_CONTAINER,
      WAIT_FOR_SERVICE,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link CreateContainerTaskService} task.
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
     * This value represents the document link of the {@link DeploymentService} in whose context the operation is being
     * performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link ContainerService} representing the container to be
     * created.
     */
    @NotNull
    @Immutable
    public String containerServiceLink;

    /**
     * This value represents the number of consecutive successful polling iterations which must be performed before a
     * service can be declared ready.
     */
    @DefaultInteger(value = 10)
    @Immutable
    public Integer requiredPollCount;

    /**
     * This value represents the maximum number of polling iterations which should be attempted before declaring
     * failure.
     */
    @Immutable
    public Integer maximumPollCount;

    /**
     * This value represents the delay, in milliseconds, to use when polling the state of a container.
     */
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the number of polling iterations which have been performed by the current task.
     */
    @DefaultInteger(value = 0)
    public Integer pollCount;

    /**
     * This value represents the number of consecutive successful polling iterations which have been performed by the
     * current task.
     */
    @DefaultInteger(value = 0)
    public Integer successfulPollCount;
  }

  public CreateContainerTaskService() {
    super(State.class);
  }

  @Override
  public void handleStart(Operation postOp) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = postOp.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.maximumPollCount == null) {
      startState.maximumPollCount = HostUtils.getDeployerContext(this).getWaitForServiceMaxRetryCount();
    }

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_CONTAINER;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    postOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.STARTED) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(patchOp);
    State patchState = patchOp.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
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

  private void validatePatchState(State currentState, State patchState) {
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
          case CREATE_CONTAINER:
          case WAIT_FOR_SERVICE:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (patchState.subStage != null && currentState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case CREATE_CONTAINER:
        processCreateContainerSubStage(currentState);
        break;
      case WAIT_FOR_SERVICE:
        processWaitForServiceSubStage(currentState);
        break;
    }
  }

  //
  // CREATE_CONTAINER sub-stage routines
  //

  private void processCreateContainerSubStage(State currentState) {

    Operation deploymentOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink);
    Operation containerOp = Operation.createGet(this, currentState.containerServiceLink);

    OperationJoin
        .create(deploymentOp, containerOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processCreateContainerSubStage(currentState,
                    ops.get(deploymentOp.getId()).getBody(DeploymentService.State.class),
                    ops.get(containerOp.getId()).getBody(ContainerService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processCreateContainerSubStage(State currentState,
                                              DeploymentService.State deploymentState,
                                              ContainerService.State containerState) {

    Operation templateOp = Operation.createGet(this, containerState.containerTemplateServiceLink);
    Operation vmOp = Operation.createGet(this, containerState.vmServiceLink);

    OperationJoin
        .create(templateOp, vmOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processCreateContainerSubStage(currentState, deploymentState, containerState,
                    ops.get(templateOp.getId()).getBody(ContainerTemplateService.State.class),
                    ops.get(vmOp.getId()).getBody(VmService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processCreateContainerSubStage(State currentState,
                                              DeploymentService.State deploymentState,
                                              ContainerService.State containerState,
                                              ContainerTemplateService.State templateState,
                                              VmService.State vmState) {

    ContainersConfig.ContainerType containerType =
        ContainersConfig.ContainerType.valueOf(templateState.name);

    String hostVolume = ServiceFileConstants.VM_MUSTACHE_DIRECTORY +
        ServiceFileConstants.CONTAINER_CONFIG_ROOT_DIRS.get(containerType);

    if (templateState.volumeBindings == null) {
      templateState.volumeBindings = new HashMap<>();
    }

    templateState.volumeBindings.put(hostVolume, ServiceFileConstants.CONTAINER_CONFIG_DIRECTORY);

    switch (containerType) {
      case LoadBalancer:
        templateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + HAPROXY_CONF_DIR);
        break;
      case Lightwave:
        templateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + LIGHTWAVE_CONF_DIR);
        break;
    }

    String[] commandList = {DeployerDefaults.DEFAULT_ENTRYPOINT_COMMAND};

    Map<String, String> environmentVariables = new HashMap<>();
    if (templateState.environmentVariables != null) {
      environmentVariables.putAll(templateState.environmentVariables);
    }

    if (deploymentState.oAuthEnabled) {
      environmentVariables.put(ENV_COMMON_ENABLE_AUTH, "true");
      environmentVariables.put(ENV_MGMT_API_SWAGGER_LOGIN_URL, deploymentState.oAuthSwaggerLoginEndpoint);
      environmentVariables.put(ENV_MGMT_API_SWAGGER_LOGOUT_URL, deploymentState.oAuthSwaggerLogoutEndpoint);
      environmentVariables.put(ENV_MGMT_UI_LOGIN_URL, deploymentState.oAuthMgmtUiLoginEndpoint);
      environmentVariables.put(ENV_MGMT_UI_LOGOUT_URL, deploymentState.oAuthMgmtUiLogoutEndpoint);
    }

    String containerId = HostUtils.getDockerProvisionerFactory(this)
        .create(vmState.ipAddress)
        .launchContainer(templateState.name,
            templateState.containerImage,
            containerState.cpuShares,
            containerState.memoryMb,
            templateState.volumeBindings,
            templateState.portBindings,
            templateState.volumesFrom,
            templateState.isPrivileged,
            environmentVariables,
            true,
            templateState.useHostNetwork,
            commandList);

    if (containerId == null) {
      throw new IllegalStateException("Create container returned null");
    }

    ContainerService.State patchState = new ContainerService.State();
    patchState.containerId = containerId;

    sendRequest(Operation
        .createPatch(this, currentState.containerServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_SERVICE);
              }
            }));
  }

  //
  // WAIT_FOR_SERVICE sub-stage routines
  //

  private void processWaitForServiceSubStage(State currentState) {

    sendRequest(Operation
        .createGet(this, currentState.containerServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                processWaitForServiceSubStage(currentState, o.getBody(ContainerService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processWaitForServiceSubStage(State currentState, ContainerService.State containerState) {

    Operation templateOp = Operation.createGet(this, containerState.containerTemplateServiceLink);
    Operation vmOp = Operation.createGet(this, containerState.vmServiceLink);

    OperationJoin
        .create(templateOp, vmOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processWaitForServiceSubStage(currentState, containerState,
                    ops.get(templateOp.getId()).getBody(ContainerTemplateService.State.class),
                    ops.get(vmOp.getId()).getBody(VmService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processWaitForServiceSubStage(State currentState,
                                             ContainerService.State containerState,
                                             ContainerTemplateService.State templateState,
                                             VmService.State vmState) {

    ContainersConfig.ContainerType containerType =
        ContainersConfig.ContainerType.valueOf(templateState.name);

    HealthChecker healthChecker = HostUtils.getHealthCheckHelperFactory(this)
        .create(this, containerType, vmState.ipAddress)
        .getHealthChecker();

    if (healthChecker.isReady()) {
      currentState.pollCount++;
      currentState.successfulPollCount++;
    } else {
      currentState.pollCount++;
      currentState.successfulPollCount = 0;
    }

    if (currentState.successfulPollCount >= currentState.requiredPollCount) {
      sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
    } else if (currentState.pollCount >= currentState.maximumPollCount) {
      failTask(new IllegalStateException("Container " + containerState.containerId + " of type " + containerType +
          " on VM " + vmState.ipAddress + " failed to become ready after " + currentState.pollCount + " iterations"));
    } else {
      getHost().schedule(
          () -> {
            State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_SERVICE, null);
            patchState.pollCount = currentState.pollCount;
            patchState.successfulPollCount = currentState.successfulPollCount;
            TaskUtils.sendSelfPatch(this, patchState);
          },
          currentState.taskPollDelay, TimeUnit.MILLISECONDS);
    }
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s : %s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage, null));
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
