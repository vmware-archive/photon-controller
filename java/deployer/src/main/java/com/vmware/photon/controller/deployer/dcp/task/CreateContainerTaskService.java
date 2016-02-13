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

import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.DeployerDefaults;
import com.vmware.photon.controller.deployer.dcp.constant.ServiceFileConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.DockerProvisioner;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This class implements a DCP micro-service which performs the task of
 * creating a container on the specified VM.
 */
public class CreateContainerTaskService extends StatefulService {

  public static final String ZOOKEEPER_DATA_DIR = "/var/esxcloud/data/zookeeper";
  public static final String ZOOKEEPER_CONF_DIR = "/usr/lib/zookeeper/conf";
  public static final String HAPROXY_CONF_DIR = "/etc/haproxy";
  public static final String LIGHTWAVE_CONF_DIR = "/var/lib/vmware/config";

  /**
   * This class defines the document state associated with a single
   * {@link CreateContainerTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the id of the container that is created.
     */
    public String containerId;

    /**
     * This value represents the URL of the ContainerService object which
     * represents the container to be created.
     */
    @NotNull
    @Immutable
    public String containerServiceLink;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;
  }

  public CreateContainerTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed on the current
   * service instance.
   *
   * @param start Supplies a patch operation to be handled.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current
   * service instance.
   *
   * @param patch Supplies a patch operation to be handled.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processGetContainerService(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies the current state of the service instance.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method validates a patch object against a valid document state
   * object.
   *
   * @param startState Supplies the state of the current service instance.
   * @param patchState Supplies the state object specified in the patch
   *                   operation.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the initial state of the current service
   *                   instance.
   * @param patchState Supplies the patch state associated with a patch
   *                   operation.
   * @return The updated state of the current service instance.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage) {
        ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      }

      startState.taskState = patchState.taskState;

      if (null != patchState.containerId) {
        startState.containerId = patchState.containerId;
      }
    }

    return startState;
  }

  /**
   * This method performs document state updates in response to an operation
   * which creates a container. It has a callback handler which retrieves the
   * container service link and calls another method to get the VM ip address
   * and container template.
   *
   * @param currentState
   */
  private void processGetContainerService(final State currentState) {
    final Service service = this;
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerService.State containerState = operation.getBody(ContainerService.State.class);
          // Skip if container already created
          if (containerState.containerId != null) {
            State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            patchState.containerId = containerState.containerId;
            TaskUtils.sendSelfPatch(service, patchState);
            return;
          }
          processGetDeploymentService(currentState, containerState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.containerServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * Obtains the deployment service state to retrieve logon and logout URLs if auth is enabled.
   *
   * @param currentState
   * @param containerState
   */
  private void processGetDeploymentService(final State currentState, final ContainerService.State containerState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);
                    processGetVmState(currentState, containerState, deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
   * This method retrieves the VM Service entity document pointed by the
   * container service to get the ip address of the vm.
   *
   * @param currentState
   * @param deploymentState
   */
  private void processGetVmState(final State currentState, final ContainerService.State containerState,
                                 DeploymentService.State deploymentState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          processGetContainerTemplate(currentState, vmState, containerState, deploymentState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerState.vmServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method retrieves the VM Service entity document pointed by the
   * container service to get the ip address of the vm.
   *
   * @param currentState
   * @param deploymentState
   */
  private void processGetContainerTemplate(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState, DeploymentService.State deploymentState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerTemplateService.State containerTemplateState = operation.getBody(ContainerTemplateService.State
              .class);
          setVolumeBindings(currentState, vmState, containerTemplateState, deploymentState, containerState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerState.containerTemplateServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  private void setVolumeBindings(final State currentState,
                                 final VmService.State vmState,
                                 final ContainerTemplateService.State containerTemplateState,
                                 DeploymentService.State deploymentState,
                                 ContainerService.State containerState) {
    ContainersConfig.ContainerType containerType =
        ContainersConfig.ContainerType.valueOf(containerTemplateState.name);

    if (containerTemplateState.volumeBindings == null) {
      containerTemplateState.volumeBindings = new HashMap<>();
    }

    String hostVolume = ServiceFileConstants.VM_MUSTACHE_DIRECTORY + ServiceFileConstants
        .CONTAINER_CONFIG_ROOT_DIRS.get(containerType);
    containerTemplateState.volumeBindings.put(hostVolume, ServiceFileConstants.CONTAINER_CONFIG_DIRECTORY);

    if (containerTemplateState.name.equals(ContainersConfig.ContainerType.Zookeeper.name())) {
      containerTemplateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + ZOOKEEPER_CONF_DIR);
      containerTemplateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + ZOOKEEPER_DATA_DIR);
    }

    if (containerTemplateState.name.equals(ContainersConfig.ContainerType.LoadBalancer.name())) {
      containerTemplateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + HAPROXY_CONF_DIR);
    }

    if (containerTemplateState.name.equals(ContainersConfig.ContainerType.Lightwave.name())) {
      containerTemplateState.volumeBindings.computeIfPresent(hostVolume, (k, v) -> v + "," + LIGHTWAVE_CONF_DIR);
    }

    processCreateContainer(currentState, vmState.ipAddress, containerTemplateState, deploymentState, containerState);
  }

  /**
   * This method creates a docker container by submitting a future task to
   * the executor service for the DCP host. On successful completion, the
   * service is transitioned to the FINISHED state.
   *
   * @param currentState    Supplies the updated state of the current service
   *                        instance.
   * @param deploymentState
   */
  private void processCreateContainer(final State currentState,
                                      final String vmIpAddress,
                                      final ContainerTemplateService.State containerTemplateState,
                                      DeploymentService.State deploymentState,
                                      ContainerService.State containerState) {

    final Service service = this;
    ListenableFutureTask<String> futureTask = ListenableFutureTask.create(new Callable<String>() {
      @Override
      public String call() {
        List<String> commandList = new ArrayList<>();
        commandList.add(DeployerDefaults.DEFAULT_ENTRYPOINT_COMMAND);

        Map<String, String> environmentVariables = null;
        if (deploymentState.oAuthEnabled) {
          environmentVariables = new HashMap<String, String>();
          environmentVariables.putAll(containerTemplateState.environmentVariables);
          if (!environmentVariables.containsKey(BuildRuntimeConfigurationTaskService.ENV_ENABLE_AUTH)) {
            environmentVariables.put(BuildRuntimeConfigurationTaskService.ENV_ENABLE_AUTH, "true");
          }
          environmentVariables.put(BuildRuntimeConfigurationTaskService.ENV_SWAGGER_LOGIN_URL,
              deploymentState.oAuthResourceLoginEndpoint);
          environmentVariables.put(BuildRuntimeConfigurationTaskService.ENV_SWAGGER_LOGOUT_URL,
              deploymentState.oAuthLogoutEndpoint);
        } else {
          environmentVariables = containerTemplateState.environmentVariables;
        }

        DockerProvisioner dockerProvisioner = HostUtils.getDockerProvisionerFactory(service).create(vmIpAddress);
        String containerId = dockerProvisioner.launchContainer(
            containerTemplateState.name,
            containerTemplateState.containerImage,
            containerState.cpuShares,
            containerState.memoryMb,
            containerTemplateState.volumeBindings,
            containerTemplateState.portBindings,
            containerTemplateState.volumesFrom,
            containerTemplateState.isPrivileged,
            environmentVariables,
            true,
            containerTemplateState.useHostNetwork,
            commandList.toArray(new String[commandList.size()]));
        return containerId;
      }
    });

    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<String> futureCallback = new FutureCallback<String>() {
      @Override
      public void onSuccess(@Nullable String result) {
        try {
          if (null == result) {
            failTask(new IllegalStateException("Create container returned null"));
            return;
          }

          Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
            @Override
            public void handle(Operation operation, Throwable throwable) {
              if (null != throwable) {
                failTask(throwable);
                return;
              }
              // Update ourselves with FINISHED stage.
              State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
              ContainerService.State containerState = operation.getBody(ContainerService.State.class);
              patchState.containerId = containerState.containerId;
              TaskUtils.sendSelfPatch(service, patchState);
            }
          };

          // Update the Container service state with the containerId.
          ContainerService.State containerPatchState = new ContainerService.State();
          containerPatchState.containerId = result;
          Operation patchOperation = Operation
              .createPatch(UriUtils.buildUri(getHost(), currentState.containerServiceLink))
              .setBody(containerPatchState)
              .setCompletion(completionHandler);
          sendRequest(patchOperation);
        } catch (Throwable e) {
          failTask(e);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage Supplies the state to which the service instance should be
   *              transitioned.
   * @param e     Supplies an optional Throwable object representing the failure
   *              encountered by the service instance.
   * @return A State object which can be used to submit a stage progress self-
   * patch.
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

  /**
   * This method sends a patch operation to the current service instance to
   * transition to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the failure encountered by the service instance.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
