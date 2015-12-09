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

import com.vmware.photon.controller.api.Deployment;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUriPaths;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.common.dcp.validation.WriteOnce;
import com.vmware.photon.controller.deployer.DeployerModule;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.MigrationStatusUpdateTriggerService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.UploadVibTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a DCP micro-service which performs the task of
 * initializing migration of an existing deployment to a new deployment.
 */
public class InitializeDeploymentMigrationWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link InitializeDeploymentMigrationWorkflowService} task.
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
      PAUSE_DESTINATION_SYSTEM,
      UPLOAD_VIBS,
      CONTIONUS_MIGRATE_DATA,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link InitializeDeploymentMigrationWorkflowService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value allows processing of post and patch operations to be
     * disabled, effectively making all service instances listeners. It is set
     * only in test scenarios.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the link to the source management plane in the form of http://address:port.
     */
    @NotNull
    @Immutable
    public String sourceLoadBalancerAddress;

    /**
     * This value represents the id of the destination deployment.
     */
    @NotNull
    @Immutable
    public String destinationDeploymentId;

    /**
     * This value represents the the DeploymentId on source.
     */
    @WriteOnce
    public String sourceDeploymentId;

    /**
     * This value represents the the DeploymentId on source.
     */
    @WriteOnce
    public String sourceZookeeperQuorum;
  }

  public InitializeDeploymentMigrationWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PAUSE_DESTINATION_SYSTEM;
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
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
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
        processStartedState(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case PAUSE_DESTINATION_SYSTEM:
        pauseDestinationSystem(currentState);
        break;
      case UPLOAD_VIBS:
        uploadVibs(currentState);
        break;
      case CONTIONUS_MIGRATE_DATA:
        migrateDataContionously(currentState);
        break;
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case PAUSE_DESTINATION_SYSTEM:
          break;
        case CONTIONUS_MIGRATE_DATA:
        case UPLOAD_VIBS:
          checkState(null != currentState.sourceDeploymentId);
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage);
      }
    }
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

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (null != currentState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void getDeployment(final State currentState, String endpoint, FutureCallback<ResourceList<Deployment>>
      callback)
      throws IOException {
    ApiClient client = null;
    if (endpoint != null) {
      client = HostUtils.getApiClient(this, endpoint);
    } else {
      client = HostUtils.getApiClient(this);
    }
    client.getDeploymentApi().listAllAsync(callback);
  }

  private void pauseDestinationSystem(State currentState) throws IOException {
    getDeployment(currentState, null, new FutureCallback<ResourceList<Deployment>>() {
      @Override
      public void onSuccess(@Nullable final ResourceList<Deployment> result) {
        // Will move this later
        try {
          checkState(result != null && result.getItems().size() == 1);

          Deployment deployment = result.getItems().get(0);
          querySourceAndPauseDestinationSystem(deployment.getId(), currentState);
        } catch (Throwable throwable) {
          failTask(throwable);
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        failTask(throwable);
      }
    });
  }

  private void querySourceAndPauseDestinationSystem(final String destinationDeploymentId, final State currentState)
      throws Throwable {
    final FutureCallback<ResourceList<Deployment>> querySourceDeploymentCallback = new
        FutureCallback<ResourceList<Deployment>>() {
          @Override
          public void onSuccess(@Nullable final ResourceList<Deployment> result) {
            try {
              checkState(result != null && result.getItems().size() == 1);

              Deployment deployment = result.getItems().get(0);
              String sourceDeploymentId = deployment.getId();
              pauseDestinationSystem(sourceDeploymentId, destinationDeploymentId, currentState);
            } catch (Throwable throwable) {
              failTask(throwable);
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        };

    // Get source deployment first, then pause destination deployment
    getDeployment(currentState, currentState.sourceLoadBalancerAddress, querySourceDeploymentCallback);
  }

  private void pauseDestinationSystem(final String sourceDeploymentId, final String destinationDeploymentId, final
  State currentState) throws Throwable {
    ApiClient destinationClient = HostUtils.getApiClient(this);

    FutureCallback<Task> pauseCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_VIBS, null);
          patchState.sourceDeploymentId = sourceDeploymentId;
          TaskUtils.sendSelfPatch(InitializeDeploymentMigrationWorkflowService.this, patchState);
        } catch (Throwable throwable) {
          failTask(throwable);
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        failTask(throwable);
      }
    };
    destinationClient.getDeploymentApi().pauseSystemAsync(destinationDeploymentId, pauseCallback);
  }

  private void getZookeeperQuorumFromSourceSystem(final State currentState)
      throws Throwable {
    MiscUtils.getZookeeperQuorumFromSourceSystem(this, currentState.sourceLoadBalancerAddress,
        currentState.sourceDeploymentId, currentState.taskPollDelay, new FutureCallback<List<String>>() {
          @Override
          public void onSuccess(@Nullable List<String> result) {
            setZookeeperQuorum(currentState, result);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private void setZookeeperQuorum(State currentState, List<String> ipAddresses) {
    String zookeeperQuorum = MiscUtils.generateReplicaList(ipAddresses, Integer.toString(ServicePortConstants
        .ZOOKEEPER_PORT));

    ServiceUtils.logInfo(InitializeDeploymentMigrationWorkflowService.this, "Set Zookeeper quorum %s", zookeeperQuorum);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_VIBS, null);
    patchState.sourceZookeeperQuorum = zookeeperQuorum;
    TaskUtils.sendSelfPatch(InitializeDeploymentMigrationWorkflowService.this, patchState);
  }

  private URI getRandomSourceCloudStoreURI(State currentState) throws URISyntaxException {
    ZookeeperClient zookeeperClient
        = ((ZookeeperClientFactoryProvider) getHost()).getZookeeperServerSetFactoryBuilder().create();

    Set<InetSocketAddress> sourceCloudStoreServers
        = zookeeperClient.getServers(currentState.sourceZookeeperQuorum, DeployerModule.CLOUDSTORE_SERVICE_NAME);

    return ServiceUtils.createUriFromServerSet(sourceCloudStoreServers, null);
  }

  private QueryTask.QuerySpecification buildHostQuerySpecification() {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    return querySpecification;
  }

  private void getAllHostsFromSourceSystem(final State currentState, Operation.CompletionHandler callback) throws
      URISyntaxException {

    sendRequest(Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getRandomSourceCloudStoreURI(currentState), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask.create(buildHostQuerySpecification()).setDirect(true))
        .setCompletion(callback));
  }

  private void uploadVibs(State currentState) throws Throwable {
    Operation.CompletionHandler getSourceHostsHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          NodeGroupBroadcastResponse queryResponse = operation.getBody(NodeGroupBroadcastResponse.class);
          Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);
          if (documentLinks.size() > 0) {
            Iterator<String> it = documentLinks.iterator();
            final AtomicInteger pendingChildren = new AtomicInteger(documentLinks.size());
            FutureCallback<UploadVibTaskService.State> futureCallback = new FutureCallback<UploadVibTaskService.State>()
            {
              @Override
              public void onSuccess(@Nullable UploadVibTaskService.State result) {
                switch (result.taskState.stage) {
                  case FINISHED: {
                    if (0 == pendingChildren.decrementAndGet()) {
                      sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CONTIONUS_MIGRATE_DATA);
                    }
                    break;
                  }
                  case FAILED: {
                    State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                    patchState.taskState.failure = result.taskState.failure;
                    TaskUtils.sendSelfPatch(InitializeDeploymentMigrationWorkflowService.this, patchState);
                    break;
                  }
                  case CANCELLED:
                    sendStageProgressPatch(TaskState.TaskStage.CANCELLED, null);
                    break;
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            while (it.hasNext()) {
              processUploadVibSubStage(currentState, it.next(), futureCallback);
            }
          } else {
            sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CONTIONUS_MIGRATE_DATA);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    if (currentState.sourceZookeeperQuorum == null) {
      getZookeeperQuorumFromSourceSystem(currentState);
    } else {
      getAllHostsFromSourceSystem(currentState, getSourceHostsHandler);
    }

  }

  private void processUploadVibSubStage(State currentState, String hostServiceLink,
                                        FutureCallback<UploadVibTaskService.State> futureCallback) {
    UploadVibTaskService.State startState = new UploadVibTaskService.State();
    startState.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + currentState.destinationDeploymentId;
    startState.hostServiceLink = hostServiceLink;

    TaskUtils.startTaskAsync(
        this,
        UploadVibTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        UploadVibTaskService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private void migrateDataContionously(State currentState) {
    // Start MigrationStatusUpdateService
    MigrationStatusUpdateTriggerService.State startState = new MigrationStatusUpdateTriggerService.State();
    startState.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + currentState.destinationDeploymentId;
    startState.documentSelfLink = currentState.destinationDeploymentId;

    sendRequest(
        Operation.createPost(UriUtils.buildUri(getHost(), MigrationStatusUpdateTriggerFactoryService.SELF_LINK, null))
            .setBody(startState)
            .setCompletion((o, t) -> {
              if (t != null) {
                failTask(t);
                return;
              }
              sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
            }));
  }

  private State applyPatch(State currentState, State patchState) {
    if (patchState.taskState.stage != currentState.taskState.stage
        || patchState.taskState.subStage != currentState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      currentState.taskState = patchState.taskState;
    }

    if (null != patchState.sourceZookeeperQuorum) {
      currentState.sourceZookeeperQuorum = patchState.sourceZookeeperQuorum;
    }

    if (null != patchState.sourceDeploymentId) {
      currentState.sourceDeploymentId = patchState.sourceDeploymentId;
    }

    return currentState;
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, t));
  }

  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
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
