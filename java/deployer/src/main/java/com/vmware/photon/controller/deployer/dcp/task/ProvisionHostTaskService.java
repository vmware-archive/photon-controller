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
import com.vmware.photon.controller.agent.gen.AgentStatusResponse;
import com.vmware.photon.controller.agent.gen.ProvisionResponse;
import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.clients.AgentControlClient;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultString;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.VibFactoryService;
import com.vmware.photon.controller.deployer.dcp.entity.VibService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.models.FabricNode;
import com.vmware.photon.controller.nsxclient.models.FabricNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.FabricNodeState;
import com.vmware.photon.controller.nsxclient.models.HostNodeLoginCredential;
import com.vmware.photon.controller.nsxclient.models.HostSwitch;
import com.vmware.photon.controller.nsxclient.models.TransportNode;
import com.vmware.photon.controller.nsxclient.models.TransportNodeCreateSpec;
import com.vmware.photon.controller.nsxclient.models.TransportNodeState;
import com.vmware.photon.controller.nsxclient.models.TransportZoneEndPoint;
import com.vmware.photon.controller.nsxclient.utils.NameUtils;
import com.vmware.photon.controller.stats.plugin.gen.StatsPluginConfig;
import com.vmware.photon.controller.stats.plugin.gen.StatsStoreType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.async.AsyncMethodCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class implements a DCP service which provisions a host and updates the corresponding cloud store documents.
 */
public class ProvisionHostTaskService extends StatefulService {

  /**
   * This string specifies the script which is used to configure syslog on a host.
   */
  public static final String CONFIGURE_SYSLOG_SCRIPT_NAME = "esx-configure-syslog";

  /**
   * This string specifies the script which is used to install a VIB.
   */
  public static final String INSTALL_VIB_SCRIPT_NAME = "esx-install-agent2";

