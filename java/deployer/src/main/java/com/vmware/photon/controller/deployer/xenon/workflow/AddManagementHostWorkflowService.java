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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateHostResourceTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateHostResourceTaskService;
import com.vmware.photon.controller.deployer.xenon.task.BuildRuntimeConfigurationTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.photon.controller.deployer.xenon.util.MiscUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon micro-service which performs the task of
 * adding a new cloud host to an existing deployment.
 */
public class AddManagementHostWorkflowService extends StatefulService {

  public static final String ZOOKEEPER_PORT = "2181";

  /**
   * This class defines the state of a {@link AddManagementHostWorkflowService} task.
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
      CREATE_MANAGEMENT_PLANE_LAYOUT,
      BUILD_RUNTIME_CONFIGURATION,
      SET_QUORUM_ON_DEPLOYMENT_ENTITY,
      PROVISION_MANAGEMENT_HOSTS,
      CREATE_MANAGEMENT_PLANE,
      RECONFIGURE_ZOOKEEPER,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link AddManagementHostWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the states of individual task sub-stages.
     * <p>
     * N.B. This value is not actually immutable, but it should never be set in a patch; instead, it is updated
     * synchronously in the start and patch handlers.
     */
    @Immutable
    public List<TaskState.TaskStage> taskSubStates;

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
     * the state of a Xenon task.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * This value represents the polling interval override value to use for child tasks.
     */
    @DefaultInteger(value = 3000)
    @Immutable
    public Integer childPollInterval;

    /**
     * This value represents the link to the {@link DeploymentService.State} entity.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the link to the host service entity.
     */
    public String hostServiceLink;

