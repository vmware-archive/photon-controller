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

package com.vmware.photon.controller.deployer.dcp.workflow;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceErrorResponse;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.CreateIsoTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateIsoTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForDockerTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.WaitForDockerTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.net.util.SubnetUtils;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * This class implements a DCP service representing the creation of a management vm.
 */
public class CreateManagementVmWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link CreateManagementVmWorkflowService} task.
   */
  public static class TaskState extends com.vmware.dcp.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this work-flow.
     */
    public enum SubStage {
      CREATE_VM,
      CREATE_ISO,
      START_VM,
      WAIT_FOR_DOCKER,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link CreateManagementVmWorkflowService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents control flags influencing the behavior of the workflow.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * This value represents the link to the vm entity.
     */
    @NotNull
    @Immutable
    public String vmServiceLink;

    /**
     * This value represents the polling interval override value to use for child tasks.
     */
    @Immutable
    public Integer childPollInterval;

    /**
     * The iso filename of the created network iso.
     */
    public String isoFile;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Immutable
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the NTP server configured at VM.
     */
    @Immutable
    public String ntpEndpoint;
  }

  public CreateManagementVmWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_VM;
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed for the current
   * service instance.
   *
   * @param patch Supplies the start operation object.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    validateState(startState);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStartedState(currentState);
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
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(null != currentState.taskState.subStage, "Sub-stage cannot be null in STARTED stage.");
      switch (currentState.taskState.subStage) {
        case CREATE_ISO:
        case CREATE_VM:
        case START_VM:
        case WAIT_FOR_DOCKER:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage.toString());
      }
    } else {
      checkState(null == currentState.taskState.subStage, "Sub-stage must be null in stages other than STARTED.");
    }
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
    checkNotNull(startState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    // The task sub-state must be at least equal to the current task sub-state
    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
    // A document can never be patched to the CREATED state.
    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patchState.taskState.stage.ordinal() >= startState.taskState.stage.ordinal());

    // Patches cannot be applied to documents in terminal states.
    checkState(startState.taskState.subStage == null
        || startState.taskState.stage.ordinal() <= TaskState.TaskStage.STARTED.ordinal());
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage
          || patchState.taskState.subStage != startState.taskState.subStage) {
        ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      }
      startState.taskState = patchState.taskState;
      startState.isoFile = patchState.isoFile;
    }
    return startState;
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) {
    switch (currentState.taskState.subStage) {
      case CREATE_VM:
        createVm(currentState);
        break;
      case CREATE_ISO:
        getVmStateToCreateIso(currentState);
        break;
      case START_VM:
        getVmStateToStartVm(currentState);
        break;
      case WAIT_FOR_DOCKER:
        waitForDocker(currentState);
        break;
    }
  }

  /**
   * This method starts a {@link CreateManagementVmTaskService} task.
   *
   * @param currentState Supplies the current state object.
   */
  private void createVm(final State currentState) {

    FutureCallback<CreateManagementVmTaskService.State> callback =
        new FutureCallback<CreateManagementVmTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateManagementVmTaskService.State result) {
            sendProgressPatch(result.taskState, TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_ISO);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateManagementVmTaskService.State startState = createVmTaskState(currentState);

    TaskUtils.startTaskAsync(
        this,
        CreateManagementVmTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateManagementVmTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * Creates a {@link CreateManagementVmTaskService.State} object from the current state.
   *
   * @param currentState Supplies the current state object.
   * @return
   */
  private CreateManagementVmTaskService.State createVmTaskState(final State currentState) {
    CreateManagementVmTaskService.State state = new CreateManagementVmTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.vmServiceLink = currentState.vmServiceLink;
    state.queryCreateVmTaskInterval = currentState.taskPollDelay;
    return state;
  }

  /**
   * Gets the {@link VmService.State} object for the vm being created and proceeds to creating the ISO.
   *
   * @param currentState
   */
  private void getVmStateToCreateIso(final State currentState) {
    getVmState(
        currentState,
        new FutureCallback<VmService.State>() {
          @Override
          public void onSuccess(@Nullable VmService.State state) {
            getHostStateToCreateIso(currentState, state);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  /**
   * Gets the {@link HostService.State} object for the vm being created and proceeds to creating the ISO.
   *
   * @param currentState
   * @param vmState
   */
  private void getHostStateToCreateIso(final State currentState, final VmService.State vmState) {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          HostService.State hostState = operation.getBody(HostService.State.class);
          createIso(currentState, vmState, hostState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    CloudStoreHelper cloudStoreHelper = ((DeployerDcpServiceHost) getHost()).getCloudStoreHelper();
    cloudStoreHelper.getEntity(this, vmState.hostServiceLink, completionHandler);
  }

  /**
   * Creats the network iso.
   *
   * @param currentState Supplies the current state object.
   */
  private void createIso(
      final State currentState, final VmService.State vmState, final HostService.State hostState) {

    FutureCallback<CreateIsoTaskService.State> callback = new FutureCallback<CreateIsoTaskService.State>() {
      @Override
      public void onSuccess(@Nullable CreateIsoTaskService.State result) {
        sendProgressPatch(result.taskState, TaskState.TaskStage.STARTED, TaskState.SubStage.START_VM);
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };
    CreateIsoTaskService.State startState = createCreateIsoTaskServiceStartState(currentState, vmState, hostState);

    TaskUtils.startTaskAsync(
        this,
        CreateIsoTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateIsoTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  /**
   * This method creates a valid start state for the {@link CreateIsoTaskService}.
   *
   *
   * @param currentState
   * @param vmState
   * @param hostState
   * @return
   */
  private CreateIsoTaskService.State createCreateIsoTaskServiceStartState(
      State currentState, final VmService.State vmState, final HostService.State hostState) {
    CreateIsoTaskService.State state = new CreateIsoTaskService.State();
    state.taskState = new com.vmware.dcp.common.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.vmId = vmState.vmId;

    state.userDataTemplate = createCloudInitUserDataTemplate(currentState, hostState);
    state.metaDataTemplate = createCloudInitMetaDataTemplate(vmState);
    state.placeConfigFilesInISO = true;

    return state;
  }

  /**
   * Creates the template for cloud init's user-data file that is later on used by {@link CreateIsoTaskService}.
   *
   *
   * @param currentState
   * @param hostState Supplies the {@link HostService.State} object.
   * @return
   * @throws IOException
   */
  private CreateIsoTaskService.FileTemplate createCloudInitUserDataTemplate(
      State currentState, HostService.State hostState) {

    CreateIsoTaskService.FileTemplate template = new CreateIsoTaskService.FileTemplate();
    template.parameters = new HashMap();

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);
    template.filePath = Paths.get(deployerContext.getScriptDirectory(), "user-data.template").toString();

    String gateway = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY);
    template.parameters.put("$GATEWAY", gateway);

    String ip = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP);
    String netMask = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK);
    template.parameters.put("$ADDRESS", new SubnetUtils(ip, netMask).getInfo().getCidrSignature());

    String dnsList = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER);
    StringBuilder dnsServerBuilder = new StringBuilder();
    if (!Strings.isNullOrEmpty(dnsList)) {
      for (String dnsServer : dnsList.split(",")) {
        dnsServerBuilder.append("DNS=");
        dnsServerBuilder.append(dnsServer);
        dnsServerBuilder.append("\n");
      }
    }
    template.parameters.put("$DNS", dnsServerBuilder.toString());
    template.parameters.put("$NTP", currentState.ntpEndpoint);
    return template;
  }

  /**
   * Creates the template for cloud init's meta-data file that is later on used by {@link CreateIsoTaskService}.
   *
   * @param vmState Supplies the {@link VmService.State} object.
   * @return
   * @throws IOException
   */
  private CreateIsoTaskService.FileTemplate createCloudInitMetaDataTemplate(
      VmService.State vmState) {

    CreateIsoTaskService.FileTemplate template = new CreateIsoTaskService.FileTemplate();
    template.parameters = new HashMap();

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);
    template.filePath = Paths.get(deployerContext.getScriptDirectory(), "meta-data.template").toString();

    template.parameters.put("$INSTANCE_ID", vmState.name);
    template.parameters.put("$LOCAL_HOSTNAME", vmState.name);
    return template;
  }

  /**
   * Gets the {@link VmService.State} object for the vm being started and proceeds to starting the VM.
   *
   * @param currentState
   */
  private void getVmStateToStartVm(final State currentState) {
    getVmState(
        currentState,
        new FutureCallback<VmService.State>() {
          @Override
          public void onSuccess(@Nullable VmService.State state) {
            startVm(currentState, state);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  /**
   * This method starts a vm.
   *
   * @param currentState Supplies the current state object.
   */
  private void startVm(final State currentState, VmService.State vmState) {
    try {
      HostUtils.getApiClient(this).getVmApi().performStartOperationAsync(
          vmState.vmId,
          new FutureCallback<Task>() {
            @Override
            public void onSuccess(@Nullable Task result) {
              processTask(result,
                  buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_DOCKER));
            }

            @Override
            public void onFailure(Throwable t) {
              failTask(t);
            }
          }
      );
    } catch (IOException e) {
      failTask(e);
    }
  }

  private void processTask(Task task, final State patchState) {
    ApiUtils.pollTaskAsync(
        task,
        HostUtils.getApiClient(this),
        this,
        HostUtils.getDeployerContext(this).getTaskPollDelay(),
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            TaskUtils.sendSelfPatch(CreateManagementVmWorkflowService.this, patchState);
          }
          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  /**
   * This method uses the {@link WaitForDockerTaskService} to wait for Docker to initialize on the VM.
   *
   * @param currentState Supplies the current state object.
   */
  private void waitForDocker(State currentState) {

    FutureCallback<WaitForDockerTaskService.State> futureCallback =
        new FutureCallback<WaitForDockerTaskService.State>() {
          @Override
          public void onSuccess(@Nullable WaitForDockerTaskService.State result) {
            sendProgressPatch(result.taskState, TaskState.TaskStage.FINISHED, null);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    WaitForDockerTaskService.State startState = new WaitForDockerTaskService.State();
    startState.vmServiceLink = currentState.vmServiceLink;
    startState.delayInterval = currentState.childPollInterval;
    startState.pollInterval = currentState.childPollInterval;

    TaskUtils.startTaskAsync(
        this,
        WaitForDockerTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        WaitForDockerTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  /**
   * Gets the {@link VmService.State} object for the vm being created and proceeds to creating the ISO.
   *
   * @param currentState
   */
  private void getVmState(final State currentState, final FutureCallback<VmService.State> callback) {

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          callback.onFailure(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          callback.onSuccess(vmState);
        } catch (Throwable t) {
          callback.onFailure(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method sends a progress patch depending of the taskState of the provided state.
   *
   * @param state    Supplies the state.
   * @param stage    Supplies the stage to progress to.
   * @param subStage Supplies the substate to progress to.
   */
  private void sendProgressPatch(
      com.vmware.dcp.common.TaskState state,
      TaskState.TaskStage stage,
      TaskState.SubStage subStage) {
    switch (state.stage) {
      case FINISHED:
        TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
        break;
      case CANCELLED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.CANCELLED, null));
        break;
      case FAILED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, state.failure));
        break;
    }
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage));
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param errorResponse
   * @return
   */
  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }
}
