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
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService.State;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.exceptions.InvalidAgentConfigurationException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.host.gen.AgentStatusCode;
import com.vmware.photon.controller.host.gen.AgentStatusResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP microservice which performs the task of provisioning an agent.
 */
public class ProvisionAgentTaskService extends StatefulService {

  private static final String DEFAULT_AGENT_LOG_LEVEL = "debug";
  private static final String COMMA_DELIMITED_REGEX = "\\s*,\\s*";

  // availability zone is currently required for the host provisioning but will be removed in the near future
  private static final String AVAILABILITY_ZONE = "1";
  private static final RpcException RESTART_EXCEPTION =
      new RpcException("Exceeded num of retries. Agent is rebooting.");

  /**
   * This class defines the document state associated with a single {@link ProvisionAgentTaskService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link DeploymentService} in whose context the task operation is
     * being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link to the {@link HostService} instance representing the ESX host on which
     * the agent should be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the list of chairman servers with which to provision the agent.
     */
    @NotNull
    @Immutable
    public Set<String> chairmanServerList;

    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the control flags for the current task.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;

    /**
     * This value represents the polling interval, in milliseconds, to use when waiting for agent provisioning to
     * complete.
     */
    @DefaultInteger(value = 5000)
    @Positive
    @Immutable
    public Integer agentPollDelay;

