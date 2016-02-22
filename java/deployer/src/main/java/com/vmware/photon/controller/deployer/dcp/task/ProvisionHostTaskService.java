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

import com.vmware.photon.controller.agent.gen.AgentControl;
import com.vmware.photon.controller.agent.gen.AgentStatusCode;
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostConfig;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.Network;
import com.vmware.photon.controller.resource.gen.NetworkType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP service which provisions a host and updates the corresponding cloud store documents.
 */
public class ProvisionHostTaskService extends StatefulService {

  public static final String SCRIPT_NAME = "esx-install-agent2";

  /**
   * This class defines the state of a {@link ProvisionHostTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-states for this task.
     */
    public enum SubStage {
      INSTALL_AGENT,
      WAIT_FOR_AGENT,
      PROVISION_AGENT,
      GET_HOST_CONFIG,
    }

    /**
     * This value defines the state of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link ProvisionHostTaskService} task.
   */
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
     * This value represents the interval, in milliseconds, between child task polling iterations.
     */
    @Positive
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the document link of the {@link HostService} object which represents
     * the host to be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the document link of the {@link DeploymentService} object which
     * represents the deployment in whose context the task is being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the absolute path to the uploaded VIB image on the host.
     */
    @NotNull
    @Immutable
    public String vibPath;

    /**
     * This value represents the maximum number of agent status polling iterations which should be attempted before
     * declaring failure.
     */
    @DefaultInteger(value = 60)
    @Positive
    @Immutable
    public Integer maximumPollCount;

    /**
     * This value represents the interval, in milliseconds, between agent status polling iterations.
     */
    @DefaultInteger(value = 5000)
    @Positive
    @Immutable
    public Integer pollInterval;