  /**
   * This class defines the state of a {@link ProvisionHostTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages of a {@link ProvisionHostTaskService} task.
     */
    public enum SubStage {
      GET_NETWORK_MANAGER_INFO,
      CREATE_FABRIC_NODE,
      WAIT_FOR_FABRIC_NODE,
      CREATE_TRANSPORT_NODE,
      WAIT_FOR_TRANSPORT_NODE,
      CONFIGURE_SYSLOG,
      UPLOAD_VIBS,
      INSTALL_VIBS,
      WAIT_FOR_AGENT_START,
      PROVISION_AGENT,
      WAIT_FOR_AGENT_RESTART,
      WAIT_FOR_HOST_UPDATES,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link ProvisionHostTaskService} task.
   */
  @NoMigrationDuringUpgrade
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
     * This value represents the document link of the {@link DeploymentService} in whose context
     * the task is being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link HostService} representing the host to
     * be provisioned.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

    /**
     * This value represents the address of the NSX network manager API.
     */
    @WriteOnce
    public String networkManagerAddress;

    /**
     * This value represents the user name used to access the NSX network manager.
     */
    @WriteOnce
    public String networkManagerUserName;

    /**
     * This value represents the password used to access the NSX network manager.
     */
    @WriteOnce
    public String networkManagerPassword;

    /**
     * This value represents the NSX network zone ID associated with the current deployment.
     */
    @WriteOnce
    public String networkZoneId;

    /**
     * This value represents the interval, in milliseconds, to wait before starting to poll the
     * status of a task object in the NSX REST API.
     */
    @WriteOnce
    public Integer nsxPollDelay;

    /**
     * This value represents the ID of the NSX fabric node created by the current task.
     */
    @WriteOnce
    public String fabricNodeId;

    /**
     * This value represents the ID of the NSX transport node created by the current task.
     */
    @WriteOnce
    public String transportNodeId;

    /**
     * This value represents the delay, in milliseconds, which should be observed between agent
     * status polling iterations.
     */
    @WriteOnce
    public Integer agentStatusPollDelay;

    /**
     * This value represents the maximum number of polling iterations which should be attempted
     * while waiting for the agent to become ready after VIB installation.
     */
    @DefaultInteger(value = 60)
    @Immutable
    public Integer agentStartMaxPollCount;

    /**
     * This value represents the number of polling iterations which have been attempted by the
     * current task while waiting for the agent to become ready after VIB installation.
     */
    @DefaultInteger(value = 0)
    public Integer agentStartPollCount;

    /**
     * This value represents the log level parameter to be specified to the agent at provisioning
     * time.
     */
    @DefaultString(value = "debug")
    @Immutable
    public String agentLogLevel;

    /**
     * This value represents the maximum number of polling iterations which should be attempted
     * while waiting for the agent to become ready after provisioning.
     */
    @DefaultInteger(value = 60)
    @Immutable
    public Integer agentRestartMaxPollCount;

    /**
     * This value represents the number of polling iterations which have been attempted by the
     * current task while waiting for the agent to become ready after provisioning.
     */
    @DefaultInteger(value = 0)
    public Integer agentRestartPollCount;

    /**
     * This value represents the interval, in milliseconds, which should be observed between host
     * status polling iterations.
     */
    @WriteOnce
    public Integer hostStatusPollDelay;

    /**
     * This value represents the maximum number of polling iterations which should be attempted
     * while waiting for the host to become ready after provisioning.
     */
    @DefaultInteger(value = 12)
    @Immutable
    public Integer hostStatusMaxPollCount;

    /**
     * This value represents the number of polling iterations which have been attempted by the
     * current task while waiting for the host to become ready after provisioning.
     */
    @DefaultInteger(value = 0)
    public Integer hostStatusPollCount;
  }

  public ProvisionHostTaskService() {
    super(State.class);

    /**
     * These attributes are required because the {@link UploadVibTaskService} task is scheduled by
     * the task scheduler. If and when this is not the case -- either these attributes are no
     * longer required, or this task is not scheduled by the task scheduler -- then they should be
     * removed, along with the same attributes in higher-level task services which create instances
     * of this task.
     */
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
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

    if (startState.nsxPollDelay == null) {
      startState.nsxPollDelay = HostUtils.getDeployerContext(this).getNsxPollDelay();
    }

    if (startState.agentStatusPollDelay == null) {
      startState.agentStatusPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (startState.hostStatusPollDelay == null) {
      startState.hostStatusPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    try {
      validateState(startState);
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, t);
      return;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.GET_NETWORK_MANAGER_INFO);
      } else {
        throw new IllegalStateException("Task is not restartable");
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
    } catch (Throwable t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, t);
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
    validateTaskStage(currentState.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskStage(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void validateTaskStage(TaskState taskState) {
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
          case GET_NETWORK_MANAGER_INFO:
          case CREATE_FABRIC_NODE:
          case WAIT_FOR_FABRIC_NODE:
          case CREATE_TRANSPORT_NODE:
          case WAIT_FOR_TRANSPORT_NODE:
          case CONFIGURE_SYSLOG:
          case UPLOAD_VIBS:
          case INSTALL_VIBS:
          case WAIT_FOR_AGENT_START:
          case PROVISION_AGENT:
          case WAIT_FOR_AGENT_RESTART:
          case WAIT_FOR_HOST_UPDATES:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case GET_NETWORK_MANAGER_INFO:
        processGetNetworkManagerInfoSubStage(currentState);
        break;
      case CREATE_FABRIC_NODE:
        processCreateFabricNodeSubStage(currentState);
        break;
      case WAIT_FOR_FABRIC_NODE:
        processWaitForFabricNodeSubStage(currentState);
        break;
      case CREATE_TRANSPORT_NODE:
        processCreateTransportNodeSubStage(currentState);
        break;
      case WAIT_FOR_TRANSPORT_NODE:
        processWaitForTransportNodeSubStage(currentState);
        break;
      case CONFIGURE_SYSLOG:
        processConfigureSyslogSubStage(currentState);
        break;
      case UPLOAD_VIBS:
        processUploadVibsSubStage(currentState);
        break;
      case INSTALL_VIBS:
        processInstallVibsSubStage(currentState);
        break;
      case WAIT_FOR_AGENT_START:
        processWaitForAgentSubStage(currentState);
        break;
      case PROVISION_AGENT:
        processProvisionAgentSubStage(currentState);
        break;
      case WAIT_FOR_AGENT_RESTART:
        processWaitForAgentRestartSubStage(currentState);
        break;
      case WAIT_FOR_HOST_UPDATES:
        processWaitForHostUpdatesSubStage(currentState);
        break;
    }
  }

  //
  // GET_NETWORK_MANAGER_INFO sub-stage routines
  //

  private void processGetNetworkManagerInfoSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processGetNetworkManagerInfoSubStage(o.getBody(DeploymentService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processGetNetworkManagerInfoSubStage(DeploymentService.State deploymentState) {

    if (!deploymentState.virtualNetworkEnabled) {
      ServiceUtils.logInfo(this, "Skipping virtual network configuration (disabled)");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CONFIGURE_SYSLOG);
      return;
    }

    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_FABRIC_NODE);
    patchState.networkManagerAddress = deploymentState.networkManagerAddress;
    patchState.networkManagerUserName = deploymentState.networkManagerUsername;
    patchState.networkManagerPassword = deploymentState.networkManagerPassword;
    patchState.networkZoneId = deploymentState.networkZoneId;
    sendStageProgressPatch(patchState);
  }

  //
  // CREATE_FABRIC_NODE sub-stage routines
  //

  private void processCreateFabricNodeSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processCreateFabricNodeSubStage(currentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processCreateFabricNodeSubStage(State currentState, HostService.State hostState) throws Throwable {

    checkState(hostState.nsxFabricNodeId == null);

    NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
        currentState.networkManagerAddress,
        currentState.networkManagerUserName,
        currentState.networkManagerPassword);

    HostNodeLoginCredential hostNodeLoginCredential = new HostNodeLoginCredential();
    hostNodeLoginCredential.setUsername(hostState.userName);
    hostNodeLoginCredential.setPassword(hostState.password);
    hostNodeLoginCredential.setThumbprint(nsxClient.getHostThumbprint(hostState.hostAddress,
        ServicePortConstants.ESXI_PORT));

    FabricNodeCreateSpec request = new FabricNodeCreateSpec();
    request.setDisplayName(NameUtils.getFabricNodeName(hostState.hostAddress));
    request.setDescription(NameUtils.getFabricNodeDescription(hostState.hostAddress));
    request.setIpAddresses(Collections.singletonList(hostState.hostAddress));
    request.setOsType("ESXI");
    request.setResourceType("HostNode");
    request.setHostCredential(hostNodeLoginCredential);

    ObjectMapper om = new ObjectMapper();
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    String payload = om.writeValueAsString(request);
    ServiceUtils.logInfo(this, "FC request: " + payload);

    nsxClient.getFabricApi().registerFabricNode(request,
        new FutureCallback<FabricNode>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull FabricNode fabricNode) {
            try {
              setFabricNodeId(currentState, fabricNode.getId());
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void setFabricNodeId(State currentState, String fabricNodeId) {

    HostService.State hostPatchState = new HostService.State();
    hostPatchState.nsxFabricNodeId = fabricNodeId;

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createPatch(currentState.hostServiceLink)
        .setBody(hostPatchState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {

                  //
                  // N.B. Due to a quirk of the NSX REST API, it is necessary to wait for a period before
                  // starting to poll the status of the task.
                  //

                  getHost().schedule(
                      () -> {
                        State selfPatchState = buildPatch(TaskState.TaskStage.STARTED,
                            TaskState.SubStage.WAIT_FOR_FABRIC_NODE);
                        selfPatchState.fabricNodeId = fabricNodeId;
                        sendStageProgressPatch(selfPatchState);
                      },
                      currentState.nsxPollDelay,
                      TimeUnit.MILLISECONDS);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }
        ));
  }

  //
  // WAIT_FOR_FABRIC_NODE sub-stage routines
  //

  private void processWaitForFabricNodeSubStage(State currentState) throws Throwable {

    NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
        currentState.networkManagerAddress,
        currentState.networkManagerUserName,
        currentState.networkManagerPassword);

    nsxClient.getFabricApi().getFabricNodeState(currentState.fabricNodeId,
        new FutureCallback<FabricNodeState>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull FabricNodeState fabricNodeState) {
            try {
              switch (fabricNodeState.getState()) {
                case SUCCESS:
                  sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_TRANSPORT_NODE);
                  break;
                case PENDING:
                case IN_PROGRESS:
                  getHost().schedule(
                      () -> sendStageProgressPatch(TaskState.TaskStage.STARTED,
                          TaskState.SubStage.WAIT_FOR_FABRIC_NODE),
                      currentState.nsxPollDelay,
                      TimeUnit.MILLISECONDS);
                  break;
                case FAILED:
                case PARTIAL_SUCCESS:
                case ORPHANED:
                  logFabricNodeResultAndFail(currentState, fabricNodeState);
                  break;
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void logFabricNodeResultAndFail(State currentState, FabricNodeState fabricNodeState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  throw new IllegalStateException("Registering host " +
                      o.getBody(HostService.State.class).hostAddress + " as a fabric node failed with result " +
                      fabricNodeState.getState() + " (fabric node ID " + currentState.fabricNodeId + ")");
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // CREATE_TRANSPORT_NODE sub-stage routines
  //

  private void processCreateTransportNodeSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processCreateTransportNodeSubStage(currentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processCreateTransportNodeSubStage(State currentState, HostService.State hostState) throws Throwable {

    checkState(hostState.nsxTransportNodeId == null);

    NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
        currentState.networkManagerAddress,
        currentState.networkManagerUserName,
        currentState.networkManagerPassword);

    HostSwitch hostSwitch = new HostSwitch();
    hostSwitch.setName(NameUtils.HOST_SWITCH_NAME);

    TransportNodeCreateSpec request = new TransportNodeCreateSpec();
    request.setDisplayName(NameUtils.getTransportNodeName(hostState.hostAddress));
    request.setDescription(NameUtils.getTransportNodeDescription(hostState.hostAddress));
    request.setNodeId(currentState.fabricNodeId);
    request.setHostSwitches(Collections.singletonList(hostSwitch));

    if (currentState.networkZoneId != null) {
      TransportZoneEndPoint transportZoneEndPoint = new TransportZoneEndPoint();
      transportZoneEndPoint.setTransportZoneId(currentState.networkZoneId);
      request.setTransportZoneEndPoints(Collections.singletonList(transportZoneEndPoint));
    }

    nsxClient.getFabricApi().createTransportNode(request,
        new FutureCallback<TransportNode>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull TransportNode transportNode) {
            try {
              setTransportNodeId(currentState, transportNode.getId());
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void setTransportNodeId(State currentState, String transportNodeId) {

    HostService.State hostPatchState = new HostService.State();
    hostPatchState.nsxTransportNodeId = transportNodeId;

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createPatch(currentState.hostServiceLink)
        .setBody(hostPatchState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {

                  //
                  // N.B. Due to a quirk of the NSX REST API, it is necessary to wait for a period before
                  // starting to poll the status of the task.
                  //

                  getHost().schedule(
                      () -> {
                        State selfPatchState = buildPatch(TaskState.TaskStage.STARTED,
                            TaskState.SubStage.WAIT_FOR_TRANSPORT_NODE);
                        selfPatchState.transportNodeId = transportNodeId;
                        sendStageProgressPatch(selfPatchState);
                      },
                      currentState.nsxPollDelay,
                      TimeUnit.MILLISECONDS);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // WAIT_FOR_TRANSPORT_NODE sub-stage routines
  //

  private void processWaitForTransportNodeSubStage(State currentState) throws Throwable {

    NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
        currentState.networkManagerAddress,
        currentState.networkManagerUserName,
        currentState.networkManagerPassword);

    nsxClient.getFabricApi().getTransportNodeState(currentState.transportNodeId,
        new FutureCallback<TransportNodeState>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull TransportNodeState transportNodeState) {
            try {
              switch (transportNodeState.getState()) {
                case SUCCESS:
                  sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CONFIGURE_SYSLOG);
                  break;
                case PENDING:
                case IN_PROGRESS:
                  getHost().schedule(
                      () -> sendStageProgressPatch(TaskState.TaskStage.STARTED,
                          TaskState.SubStage.WAIT_FOR_TRANSPORT_NODE),
                      currentState.nsxPollDelay,
                      TimeUnit.MILLISECONDS);
                  break;
                case FAILED:
                case PARTIAL_SUCCESS:
                case ORPHANED:
                  logTransportNodeResultAndFail(currentState, transportNodeState);
                  break;
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void logTransportNodeResultAndFail(State currentState, TransportNodeState transportNodeState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  throw new IllegalStateException("Registering host " +
                      o.getBody(HostService.State.class).hostAddress + " as a transport node failed with result " +
                      transportNodeState.getState() + " (transport node ID " + currentState.transportNodeId + ")");
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // CONFIGURE_SYSLOG sub-stage routines
  //

  private void processConfigureSyslogSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processConfigureSyslogSubStage(currentState, o.getBody(DeploymentService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processConfigureSyslogSubStage(State currentState, DeploymentService.State deploymentState) {

    if (deploymentState.syslogEndpoint == null) {
      ServiceUtils.logInfo(this, "Skipping syslog endpoint configuration (disabled)");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_VIBS);
      return;
    }

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processConfigureSyslogSubStage(deploymentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processConfigureSyslogSubStage(DeploymentService.State deploymentState, HostService.State hostState) {

    List<String> command = Arrays.asList(
        "./" + CONFIGURE_SYSLOG_SCRIPT_NAME,
        hostState.hostAddress,
        hostState.userName,
        hostState.password,
        deploymentState.syslogEndpoint);

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);

    File scriptLogFile = new File(deployerContext.getScriptLogDirectory(), CONFIGURE_SYSLOG_SCRIPT_NAME + "-" +
        hostState.hostAddress + "-" + ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink) + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .redirectErrorStream(true)
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    Futures.addCallback(futureTask,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull Integer result) {
            try {
              if (result != 0) {
                logSyslogConfigurationErrorAndFail(hostState, result, scriptLogFile);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_VIBS);
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void logSyslogConfigurationErrorAndFail(HostService.State hostState,
                                                  Integer result,
                                                  File scriptLogFile) throws Throwable {

    ServiceUtils.logSevere(this, CONFIGURE_SYSLOG_SCRIPT_NAME + " returned " + result);
    ServiceUtils.logSevere(this, "Script output: " + FileUtils.readFileToString(scriptLogFile));
    throw new IllegalStateException("Configuring syslog on host " + hostState.hostAddress + " failed with exit code " +
        result);
  }

  //
  // UPLOAD_VIBS sub-stage routines
  //

  private void processUploadVibsSubStage(State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VibService.State.class)
            .addFieldClause(VibService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
        .addOptions(EnumSet.of(
            QueryTask.QuerySpecification.QueryOption.BROADCAST,
            QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processUploadVibsSubStage(currentState, o.getBody(QueryTask.class).results.documents);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processUploadVibsSubStage(State currentState, Map<String, Object> vibDocuments) {

    Set<String> existingVibNames = vibDocuments.values().stream()
        .map((vibDocument) -> Utils.fromJson(vibDocument, VibService.State.class))
        .map((vibState) -> vibState.vibName)
        .collect(Collectors.toSet());

    File sourceDirectory = new File(HostUtils.getDeployerContext(this).getVibDirectory());
    if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
      throw new IllegalStateException("Invalid VIB source directory " + sourceDirectory);
    }

    File[] vibFiles = sourceDirectory.listFiles((file) -> file.getName().toUpperCase().endsWith(".VIB"));
    if (vibFiles.length == 0) {
      throw new IllegalStateException("No VIB files were found in source directory " + sourceDirectory);
    }

    Set<File> vibFilesToUpload = Stream.of(vibFiles)
        .filter((vibFile) -> !existingVibNames.contains(vibFile.getName()))
        .collect(Collectors.toSet());

    if (vibFilesToUpload.isEmpty()) {
      ServiceUtils.logInfo(this, "Found no VIB files to upload");
      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.INSTALL_VIBS);
      return;
    }

    Stream<Operation> vibStartOps = vibFilesToUpload.stream().map((vibFile) -> {
      VibService.State startState = new VibService.State();
      startState.vibName = vibFile.getName();
      startState.hostServiceLink = currentState.hostServiceLink;
      return Operation.createPost(this, VibFactoryService.SELF_LINK).setBody(startState);
    });

    OperationJoin
        .create(vibStartOps)
        .setCompletion(
            (ops, exs) -> {
              try {
                if (exs != null && !exs.isEmpty()) {
                  failTask(exs.values());
                } else {
                  createUploadVibTasks(ops.values());
                }
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void createUploadVibTasks(Collection<Operation> vibStartOps) {

    ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
    startState.parentTaskLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(buildPatch(TaskState.TaskStage.STARTED,
        TaskState.SubStage.INSTALL_VIBS));
    startState.pendingCompletionCount = vibStartOps.size();
    startState.errorThreshold = 0.0;

    sendRequest(Operation
        .createPost(this, ChildTaskAggregatorFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  createUploadVibTasks(vibStartOps, o.getBody(ServiceDocument.class).documentSelfLink);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createUploadVibTasks(Collection<Operation> vibStartOps, String aggregatorServiceLink) {

    Stream<Operation> taskStartOps = vibStartOps.stream().map((vibStartOp) -> {
      UploadVibTaskService.State startState = new UploadVibTaskService.State();
      startState.parentTaskServiceLink = aggregatorServiceLink;
      startState.vibServiceLink = vibStartOp.getBody(ServiceDocument.class).documentSelfLink;
      return Operation.createPost(this, UploadVibTaskFactoryService.SELF_LINK).setBody(startState);
    });

    OperationJoin
        .create(taskStartOps)
        .setCompletion(
            (ops, exs) -> {
              try {
                if (exs != null && !exs.isEmpty()) {
                  failTask(exs.values());
                }
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  //
  // INSTALL_VIBS sub-stage routines
  //
  // N.B. Multiple VIBs may have been uploaded to the host -- either by this task, or previously
  // during upgrae initialization. ESX does not handle parallel VIB installation gracefully, so
  // this sub-stage will install VIBs in sequence. It does this by querying the set of VIB service
  // entities associated with the host and -- if any are found -- by selecting one at random,
  // installing it, deleting the VIB service entity, and self-patching to the same sub-stage (e.g.
  // INSTALL_VIBS) to retry the query. Only when the query returns no results will the service
  // transition to the next sub-stage.
  //

  private void processInstallVibsSubStage(State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VibService.State.class)
            .addFieldClause(VibService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
        .build();

    sendRequest(Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processInstallVibsSubStage(QueryTaskUtils.getBroadcastQueryDocumentLinks(o));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processInstallVibsSubStage(Set<String> vibServiceLinks) {

    if (vibServiceLinks.isEmpty()) {
      ServiceUtils.logInfo(this, "Found no remaining VIBs to install");
      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT_START);
      patchState.agentStartPollCount = 1;
      sendStageProgressPatch(patchState);
      return;
    }

    sendRequest(Operation
        .createGet(this, vibServiceLinks.iterator().next())
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processInstallVibsSubStage(o.getBody(VibService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processInstallVibsSubStage(VibService.State vibState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(vibState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processInstallVibsSubStage(vibState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processInstallVibsSubStage(VibService.State vibState, HostService.State hostState) {

    List<String> command = Arrays.asList(
        "./" + INSTALL_VIB_SCRIPT_NAME,
        hostState.hostAddress,
        hostState.userName,
        hostState.password,
        vibState.uploadPath);

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);

    File scriptLogFile = new File(deployerContext.getScriptLogDirectory(), INSTALL_VIB_SCRIPT_NAME + "-" +
        hostState.hostAddress + "-" + ServiceUtils.getIDFromDocumentSelfLink(vibState.documentSelfLink) + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .redirectErrorStream(true)
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    Futures.addCallback(futureTask,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull Integer result) {
            try {
              if (result != 0) {
                logVibInstallationFailureAndFail(vibState, hostState, result, scriptLogFile);
              } else {
                deleteVibService(vibState);
              }
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void logVibInstallationFailureAndFail(VibService.State vibState,
                                                HostService.State hostState,
                                                int result,
                                                File scriptLogFile) throws Throwable {

    ServiceUtils.logSevere(this, INSTALL_VIB_SCRIPT_NAME + " returned " + result);
    ServiceUtils.logSevere(this, "Script output: " + FileUtils.readFileToString(scriptLogFile));
    throw new IllegalStateException("Installing VIB file " + vibState.vibName + " to host " + hostState.hostAddress +
        " failed with exit code " + result);
  }

  private void deleteVibService(VibService.State vibState) {

    sendRequest(Operation
        .createDelete(this, vibState.documentSelfLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.INSTALL_VIBS);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // WAIT_FOR_AGENT_START sub-stage routines
  //

  private void processWaitForAgentSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processWaitForAgentSubStage(currentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processWaitForAgentSubStage(State currentState, HostService.State hostState) throws Throwable {

    AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
    agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);

    try {
      agentControlClient.getAgentStatus(
          new AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>() {
            @Override
            public void onComplete(AgentControl.AsyncClient.get_agent_status_call agentStatusCall) {
              try {
                AgentStatusResponse response = agentStatusCall.getResult();
                AgentControlClient.ResponseValidator.checkAgentStatusResponse(response, hostState.hostAddress);
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_AGENT);
              } catch (Throwable t) {
                retryWaitForAgentOrFail(currentState, hostState, t);
              }
            }

            @Override
            public void onError(Exception exception) {
              retryWaitForAgentOrFail(currentState, hostState, exception);
            }
          });

    } catch (Throwable t) {
      retryWaitForAgentOrFail(currentState, hostState, t);
    }
  }

  private void retryWaitForAgentOrFail(State currentState,
                                       HostService.State hostState,
                                       Throwable failure) {

    if (currentState.agentStartPollCount >= currentState.agentStartMaxPollCount) {
      ServiceUtils.logSevere(this, failure);
      failTask(new IllegalStateException("The agent on host " + hostState.hostAddress +
          " failed to become ready after installation after " + currentState.agentStartPollCount + " retries"));
    } else {
      ServiceUtils.logTrace(this, failure);
      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT_START);
      patchState.agentStartPollCount = currentState.agentStartPollCount + 1;

      getHost().schedule(
          () -> TaskUtils.sendSelfPatch(this, patchState),
          currentState.agentStatusPollDelay,
          TimeUnit.MILLISECONDS);
    }
  }

  //
  // PROVISION_AGENT sub-stage routines
  //

  private void processProvisionAgentSubStage(State currentState) {

    CloudStoreHelper cloudStoreHelper = HostUtils.getCloudStoreHelper(this);
    Operation deploymentGetOp = cloudStoreHelper.createGet(currentState.deploymentServiceLink);
    Operation hostGetOp = cloudStoreHelper.createGet(currentState.hostServiceLink);

    OperationJoin
        .create(deploymentGetOp, hostGetOp)
        .setCompletion(
            (ops, exs) -> {
              try {
                if (exs != null && !exs.isEmpty()) {
                  failTask(exs.values());
                } else {
                  processProvisionAgentSubStage(currentState,
                      ops.get(deploymentGetOp.getId()).getBody(DeploymentService.State.class),
                      ops.get(hostGetOp.getId()).getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processProvisionAgentSubStage(State currentState,
                                             DeploymentService.State deploymentState,
                                             HostService.State hostState) {

    List<String> allowedDatastores = null;
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)) {

      allowedDatastores = Stream.of(
          hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_DATASTORES)
              .trim()
              .split("\\s*,\\s*"))
          .collect(Collectors.toList());
    }

    List<String> allowedNetworks = null;
    if (hostState.metadata != null
        && hostState.metadata.containsKey(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)) {

      allowedNetworks = Stream.of(
          hostState.metadata.get(HostService.State.METADATA_KEY_NAME_ALLOWED_NETWORKS)
              .trim()
              .split("\\s*,\\s*"))
          .collect(Collectors.toList());
    }

    StatsPluginConfig statsPluginConfig = new StatsPluginConfig(deploymentState.statsEnabled);
    if (deploymentState.statsStoreEndpoint != null) {
      statsPluginConfig.setStats_store_endpoint(deploymentState.statsStoreEndpoint);
    }

    if (deploymentState.statsStorePort != null) {
      statsPluginConfig.setStats_store_port(deploymentState.statsStorePort);
    }

    if (deploymentState.statsStoreType != null) {
      statsPluginConfig.setStats_store_type(StatsStoreType.findByValue(deploymentState.statsStoreType.ordinal()));
    }

    if (hostState.usageTags != null) {
      // Agent accepts stats' tags as comma separated string.
      // Concatenate usageTags as one tag for stats so that they could be
      // queried easily. For example, user can query all metrics
      // having tag equal to 'MGMT-CLOUD' or '*MGMT*'.
      List<String> usageTagList = new ArrayList<>(hostState.usageTags);
      Collections.sort(usageTagList);
      statsPluginConfig.setStats_host_tags(Joiner.on("-").skipNulls().join(usageTagList));
    }

    AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
    agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);

    try {
      agentControlClient.provision(
          allowedDatastores,
          deploymentState.imageDataStoreNames,
          deploymentState.imageDataStoreUsedForVMs,
          allowedNetworks,
          hostState.hostAddress,
          hostState.agentPort,
          0, // Overcommit ratio is not implemented
          deploymentState.syslogEndpoint,
          currentState.agentLogLevel,
          statsPluginConfig,
          (hostState.usageTags != null
              && hostState.usageTags.contains(UsageTag.MGMT.name())
              && !hostState.usageTags.contains(UsageTag.CLOUD.name())),
          ServiceUtils.getIDFromDocumentSelfLink(hostState.documentSelfLink),
          ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink),
          deploymentState.ntpEndpoint,
          new AsyncMethodCallback<AgentControl.AsyncClient.provision_call>() {
            @Override
            public void onComplete(AgentControl.AsyncClient.provision_call provisionCall) {
              try {
                ProvisionResponse result = provisionCall.getResult();
                AgentControlClient.ResponseValidator.checkProvisionResponse(result);
                State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT_RESTART);
                patchState.agentRestartPollCount = 1;
                sendStageProgressPatch(patchState);
              } catch (Throwable t) {
                logProvisioningErrorAndFail(hostState, t);
              }
            }

            @Override
            public void onError(Exception exception) {
              logProvisioningErrorAndFail(hostState, exception);
            }
          });

    } catch (Throwable t) {
      logProvisioningErrorAndFail(hostState, t);
    }
  }

  private void logProvisioningErrorAndFail(HostService.State hostState, Throwable failure) {
    ServiceUtils.logSevere(this, failure);
    failTask(new IllegalStateException("Provisioning the agent on host " + hostState.hostAddress +
        " failed with error " + failure));
  }

  //
  // WAIT_FOR_AGENT_RESTART sub-stage routines
  //

  private void processWaitForAgentRestartSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processWaitForAgentRestartSubStage(currentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processWaitForAgentRestartSubStage(State currentState, HostService.State hostState) {

    AgentControlClient agentControlClient = HostUtils.getAgentControlClient(this);
    agentControlClient.setIpAndPort(hostState.hostAddress, hostState.agentPort);

    try {
      agentControlClient.getAgentStatus(
          new AsyncMethodCallback<AgentControl.AsyncClient.get_agent_status_call>() {
            @Override
            public void onComplete(AgentControl.AsyncClient.get_agent_status_call agentStatusCall) {
              try {
                AgentStatusResponse result = agentStatusCall.getResult();
                AgentControlClient.ResponseValidator.checkAgentStatusResponse(result, hostState.hostAddress);
                updateHostState(hostState.documentSelfLink);
              } catch (Throwable t) {
                retryWaitForAgentRestartOrFail(currentState, hostState, t);
              }
            }

            @Override
            public void onError(Exception exception) {
              retryWaitForAgentRestartOrFail(currentState, hostState, exception);
            }
          });

    } catch (Throwable t) {
      retryWaitForAgentRestartOrFail(currentState, hostState, t);
    }
  }

  private void retryWaitForAgentRestartOrFail(State currentState,
                                              HostService.State hostState,
                                              Throwable failure) {

    if (currentState.agentRestartPollCount >= currentState.agentRestartMaxPollCount) {
      ServiceUtils.logSevere(this, failure);
      failTask(new IllegalStateException("The agent on host " + hostState.hostAddress +
          " failed to become ready after provisioning after " + currentState.agentRestartPollCount + " retries"));
    } else {
      ServiceUtils.logTrace(this, failure);
      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_AGENT_RESTART);
      patchState.agentRestartPollCount = currentState.agentRestartPollCount + 1;

      getHost().schedule(
          () -> TaskUtils.sendSelfPatch(this, patchState),
          currentState.agentStatusPollDelay,
          TimeUnit.MILLISECONDS);
    }
  }

  private void updateHostState(String hostServiceLink) {

    HostService.State hostPatchState = new HostService.State();
    hostPatchState.state = HostState.READY;

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createPatch(hostServiceLink)
        .setBody(hostPatchState)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_HOST_UPDATES);
                  patchState.hostStatusPollCount = 1;
                  sendStageProgressPatch(patchState);
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  //
  // WAIT_FOR_HOST_UPDATES sub-stage routines
  //

  private void processWaitForHostUpdatesSubStage(State currentState) {

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  processWaitForHostUpdatesSubStage(currentState, o.getBody(HostService.State.class));
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void processWaitForHostUpdatesSubStage(State currentState, HostService.State hostState) {

    if (hostState.esxVersion != null) {
      sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
      return;
    }

    if (currentState.hostStatusPollCount >= currentState.hostStatusMaxPollCount) {
      failTask(new IllegalStateException("Host " + hostState.hostAddress + " failed to become ready after " +
          currentState.hostStatusMaxPollCount + " retries"));
    } else {
      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_HOST_UPDATES);
      patchState.hostStatusPollCount = currentState.hostStatusPollCount + 1;

      getHost().schedule(
          () -> TaskUtils.sendSelfPatch(this, patchState),
          currentState.hostStatusPollDelay,
          TimeUnit.MILLISECONDS);
    }
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    sendStageProgressPatch(buildPatch(taskStage, subStage, null));
  }

  private void sendStageProgressPatch(State patchState) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s : %s", patchState.taskState.stage,
        patchState.taskState.subStage);
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
