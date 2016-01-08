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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.ControlFlags;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.host.gen.AgentStatusCode;
import com.vmware.photon.controller.host.gen.AgentStatusResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP microservice which performs the task of provisioning an agent.
 */
public class ProvisionAgentTaskService extends StatefulService {

  private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";
  private static final String DEFAULT_AGENT_LOG_LEVEL = "debug";
  private static final String DEFAULT_AVAILABILITY_ZONE = "1";

  /**
   * This class defines the state of a {@link ProvisionAgentTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This class defines the possible sub-stages for a task.
     */
    public enum SubStage {
      PROVISION_AGENT,
      WAIT_FOR_AGENT,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link ProvisionAgentTaskService} task.
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
     * This value represents the document link of the {@link DeploymentService} in whose context the task is being
     * performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link HostService} on which to provision the agent.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the chairman server list with which to provision the agent.
     */
    @NotNull
    @Immutable
    public Set<String> chairmanServerList;

    /**
     * This value represents the maximum number of agent status polling iterations which should be attempted before
     * declaring failure.
     */
    @DefaultInteger(value = 60)
    @Positive
    @Immutable
    public Integer maximumPollCount;

    /**
     * This value represents the interval between polling iterations in milliseconds.
     */
    @DefaultInteger(value = 5000)
    @Positive
    @Immutable
    public Integer pollInterval;

    /**
     * This value represents the number of polling iterations which have been attempted by the current task.
     */
    @DefaultInteger(value = 0)
    public Integer pollCount;
  }

  public ProvisionAgentTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation operation) {
    ServiceUtils.logTrace(this, "Handling start operation");
    State startState = operation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PROVISION_AGENT;
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

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    validateTaskState(currentState.taskState);
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
          case PROVISION_AGENT:
          case WAIT_FOR_AGENT:
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
      case PROVISION_AGENT:
        processProvisionAgentSubStage(currentState);
        break;
      case WAIT_FOR_AGENT:
        processWaitForAgentSubStage(currentState);
        break;
    }
  }

  //
  // PROVISION_AGENT sub-stage methods
  //

  private void processProvisionAgentSubStage(State currentState) {

    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    Operation deploymentOp = cloudStoreHelper.createGet(currentState.deploymentServiceLink);
    Operation hostOp = cloudStoreHelper.createGet(currentState.hostServiceLink);

    OperationJoin
        .create(deploymentOp, hostOp)
        .setCompletion((ops, exs) -> {
          if (exs != null && !exs.isEmpty()) {
            failTask(exs.values());
            return;
          }

          try {
            processProvisionAgentSubStage(currentState,
                ops.get(deploymentOp.getId()).getBody(DeploymentService.State.class),
                ops.get(hostOp.getId()).getBody(HostService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void processProvisionAgentSubStage(State currentState,
                                             DeploymentService.State deploymentState,
                                             HostService.State hostState) {

    List<String> datastores = null;
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
      String[] allowedDatastores = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)
          .trim().split(COMMA_DELIMITED_REGEX);
      datastores = new ArrayList<>(allowedDatastores.length);
      Collections.addAll(datastores, allowedDatastores);
    }

    List<String> networks = null;
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
      String[] allowedNetworks = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)
          .trim().split(COMMA_DELIMITED_REGEX);
      networks = new ArrayList<>(allowedNetworks.length);
      Collections.addAll(networks, allowedNetworks);
    }

    try {
      HostClient hostClient = HostUtils.getHostClient(this);
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.provision(
          (hostState.availabilityZone != null) ?
              hostState.availabilityZone :
              DEFAULT_AVAILABILITY_ZONE,
          datastores,
          deploymentState.imageDataStoreNames,
          deploymentState.imageDataStoreUsedForVMs,
          networks,
          hostState.hostAddress,
          hostState.agentPort,
          new ArrayList<>(currentState.chairmanServerList),
          0, // Overcommit ratio is not implemented,
          deploymentState.syslogEndpoint,
          DEFAULT_AGENT_LOG_LEVEL,
          (hostState.usageTags != null
              && hostState.usageTags.contains(UsageTag.MGMT.name())
              && !hostState.usageTags.contains(UsageTag.CLOUD.name())),
          ServiceUtils.getIDFromDocumentSelfLink(currentState.hostServiceLink),
          deploymentState.ntpEndpoint,
          new AsyncMethodCallback<Host.AsyncClient.provision_call>() {
            @Override
            public void onComplete(Host.AsyncClient.provision_call provisionCall) {
              try {
                HostClient.ResponseValidator.checkProvisionResponse(provisionCall.getResult());
                sendStageProgressPatch(currentState, TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT);
              } catch (Throwable t) {
                logProvisioningErrorAndFail(hostState, t);
              }
            }

            @Override
            public void onError(Exception e) {
              logProvisioningErrorAndFail(hostState, e);
            }
          });

    } catch (Throwable t) {
      logProvisioningErrorAndFail(hostState, t);
    }
  }

  private void logProvisioningErrorAndFail(HostService.State hostState, Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, new IllegalStateException(
        "Provisioning the agent on host " + hostState.hostAddress + " failed with error: " + failure)));
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
      HostClient hostClient = HostUtils.getHostClient(this);
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.getAgentStatus(new AsyncMethodCallback<Host.AsyncClient.get_agent_status_call>() {
        @Override
        public void onComplete(Host.AsyncClient.get_agent_status_call getAgentStatusCall) {
          try {
            AgentStatusResponse agentStatusResponse = getAgentStatusCall.getResult();
            HostClient.ResponseValidator.checkAgentStatusResponse(agentStatusResponse);
            if (agentStatusResponse.getStatus().equals(AgentStatusCode.RESTARTING)) {
              throw new IllegalStateException("Agent is restarting");
            } else {
              sendStageProgressPatch(currentState, TaskState.TaskStage.FINISHED, null);
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

  private void retryGetAgentStatusOrFail(State currentState, HostService.State hostState, Throwable failure) {
    if (currentState.pollCount + 1 >= currentState.maximumPollCount) {
      ServiceUtils.logSevere(this, failure);
      State patchState = buildPatch(TaskState.TaskStage.FAILED, null, new IllegalStateException(
          "The agent on host " + hostState.hostAddress + " failed to become ready after provisioning after " +
              Integer.toString(currentState.maximumPollCount) + " retries"));
      patchState.pollCount = currentState.pollCount + 1;
      TaskUtils.sendSelfPatch(this, patchState);
    } else {
      ServiceUtils.logTrace(this, failure);
      State patchState = buildPatch(currentState.taskState.stage, currentState.taskState.subStage, null);
      patchState.pollCount = currentState.pollCount + 1;
      getHost().schedule(() -> TaskUtils.sendSelfPatch(this, patchState), currentState.pollInterval,
          TimeUnit.MILLISECONDS);
    }
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
  protected static State buildPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage, @Nullable Throwable t) {
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