    /**
     * This value represents the number of agent status polling iterations which have been attempted by the current
     * task.
     */
    @DefaultInteger(value = 0)
    public Integer pollCount;
  }

  /**
   * This method provides a default constructor for {@link ProvisionHostTaskService} objects.
   */
  public ProvisionHostTaskService() {
    super(State.class);
    this.toggleOption(ServiceOption.OWNER_SELECTION, true);
    this.toggleOption(ServiceOption.PERSISTENCE, true);
    this.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a service instance is started.
   *
   * @param operation Supplies the {@link Operation} which triggered the service start.
   */
  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.INSTALL_AGENT;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
    }

    operation.setBody(startState).complete();

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

  /**
   * This method is called when a service instance receives a patch.
   *
   * @param operation Supplies the {@link Operation} which triggered the patch.
   */
  @Override
  public void handlePatch(Operation operation) {
    ServiceUtils.logTrace(this, "Handling patch operation");
    State currentState = getState(operation);
    State patchState = operation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateState(currentState);
    operation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
        processStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    validateTaskState(state.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (patchState.pollCount != null && currentState.pollCount != null) {
      checkState(patchState.pollCount >= currentState.pollCount);
    }
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
          case INSTALL_AGENT:
          case WAIT_FOR_AGENT:
          case PROVISION_AGENT:
          case GET_HOST_CONFIG:
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
      case INSTALL_AGENT:
        processInstallAgentSubStage(currentState);
        break;
      case WAIT_FOR_AGENT:
        processWaitForAgentSubStage(currentState);
        break;
      case PROVISION_AGENT:
        processProvisionAgentSubStage(currentState);
        break;
      case GET_HOST_CONFIG:
        processGetHostConfigSubStage(currentState);
        break;
    }
  }

  //
  // INSTALL_AGENT sub-stage routines
  //

  private void processInstallAgentSubStage(State currentState) {

    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    Operation deploymentOp = cloudStoreHelper.createGet(currentState.deploymentServiceLink);
    Operation hostOp = cloudStoreHelper.createGet(currentState.hostServiceLink);

    OperationJoin
        .create(hostOp, deploymentOp)
        .setCompletion((ops, exs) -> {
          if (exs != null && !exs.isEmpty()) {
            failTask(exs.values());
            return;
          }

          try {
            processInstallAgentSubStage(currentState,
                ops.get(deploymentOp.getId()).getBody(DeploymentService.State.class),
                ops.get(hostOp.getId()).getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);

  }

  private void processInstallAgentSubStage(State currentState,
                                           DeploymentService.State deploymentState,
                                           HostService.State hostState) {

    List<String> command = new ArrayList<>();
    command.add("./" + SCRIPT_NAME);
    command.add(hostState.hostAddress);
    command.add(hostState.userName);
    command.add(hostState.password);
    command.add(currentState.vibPath);

    if (deploymentState.syslogEndpoint != null) {
      command.add("-l");
      command.add(deploymentState.syslogEndpoint);
    }

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);

    File scriptLogFile = new File(deployerContext.getScriptLogDirectory(), SCRIPT_NAME + "-" +
        hostState.hostAddress + "-" + ServiceUtils.getIDFromDocumentSelfLink(currentState.documentSelfLink) + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);
    Futures.addCallback(futureTask, new FutureCallback<Integer>() {
      @Override
      public void onSuccess(@Nullable Integer result) {
        if (result == null) {
          failTask(new NullPointerException(SCRIPT_NAME + " returned null"));
        } else if (result != 0) {
          logScriptErrorAndFail(hostState, result, scriptLogFile);
        } else {
          sendStageProgressPatch(currentState, TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT);
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        failTask(throwable);
      }
    });
  }

  private void logScriptErrorAndFail(HostService.State hostState, Integer result, File scriptLogFile) {

    try {
      ServiceUtils.logSevere(this, SCRIPT_NAME + " returned " + result.toString());
      ServiceUtils.logSevere(this, "Script output: " + FileUtils.readFileToString(scriptLogFile));
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
    }

    failTask(new IllegalStateException("Deploying the agent to host " + hostState.hostAddress +
        " failed with exit code " + result.toString()));
  }

  //
  // WAIT_FOR_AGENT sub-stage routines
  //

  private void processWaitForAgentSubStage(State currentState) {

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            processWaitForAgentSubStage(currentState, op.getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processWaitForAgentSubStage(State currentState, HostService.State hostState) {
    try {
      AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
      agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      agentControlClient.getAgentStatus(new AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>() {
        @Override
        public void onComplete(AgentControl.AsyncClient.get_agent_status_call getAgentStatusCall) {
          try {
            AgentStatusResponse agentStatusResponse = getAgentStatusCall.getResult();
            AgentControlClient.ResponseValidator.checkAgentStatusResponse(agentStatusResponse);
            if (agentStatusResponse.getStatus().equals(AgentStatusCode.RESTARTING)) {
              throw new IllegalStateException("Agent is restarting");
            } else {
              sendStageProgressPatch(currentState, TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_AGENT);
            }
          } catch (Throwable t) {
            retryGetAgentStatusOrFail(currentState, hostState, t);
          }
        }

        @Override
        public void onError(Exception e) {
          retryGetAgentStatusOrFail(currentState, hostState, e);
        }
      });
    } catch (Throwable t) {
      retryGetAgentStatusOrFail(currentState, hostState, t);
    }
  }

  private void retryGetAgentStatusOrFail(State currentState, HostService.State hostState, Throwable t) {
    if (currentState.pollCount + 1 >= currentState.maximumPollCount) {
      ServiceUtils.logSevere(this, t);
      State patchState = buildPatch(TaskState.TaskStage.FAILED, null, new IllegalStateException(
          "The agent on host " + hostState.hostAddress + " failed to become ready after installation after " +
              Integer.toString(currentState.maximumPollCount) + " retries"));
      patchState.pollCount = currentState.pollCount + 1;
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      ServiceUtils.logTrace(this, t);
      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT, null);
      patchState.pollCount = currentState.pollCount + 1;
      getHost().schedule(() -> TaskUtils.sendSelfPatch(this, patchState), currentState.pollInterval,
          TimeUnit.MILLISECONDS);
    }
  }

  //
  // PROVISION_AGENT sub-stage routines
  //

  private void processProvisionAgentSubStage(State currentState) {

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            processProvisionAgentSubStage(currentState, op.getBody(DeploymentService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processProvisionAgentSubStage(State currentState, DeploymentService.State deploymentState) {

    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.GET_HOST_CONFIG, null);
    if (ControlFlags.disableOperationProcessingOnStageTransition(currentState.controlFlags)) {
      patchState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    }

    ProvisionAgentTaskService.State startState = new ProvisionAgentTaskService.State();
    startState.parentTaskReference = getUri();
    startState.parentPatchBody = Utils.toJson(patchState);
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.chairmanServerList = deploymentState.chairmanServerList;

    sendRequest(Operation
        .createPost(this, ProvisionAgentTaskFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  //
  // GET_HOST_CONFIG sub-stage routines
  //

  private void processGetHostConfigSubStage(State currentState) {

    HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          try {
            processGetHostConfigSubStage(currentState, op.getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processGetHostConfigSubStage(State currentState, HostService.State hostState) {
    try {
      HostClient hostClient = HostUtils.getHostClient(this);
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.getHostConfig(new AsyncMethodCallback<Host.AsyncClient.get_host_config_call>() {
        @Override
        public void onComplete(Host.AsyncClient.get_host_config_call getHostConfigCall) {
          try {
            GetConfigResponse response = getHostConfigCall.getResult();
            HostClient.ResponseValidator.checkGetConfigResponse(response);
            processHostConfig(currentState, response.getHostConfig());
          } catch (Throwable t) {
            failTask(t);
          }
        }

        @Override
        public void onError(Exception e) {
          failTask(e);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processHostConfig(State currentState, HostConfig hostConfig) {
    Set<String> reportedDataStores = null;
    Set<String> reportedImageDataStores = null;
    Set<String> reportedNetworks = null;
    Map<String, String> datastoreServiceLinks = null;
    Set<DatastoreService.State> datastoreStartStates = null;

    if (hostConfig.isSetDatastores()
        && hostConfig.getDatastores() != null
        && !hostConfig.getDatastores().isEmpty()) {

      reportedDataStores = new HashSet<>();
      for (Datastore datastore : hostConfig.getDatastores()) {
        reportedDataStores.add(datastore.getId());
      }

      datastoreServiceLinks = new HashMap<>();
      for (Datastore datastore : hostConfig.getDatastores()) {
        datastoreServiceLinks.put(datastore.getName(), DatastoreServiceFactory.SELF_LINK + "/" + datastore.getId());
      }

      datastoreStartStates = new HashSet<>();
      for (Datastore datastore : hostConfig.getDatastores()) {
        DatastoreService.State datastoreStartState = new DatastoreService.State();
        datastoreStartState.id = datastore.getId();
        datastoreStartState.name = datastore.getName();
        datastoreStartState.tags = datastore.getTags();
        datastoreStartState.type = datastore.getType().name();
        datastoreStartState.documentSelfLink = datastore.getId();

        if (hostConfig.isSetImage_datastore_ids()
            && hostConfig.getImage_datastore_ids() != null
            && hostConfig.getImage_datastore_ids().contains(datastore.getId())) {
          datastoreStartState.isImageDatastore = true;
        }

        datastoreStartStates.add(datastoreStartState);
      }
    }

    if (hostConfig.isSetNetworks() && hostConfig.getNetworks() != null) {
      reportedNetworks = new HashSet<>();
      for (Network network : hostConfig.getNetworks()) {
        if (network.getTypes() != null && network.getTypes().contains(NetworkType.VM)) {
          reportedNetworks.add(network.getId());
        }
      }
    }

    if (hostConfig.isSetImage_datastore_ids() && hostConfig.getImage_datastore_ids() != null) {
      reportedImageDataStores = new HashSet<>();
      for (String datastoreId : hostConfig.getImage_datastore_ids()) {
        reportedImageDataStores.add(datastoreId);
      }
    }

    HostService.State patchState = new HostService.State();
    patchState.state = HostState.READY;
    patchState.reportedDatastores = reportedDataStores;
    patchState.reportedImageDatastores = reportedImageDataStores;
    patchState.reportedNetworks = reportedNetworks;
    patchState.datastoreServiceLinks = datastoreServiceLinks;

    if (hostConfig.isSetCpu_count()) {
      patchState.cpuCount = hostConfig.getCpu_count();
    }

    if (hostConfig.isSetEsx_version()) {
      patchState.esxVersion = hostConfig.getEsx_version();
    }

    if (hostConfig.isSetMemory_mb()) {
      patchState.memoryMb = hostConfig.getMemory_mb();
    }

    if (datastoreStartStates == null) {
      patchHost(currentState, patchState);
      return;
    }

    OperationJoin
        .create(datastoreStartStates.stream()
            .map((datastoreState) -> HostUtils.getCloudStoreHelper(this)
                .createPost(DatastoreServiceFactory.SELF_LINK)
                .setBody(datastoreState)))
        .setCompletion((ops, exs) -> {
          for (Map.Entry<Long, Operation> entry : ops.entrySet()) {
            if (entry.getValue().getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
              exs.remove(entry.getKey());
            }
          }

          if (exs != null && !exs.isEmpty()) {
            failTask(exs.values());
            return;
          }

          try {
            patchHost(currentState, patchState);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void patchHost(State currentState, HostService.State patchState) {

    HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.hostServiceLink)
        .setBody(patchState)
        .setCompletion((o, e) -> {
          if (e != null) {
            failTask(e);
          } else {
            sendStageProgressPatch(currentState, TaskState.TaskStage.FINISHED, null);
          }
        })
        .sendWith(this);
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(State currentState, TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s : %s", taskStage, subStage);
    State patchState = buildPatch(taskStage, subStage, null);
    if (ControlFlags.disableOperationProcessingOnStageTransition(currentState.controlFlags)) {
      patchState.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
    }
    TaskUtils.sendSelfPatch(this, patchState);
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
                                    @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;
    patchState.taskState.subStage = subStage;

    if (t != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