    /**
     * This value represents the management vm that is created on the host.
     */
    @WriteOnce
    public String vmServiceLink;

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean isNewDeployment;
  }

  public AddManagementHostWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      checkState(null == startState.taskSubStates);
      startState.taskSubStates = new ArrayList<>(TaskState.SubStage.values().length);
      for (TaskState.SubStage subStage : TaskState.SubStage.values()) {
        startState.taskSubStates.add(subStage.ordinal(), TaskState.TaskStage.CREATED);
      }
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState = new TaskState();
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.CREATE_MANAGEMENT_PLANE_LAYOUT;
      startState.taskSubStates.set(0, TaskState.TaskStage.STARTED);
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
        sendStageProgressPatch(startState.taskState);
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
        getDeployment(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case CREATE_MANAGEMENT_PLANE_LAYOUT:
        case BUILD_RUNTIME_CONFIGURATION:
        case SET_QUORUM_ON_DEPLOYMENT_ENTITY:
        case PROVISION_MANAGEMENT_HOSTS:
        case CREATE_MANAGEMENT_PLANE:
        case RECONFIGURE_ZOOKEEPER:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage);
      }
    }

    checkState(null != currentState.taskSubStates);
    checkState(TaskState.SubStage.values().length == currentState.taskSubStates.size());
    for (TaskState.SubStage subStage : TaskState.SubStage.values()) {
      try {
        TaskState.TaskStage value = currentState.taskSubStates.get(subStage.ordinal());
        checkState(null != value);
        if (null != currentState.taskState.subStage) {
          if (currentState.taskState.subStage.ordinal() > subStage.ordinal()) {
            checkState(TaskState.TaskStage.FINISHED == value);
          } else if (currentState.taskState.subStage.ordinal() == subStage.ordinal()) {
            checkState(TaskState.TaskStage.STARTED == value);
          } else {
            checkState(TaskState.TaskStage.CREATED == value);
          }
        }
        if (null != currentState.taskState.subStage
            && currentState.taskState.subStage.ordinal() >= subStage.ordinal()) {
          checkState(value != TaskState.TaskStage.CREATED);
        }
      } catch (IndexOutOfBoundsException e) {
        throw new IllegalStateException(e);
      }
    }

    if (currentState.isNewDeployment == false && currentState.hostServiceLink == null) {
      throw new IllegalStateException("For existing deployments, hostServiceLink cannot be null");
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
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (null != currentState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(final State currentState, DeploymentService.State deploymentService) throws
      Throwable {
    switch (currentState.taskState.subStage) {
      case CREATE_MANAGEMENT_PLANE_LAYOUT:
        processCreateManagementPlaneLayout(currentState, deploymentService);
        break;
      case BUILD_RUNTIME_CONFIGURATION:
        processBuildRuntimeConfiguration(currentState);
        break;
      case SET_QUORUM_ON_DEPLOYMENT_ENTITY:
        queryZookeeperContainerTemplate(currentState);
        break;
      case PROVISION_MANAGEMENT_HOSTS:
        processProvisionManagementHosts(currentState, deploymentService);
        break;
      case CREATE_MANAGEMENT_PLANE:
        processCreateManagementPlane(currentState, deploymentService);
        break;
      case RECONFIGURE_ZOOKEEPER:
        reconfigureZookeeper(currentState, deploymentService);
        break;
    }
  }

  private void reconfigureZookeeper(State currentState, DeploymentService.State deploymentService) {
    if (currentState.hostServiceLink == null) {
      TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, buildPatch(
          TaskState.TaskStage.FINISHED, null, null));
    } else {

      FutureCallback callback = new FutureCallback() {
        @Override
        public void onSuccess(@Nullable Object result) {
          TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, buildPatch(
              TaskState.TaskStage.FINISHED, null, null));
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      };

      Operation getOperation = Operation
          .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
              .setCompletion(
              (operation, throwable) -> {
                if (null != throwable) {
                  failTask(throwable);
                  return;
                }

                VmService.State managementVmState = operation.getBody(VmService.State.class);
                String managementVmAddress = managementVmState.ipAddress.trim();
                // Find the host
                if (!deploymentService.zookeeperIdToIpMap.containsValue(managementVmAddress)) {
                  // Really should NEVER EVER happen. But just a sanity check
                  Throwable t = new RuntimeException("Zookeeper replica list doesn't contain host: "
                      + managementVmAddress);
                  failTask(t);
                  return;
                }

                Integer myId = null;
                for (Map.Entry<Integer, String> entry : deploymentService.zookeeperIdToIpMap.entrySet()) {
                  if (entry.getValue().equals(managementVmAddress)) {
                    myId = entry.getKey();
                    break;
                  }
                }

                try {
                  DeployerServiceGroup deployerServiceGroup =
                      (DeployerServiceGroup) ((PhotonControllerXenonHost) getHost()).getDeployer();
                  ZookeeperClient zookeeperClient
                      = deployerServiceGroup.getZookeeperServerSetFactoryBuilder().create();

                  zookeeperClient.addServer(HostUtils.getDeployerContext(this).getZookeeperQuorum(),
                      managementVmAddress, ZOOKEEPER_PORT, myId, callback);
                } catch (Throwable t) {
                  failTask(t);
                }
              }
          );
      sendRequest(getOperation);
    }
  }

  private void processCreateManagementPlaneLayout(State currentState, DeploymentService.State deploymentService)
      throws Throwable {
    processAllocateComponents(currentState, deploymentService);
  }

  private void processAllocateComponents(final State currentState, DeploymentService.State deploymentService) throws
      Throwable {

    ServiceUtils.logInfo(this, "Generating manifest");
    final Service service = this;

    FutureCallback<CreateManagementPlaneLayoutWorkflowService.State> callback
        = new FutureCallback<CreateManagementPlaneLayoutWorkflowService.State>() {
      @Override
      public void onSuccess(@Nullable CreateManagementPlaneLayoutWorkflowService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            TaskUtils.sendSelfPatch(service, buildPatch(
                TaskState.TaskStage.STARTED,
                TaskState.SubStage.BUILD_RUNTIME_CONFIGURATION,
                null));
            break;
          case FAILED:
            State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
            patchState.taskState.failure = result.taskState.failure;
            TaskUtils.sendSelfPatch(service, patchState);
            break;
          case CANCELLED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    CreateManagementPlaneLayoutWorkflowService.State startState =
        createAllocateComplenentsWorkflowState(currentState, deploymentService);

    TaskUtils.startTaskAsync(
        this,
        CreateManagementPlaneLayoutWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateManagementPlaneLayoutWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private CreateManagementPlaneLayoutWorkflowService.State createAllocateComplenentsWorkflowState(
      State currentState,
      DeploymentService.State deploymentService) {
    CreateManagementPlaneLayoutWorkflowService.State state = new CreateManagementPlaneLayoutWorkflowService.State();
    state.taskPollDelay = currentState.taskPollDelay;
    state.isLoadbalancerEnabled = deploymentService.loadBalancerEnabled;
    state.isAuthEnabled = deploymentService.oAuthEnabled;
    state.isNewDeployment = currentState.isNewDeployment;

    if (currentState.isNewDeployment) {
      state.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());
    } else {
      state.hostQuerySpecification = MiscUtils.generateHostQuerySpecification(currentState.hostServiceLink, null);
    }
    return state;
  }

  private void processBuildRuntimeConfiguration(State currentState) {

    BuildRuntimeConfigurationTaskService.State startState = new BuildRuntimeConfigurationTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false, buildPatch(TaskState.TaskStage.STARTED,
        TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY, null));
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = currentState.hostServiceLink;

    sendRequest(Operation
        .createPost(this, BuildContainersConfigurationWorkflowFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  private void processProvisionManagementHosts(State currentState, DeploymentService.State deploymentService)
      throws Throwable {
    bulkProvisionManagementHosts(currentState, deploymentService);
  }

  private void queryZookeeperContainerTemplate(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(ContainersConfig.ContainerType.Zookeeper.name());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((operation, throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
            QueryTaskUtils.logQueryResults(AddManagementHostWorkflowService.this, documentLinks);
            checkState(1 == documentLinks.size());
            queryZookeeperContainers(currentState, documentLinks.iterator().next());
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryZookeeperContainers(final State currentState, String containerTemplateServiceLink) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query containerTemplateServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
        .setTermMatchValue(containerTemplateServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerTemplateServiceLinkClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

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

            try {
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(AddManagementHostWorkflowService.this, documentLinks);
              checkState(documentLinks.size() > 0);
              getZookeeperContainerEntities(currentState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getZookeeperContainerEntities(final State currentState, Collection<String> documentLinks) {

    if (documentLinks.isEmpty()) {
      throw new XenonRuntimeException("Document links set is empty");
    }

    OperationJoin
        .create(documentLinks.stream().map(documentLink -> Operation.createGet(this, documentLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Set<String> vmServiceLinks = ops.values().stream()
                .map(operation -> operation.getBody(ContainerService.State.class).vmServiceLink)
                .collect(Collectors.toSet());
            getZookeeperVmEntities(currentState, vmServiceLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getZookeeperVmEntities(final State currentState, Set<String> vmServiceLinks) {

    if (vmServiceLinks.isEmpty()) {
      throw new XenonRuntimeException("VM service links set is empty");
    }

    OperationJoin
        .create(vmServiceLinks.stream().map(vmServiceLink -> Operation.createGet(this, vmServiceLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            String zookeeperQuorum = MiscUtils.generateReplicaList(
                ops.values().stream().map(operation -> operation.getBody(VmService.State.class).ipAddress)
                    .collect(Collectors.toList()),
                ZOOKEEPER_PORT);

            patchDeploymentService(currentState, zookeeperQuorum);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void patchDeploymentService(State currentState, String zookeeperQuorum) {
    DeploymentService.State deploymentService = new DeploymentService.State();
    deploymentService.zookeeperQuorum = zookeeperQuorum;

    HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
        .setBody(deploymentService)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED,
                    TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS, null));
              }
            }
        )
        .sendWith(this);
  }

  private void bulkProvisionManagementHosts(final State currentState, DeploymentService.State deploymentService) throws
      Throwable {

    ServiceUtils.logInfo(this, "Bulk provisioning management hosts");

    final Service service = this;

    FutureCallback<BulkProvisionHostsWorkflowService.State> callback =
        new FutureCallback<BulkProvisionHostsWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable BulkProvisionHostsWorkflowService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                queryManagementHosts(currentState);
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(service, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    BulkProvisionHostsWorkflowService.State startState = new BulkProvisionHostsWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.usageTag = UsageTag.MGMT.name();
    if (currentState.hostServiceLink != null) {
      startState.querySpecification = MiscUtils.generateHostQuerySpecification(currentState.hostServiceLink, null);
    }

    TaskUtils.startTaskAsync(
        this,
        BulkProvisionHostsWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BulkProvisionHostsWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void queryManagementHosts(final State currentState) {
    QueryTask.QuerySpecification querySpecification;

    if (currentState.hostServiceLink != null) {
      querySpecification = MiscUtils.generateHostQuerySpecification(currentState.hostServiceLink, null);
    } else {
      querySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.MGMT.name());
    }

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(querySpecification).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(this, documentLinks);
                    checkState(documentLinks.size() >= 1);
                    allocateHostResource(currentState, documentLinks);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void allocateHostResource(final State currentState, Collection<String> documentLinks) {
    ServiceUtils.logInfo(this, "Allocating host resource to containers and vms");
    final AtomicInteger pendingChildren = new AtomicInteger(documentLinks.size());
    final Service service = this;

    FutureCallback<AllocateHostResourceTaskService.State> callback =
        new FutureCallback<AllocateHostResourceTaskService.State>() {
          @Override
          public void onSuccess(@Nullable AllocateHostResourceTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                if (0 == pendingChildren.decrementAndGet()) {
                  TaskUtils.sendSelfPatch(service, buildPatch(
                      TaskState.TaskStage.STARTED,
                      TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
                      null));
                }
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(service, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };


    for (String hostServiceLink : documentLinks) {
      AllocateHostResourceTaskService.State startState = new AllocateHostResourceTaskService.State();
      startState.hostServiceLink = hostServiceLink;

      TaskUtils.startTaskAsync(
          this,
          AllocateHostResourceTaskFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          AllocateHostResourceTaskService.State.class,
          currentState.taskPollDelay,
          callback);
    }
  }

  private void processCreateManagementPlane(State currentState, DeploymentService.State deploymentState) {

    if (currentState.hostServiceLink == null) {

      //
      // If this operation is being performed as part of a larger deployment operation, then
      // management plane VMs will be created elsewhere (?). Skip to the next stage.
      //

      TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.RECONFIGURE_ZOOKEEPER,
          null));

    } else if (currentState.vmServiceLink == null) {

      //
      // Create the management plane VM (singular).
      //

      createManagementPlaneVms(currentState, deploymentState);

    } else {

      //
      // Create the containers which were assigned to the new management plane VM.
      //

      processCreateServiceContainers(currentState, deploymentState);
    }
  }

  private void createManagementPlaneVms(State currentState, DeploymentService.State deploymentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
            .addFieldClause(VmService.State.FIELD_NAME_HOST_SERVICE_LINK, currentState.hostServiceLink)
            .build())
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
                List<String> documentLinks = o.getBody(QueryTask.class).results.documentLinks;
                checkState(documentLinks.size() == 1);
                setImageAndFlavorLinks(currentState, deploymentState, documentLinks.get(0));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void setImageAndFlavorLinks(State currentState,
                                      DeploymentService.State deploymentState,
                                      String vmServiceLink) {

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.imageId = deploymentState.imageId;
    vmPatchState.projectId = deploymentState.projectId;

    sendRequest(Operation
        .createPatch(this, vmServiceLink)
        .setBody(vmPatchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                createManagementVm(currentState, deploymentState, vmServiceLink);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createManagementVm(State currentState,
                                  DeploymentService.State deploymentState,
                                  String vmServiceLink) {

    State selfPatchBody = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.CREATE_MANAGEMENT_PLANE, null);
    selfPatchBody.vmServiceLink = vmServiceLink;

    CreateManagementVmTaskService.State startState = new CreateManagementVmTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false, selfPatchBody);
    startState.vmServiceLink = vmServiceLink;
    startState.ntpEndpoint = deploymentState.ntpEndpoint;
    startState.taskPollDelay = currentState.taskPollDelay;

    sendRequest(Operation
        .createPost(this, CreateManagementVmTaskFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  private void processCreateServiceContainers(State currentState, DeploymentService.State deploymentState) {

    CreateContainersWorkflowService.State startState = new CreateContainersWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.isAuthEnabled = deploymentState.oAuthEnabled;
    startState.isNewDeployment = currentState.isNewDeployment;
    startState.vmServiceLink = currentState.vmServiceLink;
    startState.taskPollDelay = currentState.taskPollDelay;

    TaskUtils.startTaskAsync(
        this,
        CreateContainersWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateContainersWorkflowService.State.class,
        currentState.taskPollDelay,
        new FutureCallback<CreateContainersWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainersWorkflowService.State state) {
            switch (state.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this,
                    buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.RECONFIGURE_ZOOKEEPER, null));
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = state.taskState.failure;
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void getDeployment(final State currentState) {
    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (operation, throwable) -> {
                  if (null != throwable) {
                    failTask(throwable);
                    return;
                  }

                  DeploymentService.State deploymentState = operation.getBody(DeploymentService.State.class);
                  try {
                    processStartedState(currentState, deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            )
    );
  }

  private State applyPatch(State currentState, State patchState) {
    if (patchState.taskState.stage != currentState.taskState.stage
        || patchState.taskState.subStage != currentState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving from %s:%s to stage %s:%s",
          currentState.taskState.stage, currentState.taskState.subStage,
          patchState.taskState.stage, patchState.taskState.subStage);

      switch (patchState.taskState.stage) {
        case STARTED:
          currentState.taskSubStates.set(patchState.taskState.subStage.ordinal(), TaskState.TaskStage.STARTED);
          // fall through
        case FINISHED:
          currentState.taskSubStates.set(currentState.taskState.subStage.ordinal(), TaskState.TaskStage.FINISHED);
          break;
        case FAILED:
          currentState.taskSubStates.set(currentState.taskState.subStage.ordinal(), TaskState.TaskStage.FAILED);
          break;
        case CANCELLED:
          currentState.taskSubStates.set(currentState.taskState.subStage.ordinal(), TaskState.TaskStage.CANCELLED);
          break;
      }
    }

    PatchUtils.patchState(currentState, patchState);
    return currentState;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage, null));
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

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, exs.values().iterator().next()));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param patchStage
   * @param patchSubStage
   * @param t
   * @return
   */
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
