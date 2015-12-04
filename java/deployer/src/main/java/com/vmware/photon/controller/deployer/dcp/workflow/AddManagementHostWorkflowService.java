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

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ProjectServiceFactory;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.PatchUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.DefaultBoolean;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.common.dcp.validation.Positive;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateHostResourceTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.AllocateHostResourceTaskService;
import com.vmware.photon.controller.deployer.dcp.task.CreateFlavorTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.CreateFlavorTaskService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionAgentTaskFactoryService;
import com.vmware.photon.controller.deployer.dcp.task.ProvisionAgentTaskService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClient;
import com.vmware.photon.controller.deployer.deployengine.ZookeeperClientFactoryProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class implements a DCP micro-service which performs the task of
 * adding a new cloud host to an existing deployment.
 */
public class AddManagementHostWorkflowService extends StatefulService {

  public static final String CHAIRMAN_PORT = "13000";
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
      PROVISION_CLOUD_HOSTS,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link AddManagementHostWorkflowService} instance.
   */
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
     * the state of a dcp task.
     */
    @Positive
    public Integer taskPollDelay;

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
     * This value represents the file name of the EsxCloud management VM image.
     */
    @NotNull
    @Immutable
    public String managementVmImageFile;

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean isNewDeployment;
  }

  public AddManagementHostWorkflowService() {
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
        case PROVISION_CLOUD_HOSTS:
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
        processBuildRuntimeConfiguration(currentState, deploymentService);
        break;
      case SET_QUORUM_ON_DEPLOYMENT_ENTITY:
        queryChairmanContainerTemplate(currentState);
        break;
      case PROVISION_MANAGEMENT_HOSTS:
        processProvisionManagementHosts(currentState, deploymentService);
        break;
      case CREATE_MANAGEMENT_PLANE:
        processCreateManagementPlane(currentState, deploymentService);
        break;
      case PROVISION_CLOUD_HOSTS:
        updateCloudHostAgentConfiguration(currentState, deploymentService);
        break;
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

  private void processBuildRuntimeConfiguration(final State currentState, DeploymentService.State deploymentService)
      throws Throwable {
    ServiceUtils.logInfo(this, "Building runtime configuration");
    final Service service = this;

    FutureCallback<BuildContainersConfigurationWorkflowService.State> callback
        = new FutureCallback<BuildContainersConfigurationWorkflowService.State>() {
      @Override
      public void onSuccess(@Nullable BuildContainersConfigurationWorkflowService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            TaskUtils.sendSelfPatch(service, buildPatch(
                TaskState.TaskStage.STARTED,
                TaskState.SubStage.SET_QUORUM_ON_DEPLOYMENT_ENTITY,
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

    BuildContainersConfigurationWorkflowService.State startState = buildConfigurationWorkflowState(currentState);

    TaskUtils.startTaskAsync(
        this,
        BuildContainersConfigurationWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BuildContainersConfigurationWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private BuildContainersConfigurationWorkflowService.State buildConfigurationWorkflowState(State currentState) {
    BuildContainersConfigurationWorkflowService.State startState = new BuildContainersConfigurationWorkflowService
        .State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.taskPollDelay = currentState.taskPollDelay;
    startState.isNewDeployment = currentState.isNewDeployment;
    return startState;
  }

  private void processProvisionManagementHosts(State currentState, DeploymentService.State deploymentService)
      throws Throwable {
    bulkProvisionManagementHosts(currentState, deploymentService);
  }

  private void queryChairmanContainerTemplate(final State currentState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(ContainersConfig.ContainerType.Chairman.name());

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
            Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
            QueryTaskUtils.logQueryResults(AddManagementHostWorkflowService.this, documentLinks);
            checkState(1 == documentLinks.size());
            queryChairmanContainers(currentState, documentLinks.iterator().next());
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryChairmanContainers(final State currentState, String containerTemplateServiceLink) {

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
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(AddManagementHostWorkflowService.this, documentLinks);
              checkState(documentLinks.size() > 0);
              getChairmanContainerEntities(currentState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getChairmanContainerEntities(final State currentState, Collection<String> documentLinks) {

    if (documentLinks.isEmpty()) {
      throw new DcpRuntimeException("Document links set is empty");
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
            getChairmanVmEntities(currentState, vmServiceLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getChairmanVmEntities(final State currentState, Set<String> vmServiceLinks) {

    if (vmServiceLinks.isEmpty()) {
      throw new DcpRuntimeException("VM service links set is empty");
    }

    OperationJoin
        .create(vmServiceLinks.stream().map(vmServiceLink -> Operation.createGet(this, vmServiceLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Set<String> chairmanIpAddresses = ops.values().stream()
                .map(operation -> operation.getBody(VmService.State.class).ipAddress + ":" + CHAIRMAN_PORT)
                .collect(Collectors.toSet());

            String zookeeperQuorum = MiscUtils.generateReplicaList(
                ops.values().stream().map(operation -> operation.getBody(VmService.State.class).ipAddress)
                    .collect(Collectors.toList()),
                ZOOKEEPER_PORT);
            ZookeeperClient zookeeperClient
                = ((ZookeeperClientFactoryProvider) getHost()).getZookeeperServerSetFactoryBuilder().create();

            //zookeeperClient.
            patchDeploymentService(currentState, chairmanIpAddresses, zookeeperQuorum);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void patchDeploymentService(State currentState, Set<String> chairmanIpAddresses, String zookeeperQuorum) {
    DeploymentService.State deploymentService = new DeploymentService.State();
    deploymentService.chairmanServerList = chairmanIpAddresses;
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
    startState.chairmanServerList = deploymentService.chairmanServerList;
    startState.usageTag = UsageTag.MGMT.name();
    startState.taskPollDelay = currentState.taskPollDelay;
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
                    Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(completedOp);
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

  private void processCreateManagementPlane(final State currentState, DeploymentService.State deploymentService)
      throws Throwable {
    if (currentState.hostServiceLink == null) {
      TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, buildPatch(
          TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_CLOUD_HOSTS, null));
    } else {
      createManagementVmsAndContainersOnHost(currentState, deploymentService);
    }
  }

  private void createManagementVmsAndContainersOnHost(final State currentState,
                                                      DeploymentService.State deploymentService) {
    ServiceUtils.logInfo(this, "Creating Management VMs and containers on host");

    // Query the VM entities created for the host
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.Query hostServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(VmService.State.FIELD_NAME_HOST_SERVICE_LINK)
        .setTermMatchValue(currentState.hostServiceLink);
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(hostServiceLinkClause);

    querySpecification.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
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
              Collection<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(AddManagementHostWorkflowService.this, documentLinks);
              createFlavorAndSetLinks(currentState, deploymentService, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }


  private void createFlavorAndSetLinks(State currentState, DeploymentService.State deploymentService, Collection<String>
      documentLinks) {

    final AtomicInteger latch = new AtomicInteger(documentLinks.size());
    final Service service = this;
    final Map<String, Throwable> exceptions = new ConcurrentHashMap<>();
    for (final String documentLink : documentLinks) {
      createFlavorAndSetLinks(currentState, deploymentService, documentLink);

      if (0 == latch.decrementAndGet()) {
        createContainers(currentState, deploymentService);
      }
    }
  }

  private void createFlavorAndSetLinks(State currentState, DeploymentService.State deploymentService, String
      vmServiceLink) {

    FutureCallback<CreateFlavorTaskService.State> setLinksCallback = new
        FutureCallback<CreateFlavorTaskService.State>() {
          @Override
          public void onSuccess(@Nullable CreateFlavorTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                updateVmLinks(currentState, deploymentService, vmServiceLink);
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this,
                    buildPatch(TaskState.TaskStage.CANCELLED, null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    CreateFlavorTaskService.State createFlavorState =
        generateCreateFlavorTaskServiceState(currentState, vmServiceLink);

    TaskUtils.startTaskAsync(
        this,
        CreateFlavorTaskFactoryService.SELF_LINK,
        createFlavorState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateFlavorTaskService.State.class,
        currentState.taskPollDelay,
        setLinksCallback);
  }

  private void updateVmLinks(State currentState, DeploymentService.State deploymentService, String
      vmServiceLink) {
    VmService.State vmPatchState = new VmService.State();
    vmPatchState.imageServiceLink = ImageServiceFactory.SELF_LINK + "/" + deploymentService.imageId;
    vmPatchState.projectServiceLink = ProjectServiceFactory.SELF_LINK + "/" + deploymentService.projectId;

    this.sendRequest(Operation
        .createPatch(this, vmServiceLink)
        .setBody(vmPatchState)
        .setCompletion(new Operation.CompletionHandler() {
          @Override
          public void handle(Operation operation, Throwable throwable) {
            if (null != throwable) {
              failTask(throwable);
              return;
            }

            createManagementVm(currentState, deploymentService, vmServiceLink);
          }
        }));
  }

  private void createManagementVm(State currentState, DeploymentService.State deploymentService, String
      vmServiceLink) {
    FutureCallback<CreateManagementVmWorkflowService.State> futureCallback =
        new FutureCallback<CreateManagementVmWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateManagementVmWorkflowService.State state) {
            if (state.taskState.stage != TaskState.TaskStage.FINISHED) {
              failTask(new RuntimeException("CreateManagementWorkFlow did not finish."));
              return;
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
            return;
          }
        };

    CreateManagementVmWorkflowService.State startState = createVmWorkflowState(currentState, deploymentService,
        vmServiceLink);

    TaskUtils.startTaskAsync(
        this,
        CreateManagementVmWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateManagementVmWorkflowService.State.class,
        currentState.taskPollDelay,
        futureCallback);
  }

  private CreateManagementVmWorkflowService.State createVmWorkflowState(State currentState, DeploymentService
      .State deploymentService, String vmServiceLink) {
    CreateManagementVmWorkflowService.State state = new CreateManagementVmWorkflowService.State();
    state.vmServiceLink = vmServiceLink;
    state.ntpEndpoint = deploymentService.ntpEndpoint;
    return state;
  }

  private CreateFlavorTaskService.State generateCreateFlavorTaskServiceState(State currentState, String vmServiceLink) {
    CreateFlavorTaskService.State state = new CreateFlavorTaskService.State();
    state.taskState = new com.vmware.xenon.common.TaskState();
    state.taskState.stage = TaskState.TaskStage.CREATED;
    state.vmServiceLink = vmServiceLink;
    state.queryTaskInterval = currentState.taskPollDelay;
    return state;
  }

  private void createContainers(State currentState, DeploymentService.State deploymentService) {
    final Service service = this;

    FutureCallback<CreateContainersWorkflowService.State> callback = new
        FutureCallback<CreateContainersWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainersWorkflowService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(
                    TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_CLOUD_HOSTS, null));
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
    CreateContainersWorkflowService.State startState = new CreateContainersWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.isAuthEnabled = deploymentService.oAuthEnabled;
    startState.isNewDeployment = currentState.isNewDeployment;
    startState.taskPollDelay = currentState.taskPollDelay;
    TaskUtils.startTaskAsync(
        this,
        CreateContainersWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        CreateContainersWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
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

  private void updateCloudHostAgentConfiguration(final State currentState, DeploymentService.State deploymentService)
      throws Throwable {

    if (currentState.isNewDeployment) {
      TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, buildPatch(
          TaskState.TaskStage.FINISHED, null, null));
    } else {
      // Get all cloud hosts and call provision on them to introduce new ChairmanList
      checkState(null != deploymentService.chairmanServerList);
      ServiceUtils.logInfo(this, "Provisioning cloud hosts with chairmanList " + deploymentService.chairmanServerList
          .size());

      QueryTask.QuerySpecification querySpecification = MiscUtils.generateHostQuerySpecification(null, UsageTag.CLOUD
          .name());

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
                      NodeGroupBroadcastResponse queryResponse = completedOp.getBody(NodeGroupBroadcastResponse.class);
                      Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryResults(queryResponse);
                      if (documentLinks.isEmpty()) {
                        TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this,
                            buildPatch(TaskState.TaskStage.FINISHED, null, null));
                        return;
                      }
                      provisionCloudAgents(currentState, deploymentService, documentLinks);
                    } catch (Throwable t) {
                      failTask(t);
                    }

                  }
              ));
    }
  }

  private void provisionCloudAgents(State currentState, DeploymentService.State deploymentService,
                                    Set<String> documentLinks) {
    final AtomicInteger pendingChildren = new AtomicInteger(documentLinks.size());

    ProvisionAgentTaskService.State startState = createProvisionAgentTaskState(currentState, deploymentService);
    FutureCallback<ProvisionAgentTaskService.State> futureCallback =
        new FutureCallback<ProvisionAgentTaskService.State>() {
          @Override
          public void onSuccess(@Nullable ProvisionAgentTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                if (0 == pendingChildren.decrementAndGet()) {
                  TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this,
                      buildPatch(TaskState.TaskStage.FINISHED, null, null));
                }
                break;
              case FAILED:
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, null);
                patchState.taskState.failure = result.taskState.failure;
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, patchState);
                break;
              case CANCELLED:
                TaskUtils.sendSelfPatch(AddManagementHostWorkflowService.this, buildPatch(TaskState.TaskStage.CANCELLED,
                    null, null));
                break;
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (String hostServiceLink : documentLinks) {
      startState.hostServiceLink = hostServiceLink;
      TaskUtils.startTaskAsync(this,
          ProvisionAgentTaskFactoryService.SELF_LINK,
          createProvisionAgentTaskState(currentState, deploymentService),
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          ProvisionAgentTaskService.State.class,
          currentState.taskPollDelay,
          futureCallback);
    }
  }

  private ProvisionAgentTaskService.State createProvisionAgentTaskState(State currentState, DeploymentService.State
      deploymentService) {
    ProvisionAgentTaskService.State startState = new ProvisionAgentTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.hostServiceLink = currentState.hostServiceLink;
    startState.chairmanServerList = deploymentService.chairmanServerList;
    return startState;
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