    /**
     * This value represents the maximum number of polling iterations which should be attempted before the operation
     * is considered failed.
     */
    @DefaultInteger(value = 60)
    @Positive
    @Immutable
    public Integer maximumPollCount;
  }

  public ProvisionAgentTaskService() {
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
        retrieveDocuments(currentState);
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

  private void retrieveDocuments(final State currentState) {

    Operation deploymentOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink);
    Operation hostOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.hostServiceLink);

    OperationJoin
        .create(deploymentOp, hostOp)
        .setCompletion(
            (ops, failures) -> {
              if (null != failures && failures.size() > 0) {
                failTask(failures);
                return;
              }

              try {
                processProvisionAgent(currentState,
                    ops.get(deploymentOp.getId()).getBody(DeploymentService.State.class),
                    ops.get(hostOp.getId()).getBody(HostService.State.class),
                    0);
              } catch (Throwable t) {
                failTask(t);
              }
            }
        )
        .sendWith(this);
  }

  private boolean isManagementOnlyHost(final HostService.State hostState) {
    boolean isManagementOnly = false;
    //If there is one usageTag and it is MGMT then it is a management-only host
    if (hostState.usageTags != null && hostState.usageTags.size() == 1
        && hostState.usageTags.contains(UsageTag.MGMT.name())) {
      isManagementOnly = true;
    }
    return isManagementOnly;
  }

  private void processProvisionAgent(final State currentState,
                                     final DeploymentService.State deploymentState,
                                     final HostService.State hostState,
                                     final int retryCount) {

    final Retryable retryable = new Retryable() {
      @Override
      public void retry() throws Throwable {
        processProvisionAgent(currentState, deploymentState, hostState, retryCount + 1);
      }

      @Override
      public boolean isRetryable(Throwable t) {
        return retryCount < currentState.maximumPollCount;
      }

      @Override
      public HostService.State getHostState() {
        return hostState;
      }
    };

    final AsyncMethodCallback<Host.AsyncClient.provision_call> handler =
        new AsyncMethodCallback<Host.AsyncClient.provision_call>() {
          @Override
          public void onComplete(Host.AsyncClient.provision_call provisionCall) {
            try {
              HostClient.ResponseValidator.checkProvisionResponse(provisionCall.getResult());
              processCheckAgent(currentState, hostState, 0);
            } catch (TException e) {
              retryOrFail(retryable, currentState, e);
            } catch (Throwable t) {
              retryOrFail(retryable, currentState, t);
            }
          }

          @Override
          public void onError(Exception e) {
            retryOrFail(retryable, currentState, e);
          }
        };

    HostClient hostClient = HostUtils.getHostClient(this);
    hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);

    List<String> dataStores = null;
    if (null != hostState.metadata && hostState.metadata.containsKey(
        HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {
      String[] allowedDataStores = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)
          .trim().split(COMMA_DELIMITED_REGEX);
      dataStores = new ArrayList<>(allowedDataStores.length);
      Collections.addAll(dataStores, allowedDataStores);
    }

    List<String> networks = null;
    if (null != hostState.metadata && hostState.metadata.containsKey(
        HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {
      String[] allowedNetworks = hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS).trim()
          .split(COMMA_DELIMITED_REGEX);
      networks = new ArrayList<>(allowedNetworks.length);
      Collections.addAll(networks, allowedNetworks);
    }

    boolean isManagementOnly = isManagementOnlyHost(hostState);
    String hostId = ServiceUtils.getIDFromDocumentSelfLink(currentState.hostServiceLink);
    ServiceUtils.logInfo(this, "Provisioning host with HostId %s", hostId);

    try {
      hostClient.provision(
          hostState.availabilityZone != null ? hostState.availabilityZone : AVAILABILITY_ZONE,
          dataStores,
          deploymentState.imageDataStoreNames,
          deploymentState.imageDataStoreUsedForVMs,
          networks,
          hostState.hostAddress,
          hostState.agentPort,
          null, // environment map is not implemented
          new ArrayList<>(currentState.chairmanServerList),
          0, // memory overcommit is not implemented
          deploymentState.syslogEndpoint,
          DEFAULT_AGENT_LOG_LEVEL,
          isManagementOnly,
          hostId,
          deploymentState.ntpEndpoint,
          handler);
    } catch (Throwable t) {
      retryOrFail(retryable, currentState, t);
    }
  }

  private void processCheckAgent(final State currentState, final HostService.State hostState, final int pollCount) {

    final Retryable retryable = new Retryable() {
      @Override
      public void retry() throws Throwable {
        processCheckAgent(currentState, hostState, pollCount + 1);
      }

      @Override
      public boolean isRetryable(Throwable t) {
        return pollCount < currentState.maximumPollCount;
      }

      @Override
      public HostService.State getHostState() {
        return hostState;
      }
    };

    final AsyncMethodCallback<Host.AsyncClient.get_agent_status_call> handler =
        new AsyncMethodCallback<Host.AsyncClient.get_agent_status_call>() {
          @Override
          public void onComplete(Host.AsyncClient.get_agent_status_call getAgentStatusCall) {
            try {
              AgentStatusResponse agentStatusResponse = getAgentStatusCall.getResult();
              HostClient.ResponseValidator.checkAgentStatusResponse(agentStatusResponse);
              if (agentStatusResponse.getStatus().equals(AgentStatusCode.RESTARTING)) {
                retryOrFail(retryable, currentState, RESTART_EXCEPTION);
                return;
              }
              sendStageProgressPatch(TaskState.TaskStage.FINISHED);
            } catch (InvalidAgentConfigurationException e) {
              failTask(new Throwable(e.getMessage() + "Host Address: " + hostState.hostAddress));
            } catch (TException e) {
              retryOrFail(retryable, currentState, new RpcException(e.getMessage()));
            } catch (Throwable t) {
              retryOrFail(retryable, currentState, t);
            }
          }

          @Override
          public void onError(Exception e) {
            retryOrFail(retryable, currentState, e);
          }
        };

    try {
      HostClient hostClient = HostUtils.getHostClient(this);
      hostClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);
      hostClient.getAgentStatus(handler);
    } catch (Throwable t) {
      retryOrFail(retryable, currentState, t);
    }
  }


  private void retryOrFail(final Retryable retryable, State currentState, Throwable t) {
    if (!retryable.isRetryable(t)) {
      HostService.State hostState = retryable.getHostState();

      failTask(new RuntimeException(
          "Agent is unreachable. ["
        + hostState.documentSelfLink
        + " - "
        + hostState.hostAddress
        + "]", t));
      return;
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          retryable.retry();
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    getHost().schedule(runnable, currentState.agentPollDelay, TimeUnit.MILLISECONDS);
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch with stage %s", taskStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void failTask(Map<Long, Throwable> failures) {
    failures.values().forEach(failure -> ServiceUtils.logSevere(this, failure));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, failures.values().iterator().next()));
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

  private interface Retryable {
    void retry() throws Throwable;

    boolean isRetryable(Throwable t);

    HostService.State getHostState();
  }
}
