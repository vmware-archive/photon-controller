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

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ChangeHostModeTaskService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.DeleteAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP service representing the workflow of tearing down a host.
 */
public class DeprovisionHostWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link DeprovisionHostWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this task.
     */
    public enum SubStage {
      PUT_HOST_TO_DEPROVISION_MODE,
      RECONFIGURE_ZOOKEEPER,
      DELETE_AGENT,
      DEPROVISION_NETWORK,
      DELETE_ENTITIES,
    }
  }

  /**
   * This class defines the document state associated with a single {@link DeprovisionHostWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the document link of the {@link DeploymentService}
     * object which represents the deployment.
     */
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document link of the {@link HostService}
     * object which represents the host to be torn down.
     */
    @NotNull
    @Immutable
    public String hostServiceLink;

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
     * This value represents the unique ID of the current task.
     */
    @DefaultUuid
    @Immutable
    public String uniqueId;

    /**
     * This value represents the interval, in milliseconds, to use when polling the state of a task object returned by
     * an API call.
     */
    @Immutable
    public Integer taskPollDelay;

    public List<VmService.State> vmServiceStates;
  }

  public DeprovisionHostWorkflowService() {
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

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PUT_HOST_TO_DEPROVISION_MODE;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
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
    validateState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        handleStartedStage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State state) {
    ValidationUtils.validateState(state);
    ValidationUtils.validateTaskStage(state.taskState);
    validateTaskSubStage(state.taskState);
  }

  private void validateTaskSubStage(TaskState taskState) {
    switch (taskState.stage) {
      case CREATED:
        checkState(null == taskState.subStage);
        break;
      case STARTED:
        checkState(null != taskState.subStage);
        break;
      case FINISHED:
      case FAILED:
      case CANCELLED:
        checkState(null == taskState.subStage);
        break;
    }
  }

  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  private State applyPatch(State startState, State patchState) {
    if (startState.taskState.stage != patchState.taskState.stage
        || startState.taskState.subStage != patchState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void handleStartedStage(final State currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  HostService.State hostState = completedOp.getBody(HostService.State.class);
                  if (hostState.state == HostState.CREATING ||
                      hostState.state == HostState.NOT_PROVISIONED ||
                      hostState.state == HostState.DELETED) {
                    ServiceUtils.logInfo(this, "The host is not provisioned but %s, returning....",
                        hostState.state.name());
                    sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
                    return;
                  }

                  boolean ignoreError = hostState.state == HostState.ERROR;
                  if (ignoreError) {
                    ServiceUtils.logInfo(this, "We are ignoring deprovision errors since the host is in ERROR state");
                  }

                  switch (currentState.taskState.subStage) {
                    case PUT_HOST_TO_DEPROVISION_MODE:
                      putHostToDeprovisionMode(currentState, ignoreError);
                      break;
                    case RECONFIGURE_ZOOKEEPER:
                      updateZookeeperMapAndHostService(currentState, ignoreError);
                      break;
                    case DELETE_AGENT:
                      handleDeleteAgent(currentState, ignoreError);
                      break;
                    case DEPROVISION_NETWORK:
                      deprovisionNetwork(currentState, hostState, ignoreError);
                      break;
                    case DELETE_ENTITIES:
                      deleteEntities(currentState, ignoreError);
                      break;
                  }
                }
            ));
  }

  private void putHostToDeprovisionMode(State currentState, boolean ignoreError) {
    final Service service = this;

    ChangeHostModeTaskService.State state = new ChangeHostModeTaskService.State();
    state.hostServiceLink = currentState.hostServiceLink;
    state.hostMode = HostMode.DEPROVISIONED;

    FutureCallback<ChangeHostModeTaskService.State> futureCallback = new
        FutureCallback<ChangeHostModeTaskService.State>() {
          @Override
          public void onSuccess(@Nullable ChangeHostModeTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.RECONFIGURE_ZOOKEEPER);
                break;
              case FAILED:
                if (ignoreError) {
                  sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.RECONFIGURE_ZOOKEEPER);
                } else {
                  State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                  patchState.taskState.failure = result.taskState.failure;
                  TaskUtils.sendSelfPatch(service, patchState);
                }
                break;
              case CANCELLED:
                sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            if (ignoreError) {
              sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.RECONFIGURE_ZOOKEEPER);
            } else {
              failTask(t);
            }
          }
        };

    TaskUtils.startTaskAsync(this,
        ChangeHostModeTaskFactoryService.SELF_LINK,
        state,
        (taskState) -> TaskUtils.finalTaskStages.contains(taskState.taskState.stage),
        ChangeHostModeTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private void handleDeleteAgent(State currentState, boolean ignoreError) {
    final Service service = this;

    FutureCallback<DeleteAgentTaskService.State> futureCallback = new FutureCallback<DeleteAgentTaskService.State>() {
      @Override
      public void onSuccess(@Nullable DeleteAgentTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DEPROVISION_NETWORK);
            break;
          case FAILED:
            if (ignoreError) {
              sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
            } else {
              State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
              patchState.taskState.failure = result.taskState.failure;
              TaskUtils.sendSelfPatch(service, patchState);
            }
            break;
          case CANCELLED:
            sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (ignoreError) {
          sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        } else {
          failTask(t);
        }
      }
    };

    TaskUtils.startTaskAsync(this,
        DeleteAgentTaskFactoryService.SELF_LINK,
        createDeleteAgentStartState(currentState),
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        DeleteAgentTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private DeleteAgentTaskService.State createDeleteAgentStartState(State currentState) {
    DeleteAgentTaskService.State startState = new DeleteAgentTaskService.State();
    startState.taskState = new com.vmware.xenon.common.TaskState();
    startState.taskState.stage = com.vmware.xenon.common.TaskState.TaskStage.CREATED;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.uniqueID = currentState.uniqueId;
    return startState;
  }

  private void updateZookeeperMapAndHostService(State currentState, boolean ignoreError) {
    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
        .build();

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

            List<VmService.State> vmServiceStates =
                QueryTaskUtils.getBroadcastQueryDocuments(VmService.State.class, operation);

            if (vmServiceStates.size() == 0) {
              sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_AGENT);
            } else {
              updateZookeeperMapAndHostService(currentState, vmServiceStates, ignoreError);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void handleZookeeperStepFailure(Throwable failure, String logMessage, boolean ignoreError) {
    if (ignoreError) {
      ServiceUtils.logInfo(this, "Ignoring error: " + logMessage);
      sendStageProgressPatch(TaskState.TaskStage.STARTED,
          TaskState.SubStage.DELETE_AGENT);
    } else {
      failTask(failure);
    }
  }

  private void updateZookeeperMapAndHostService(State currentState, List<VmService.State> vmServiceStates, boolean
      ignoreError) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = kindClause;
    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    handleZookeeperStepFailure(failure, "Error while getting DeploymentService", ignoreError);
                    return;
                  }

                  try {
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(DeprovisionHostWorkflowService.this, documentLinks);
                    List<DeploymentService.State> deploymentServices =
                        QueryTaskUtils.getBroadcastQueryDocuments(DeploymentService.State.class, completedOp);
                    if (deploymentServices == null || deploymentServices.size() == 0) {
                      Throwable t = new RuntimeException("DeploymentService must exist");
                      handleZookeeperStepFailure(t, "No DeploymentService found", true);
                      return;
                    }

                    DeploymentService.State deploymentService = deploymentServices.get(0);

                    if (deploymentService.zookeeperIdToIpMap == null) {
                      Throwable t = new RuntimeException("ZookeeperIdToIpMap cannot be null");
                      handleZookeeperStepFailure(t, "ZookeeperIdToIpMap is null moving on", true);
                      return;
                    }

                    // We are assuming there is just vm associated with this host
                    String hostAddress = vmServiceStates.get(0).ipAddress;
                    Integer zkIndex = null;
                    for (Map.Entry<Integer, String> zkNode : deploymentService.zookeeperIdToIpMap.entrySet()) {
                      if (zkNode.getValue().equals(hostAddress)) {
                        zkIndex = zkNode.getKey();
                        break;
                      }
                    }

                    if (zkIndex == null) {
                      Throwable t = new RuntimeException("Could not find this host in ZookeeperIdToIpMap");
                      handleZookeeperStepFailure(t, "ZookeeperIdToIpMap does not have this host " + hostAddress
                          , true);
                      return;
                    }

                    DeploymentService.HostListChangeRequest hostListChangeRequest =
                        new DeploymentService.HostListChangeRequest();
                    hostListChangeRequest.kind = DeploymentService.HostListChangeRequest.Kind.UPDATE_ZOOKEEPER_INFO;
                    hostListChangeRequest.zookeeperIpToRemove = hostAddress;

                    FutureCallback callback = new FutureCallback() {
                      @Override
                      public void onSuccess(@Nullable Object result) {
                        sendRequest(
                            HostUtils.getCloudStoreHelper(DeprovisionHostWorkflowService.this)
                                .createPatch(deploymentService.documentSelfLink)
                                .setBody(hostListChangeRequest)
                                .setCompletion(
                                    (operation, throwable) -> {
                                      if (null != throwable) {
                                        handleZookeeperStepFailure(throwable, "Error happened while updating " +
                                            "deploymentService zookeeper map", ignoreError);
                                        return;
                                      }

                                      State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage
                                          .DELETE_AGENT, null);
                                      patchState.vmServiceStates = vmServiceStates;
                                      TaskUtils.sendSelfPatch(DeprovisionHostWorkflowService.this, patchState);
                                    }
                                )
                        );
                      }

                      @Override
                      public void onFailure(Throwable t) {
                        failTask(t);
                      }
                    };

                    // Update zookeeper config by calling reconfigure
                    try {
                      ZookeeperClient zookeeperClient
                          = ((ZookeeperClientFactoryProvider) getHost()).getZookeeperServerSetFactoryBuilder()
                          .create();

                      zookeeperClient.removeServer(HostUtils.getDeployerContext(this).getZookeeperQuorum(),
                          zkIndex, callback);
                    } catch (Throwable t) {
                      handleZookeeperStepFailure(t, "Error while reconfiguring zookeeper", ignoreError);
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void deprovisionNetwork(State currentState, HostService.State hostState, boolean ignoreError) {

    if (null == currentState.deploymentServiceLink) {
      QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
      querySpecification.query = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(DeploymentService.State.class));
      QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

      HostUtils.getCloudStoreHelper(this)
          .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
          .setBody(queryTask)
          .setCompletion((op, ex) -> {
            if (null != ex) {
              failTask(ex);
              return;
            }

            NodeGroupBroadcastResponse queryResponse = op.getBody(NodeGroupBroadcastResponse.class);
            Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
            if (documentLinks.isEmpty()) {
              ServiceUtils.logInfo(this, "Skip deprovisioning network because no deployment was found");
              patchHostStateAndSendStageProgress(currentState, HostState.NOT_PROVISIONED,
                  TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_ENTITIES);
              return;
            }

            getDeploymentState(currentState, hostState, documentLinks.iterator().next(), ignoreError);
          })
          .sendWith(this);
    } else {
      getDeploymentState(currentState, hostState, currentState.deploymentServiceLink, ignoreError);
    }
  }

  private void getDeploymentState(State currentState,
                                  HostService.State hostState,
                                  String deploymentServiceLink,
                                  boolean ignoreError) {
    HostUtils.getCloudStoreHelper(this)
        .createGet(deploymentServiceLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failTask(ex);
            return;
          }

          deleteTransportNode(currentState, hostState, op.getBody(DeploymentService.State.class), ignoreError);
        })
        .sendWith(this);
  }

  private void deleteTransportNode(State currentState,
                                   HostService.State hostState,
                                   DeploymentService.State deploymentState,
                                   boolean ignoreError) {
    if (hostState.nsxTransportNodeId == null) {
      ServiceUtils.logInfo(this, "Skip deleting transport node");
      patchHostStateAndSendStageProgress(currentState, HostState.NOT_PROVISIONED,
          TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_ENTITIES);
      return;
    }

    try {
      NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
          deploymentState.networkManagerAddress,
          deploymentState.networkManagerUsername,
          deploymentState.networkManagerPassword);

      nsxClient.getFabricApi().deleteTransportNode(hostState.nsxTransportNodeId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
              unregisterFabricNode(currentState, hostState, deploymentState, ignoreError);
            }

            @Override
            public void onFailure(Throwable throwable) {
              if (ignoreError) {
                ServiceUtils.logSevere(DeprovisionHostWorkflowService.this,
                    "Ignoring error while deleting transport node: ", throwable);
                unregisterFabricNode(currentState, hostState, deploymentState, ignoreError);
              } else {
                failTask(throwable);
              }
            }
          }
      );
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void unregisterFabricNode(State currentState,
                                    HostService.State hostState,
                                    DeploymentService.State deploymentState,
                                    boolean ignoreError) {
    if (hostState.nsxFabricNodeId == null) {
      ServiceUtils.logInfo(this, "Skip unregistering fabric node");
      patchHostStateAndSendStageProgress(currentState, HostState.NOT_PROVISIONED,
          TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_ENTITIES);
      return;
    }

    try {
      NsxClient nsxClient = HostUtils.getNsxClientFactory(this).create(
          deploymentState.networkManagerAddress,
          deploymentState.networkManagerUsername,
          deploymentState.networkManagerPassword);

      nsxClient.getFabricApi().unregisterFabricNode(hostState.nsxFabricNodeId,
          new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
              patchHostStateAndSendStageProgress(currentState, HostState.NOT_PROVISIONED,
                  TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_ENTITIES);
            }

            @Override
            public void onFailure(Throwable throwable) {
              if (ignoreError) {
                ServiceUtils.logSevere(DeprovisionHostWorkflowService.this,
                    "Ignoring error while unregistering fabric node: ", throwable);
                patchHostStateAndSendStageProgress(currentState, HostState.NOT_PROVISIONED,
                    TaskState.TaskStage.STARTED, TaskState.SubStage.DELETE_ENTITIES);
              } else {
                failTask(throwable);
              }
            }
          }
      );
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteEntities(State currentState, boolean ignoreError) {
    // TODO(giskender): Delete flavors
    final AtomicInteger latch = new AtomicInteger(currentState.vmServiceStates.size());

    final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        if (latch.decrementAndGet() == 0) {
          //All api-fe vms are deleted
          deleteDeployerDCPEntities(currentState, ignoreError);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (!ignoreError) {
          failTask(t);
        } else {
          ServiceUtils.logSevere(DeprovisionHostWorkflowService.this, "Ignoring error while finish Deprovision ", t);
        }
      }
    };

    // Stop and delete vms from API-FE
    ApiClient client = HostUtils.getApiClient(this);
    final FutureCallback<Task> vmCallback =
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            MiscUtils.waitForTaskToFinish(DeprovisionHostWorkflowService.this, result,
                currentState.taskPollDelay, finishedCallback);
          }

          @Override
          public void onFailure(Throwable t) {
            if (!ignoreError) {
              failTask(t);
            } else {
              ServiceUtils.logSevere(DeprovisionHostWorkflowService.this, "Ignoring error while Deprovision ", t);
              finishedCallback.onSuccess(null);
            }
          }
        };

    for (final VmService.State vm : currentState.vmServiceStates) {
      MiscUtils.stopAndDeleteVm(DeprovisionHostWorkflowService.this, client, vm.vmId, currentState.taskPollDelay,
          vmCallback);
    }
  }

  private void deleteDeployerDCPEntities(State currentState, boolean ignoreError) {
    final AtomicInteger latch = new AtomicInteger(currentState.vmServiceStates.size());
    final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        if (latch.decrementAndGet() == 0) {
          //All deployer vms are deleted
          sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (!ignoreError) {
          failTask(t);
        } else {
          ServiceUtils.logSevere(DeprovisionHostWorkflowService.this, "Ignoring error while finish Deprovision ", t);
          latch.decrementAndGet();
        }
      }
    };

    for (final VmService.State vm : currentState.vmServiceStates) {
      QueryTask containerQueryTask = QueryTask.Builder.createDirectTask()
          .setQuery(QueryTask.Query.Builder.create()
              .addKindFieldClause(ContainerService.State.class)
              .addFieldClause(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK, vm.documentSelfLink)
              .build())
          .build();

      sendRequest(Operation
          .createPost(UriUtils.buildBroadcastRequestUri(
              UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
              ServiceUriPaths.DEFAULT_NODE_SELECTOR))
          .setBody(containerQueryTask.setDirect(true))
          .setCompletion(
              (completedOp, failure) -> {
                if (null != failure) {
                  if (!ignoreError) {
                    failTask(failure);
                  } else {
                    ServiceUtils.logSevere(DeprovisionHostWorkflowService.this, "Ignoring error while querying " +
                        "containers ", failure);
                    finishedCallback.onSuccess(null);
                  }
                  return;
                }

                try {
                  Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                  QueryTaskUtils.logQueryResults(DeprovisionHostWorkflowService.this, documentLinks);
                  List<ContainerService.State> containerServices =
                      QueryTaskUtils.getBroadcastQueryDocuments(ContainerService.State.class, completedOp);
                  if (containerServices == null || containerServices.size() == 0) {
                    // Delete Vm
                    deleteDeployerDCPVmEntity(vm.documentSelfLink, ignoreError, finishedCallback);
                  } else {
                    OperationJoin
                        .create(documentLinks.stream()
                            .map(documentLink ->
                                Operation.createDelete(this, documentLink).setBody(new ServiceDocument())))
                        .setCompletion(
                            (ops, failures) -> {
                              if (null != failures && failures.size() > 0) {
                                failTask(failures.get(0));
                              }
                              deleteDeployerDCPVmEntity(vm.documentSelfLink, ignoreError,
                                  finishedCallback);
                            }
                        )
                        .sendWith(this);
                  }
                } catch (Throwable t) {
                  failTask(t);
                }
              }));
    }
  }

  private void deleteDeployerDCPVmEntity(String vmLink, boolean ignoreError, FutureCallback<Task> callback) {
    Operation delete = Operation.createDelete(this, vmLink)
        .setCompletion((o, t) -> {
          if (t != null) {
            if (!ignoreError) {
              failTask(t);
              return;
            } else {
              ServiceUtils.logSevere(DeprovisionHostWorkflowService.this, "Ignoring error while deleting vm ", t);
            }
          }

          callback.onSuccess(null);
        });
    sendRequest(delete);
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  private void patchHostStateAndSendStageProgress(State currentState,
                                                  HostState hostState,
                                                  TaskState.TaskStage stage,
                                                  TaskState.SubStage subStage) {
    HostService.State hostService = new HostService.State();
    hostService.state = hostState;

    HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.hostServiceLink)
        .setBody(hostService)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                sendStageProgressPatch(stage, subStage);
              }
            }
        )
        .sendWith(this);
  }

  @VisibleForTesting
  protected static State buildPatch(
      TaskState.TaskStage patchStage,
      @Nullable TaskState.SubStage patchSubStage,
      @Nullable Throwable t) {

    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = patchStage;
    patchState.taskState.subStage = patchSubStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
