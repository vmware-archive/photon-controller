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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.photon.controller.api.DeploymentState;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.xenon.OperationJoinLatch;
import com.vmware.photon.controller.common.xenon.OperationLatch;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.DeploymentWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.FinalizeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.FinalizeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.InitializeDeploymentMigrationWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.InitializeDeploymentMigrationWorkflowService;
import com.vmware.photon.controller.deployer.dcp.workflow.RemoveDeploymentWorkflowFactoryService;
import com.vmware.photon.controller.deployer.dcp.workflow.RemoveDeploymentWorkflowService;
import com.vmware.photon.controller.deployer.gen.DeployRequest;
import com.vmware.photon.controller.deployer.gen.DeployStageStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatus;
import com.vmware.photon.controller.deployer.gen.DeployStatusCode;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.FinalizeMigrateDeploymentStatusCode;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.InitializeMigrateDeploymentStatusCode;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentRequest;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatus;
import com.vmware.photon.controller.deployer.gen.RemoveDeploymentStatusCode;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements function to kick off a deployment workflow request.
 */
public class DeploymentWorkFlowServiceClient {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentWorkFlowServiceClient.class);
  private static final String REFERRER_PATH = "/deployer-thrift-endpoint";

  private DeployerConfig deployerConfig;
  private DeployerXenonServiceHost dcpHost;
  private long dcpOperationTimeoutMicros;

  public DeploymentWorkFlowServiceClient(DeployerConfig config, DeployerXenonServiceHost dcpHost) {
    this.deployerConfig = config;
    this.dcpHost = dcpHost;
    this.dcpOperationTimeoutMicros = OperationLatch.DEFAULT_OPERATION_TIMEOUT_MICROS;
  }

  /**
   * This method creates a DeploymentWorkflowService entity.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  public String create(DeployRequest request) throws Throwable {

    DeploymentWorkflowService.State state = new DeploymentWorkflowService.State();
    state.deploymentServiceLink = DeploymentServiceFactory.SELF_LINK + "/" + request.getDeployment().getId();
    state.managementVmImageFile = deployerConfig.getManagementImageFile();
    state.desiredState = parseDesiredState(request.getDesired_state());

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, DeploymentWorkflowFactoryService.SELF_LINK, null))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId())
        .setBody(state);

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);
    return operation.getBody(DeploymentWorkflowService.State.class).documentSelfLink;
  }

  public boolean has() throws Throwable {

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(DeploymentWorkflowService.State.class));
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(dcpHost, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setBody(queryTask);

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, queryOperation, REFERRER_PATH);

    List<Operation> opList = new ArrayList<>();
    for (String documentLink : QueryTaskUtils.getBroadcastQueryDocumentLinks(operation)) {
      Operation getOperation = Operation
          .createGet(UriUtils.buildUri(dcpHost, documentLink))
          .forceRemote()
          .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH));

      opList.add(getOperation);
    }

    if (opList.size() == 0) {
      return false;
    }

    OperationJoin join = OperationJoin.create(opList);
    OperationJoinLatch joinSync = new OperationJoinLatch(join);
    join.sendWith(dcpHost);
    joinSync.await();

    for (Operation getOperation : join.getOperations()) {
      DeploymentWorkflowService.State state = getOperation.getBody(DeploymentWorkflowService.State.class);
      if (state.taskState.stage.ordinal() <= TaskState.TaskStage.STARTED.ordinal()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the current deployment status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public DeployStatus getDeployStatus(String path) throws Throwable {

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setExpiration(Utils.getNowMicrosUtc() + dcpOperationTimeoutMicros)
        .setContextId(LoggingUtils.getRequestId());

    DeploymentWorkflowService.State serviceState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
            .getBody(DeploymentWorkflowService.State.class);
    DeployStatus deployStatus = new DeployStatus();
    deployStatus.setCode(getDeployStatusCodeForTaskStage(serviceState.taskState.stage));
    switch (serviceState.taskState.stage) {
      case CREATED:
        logger.warn("Deployment task stage CREATED is unexpected");
        break;
      case FAILED:
        logger.error("Deployment task failed: {}", Utils.toJson(serviceState));
        deployStatus.setError(serviceState.taskState.failure.message);
        break;
      case CANCELLED:
        logger.error("Deployment task was cancelled: {}", Utils.toJson(serviceState));
        deployStatus.setError("Deployment was cancelled");
        break;
    }

    ArrayList<DeployStageStatus> stages = new ArrayList<>(serviceState.taskSubStates.size());
    for (int i = 0; i < serviceState.taskSubStates.size(); i++) {
      DeployStageStatus status = new DeployStageStatus();
      status.setName(DeploymentWorkflowService.TaskState.SubStage.values()[i].name());
      if (null != serviceState.taskSubStates.get(i)) {
        status.setCode(getDeployStatusCodeForTaskStage(serviceState.taskSubStates.get(i)));
      }

      stages.add(i, status);
    }

    deployStatus.setStages(stages);
    return deployStatus;
  }

  @VisibleForTesting
  protected static DeployStatusCode getDeployStatusCodeForTaskStage(TaskState.TaskStage taskStage) {
    switch (taskStage) {
      case CREATED:
        return null;
      case STARTED:
        return DeployStatusCode.IN_PROGRESS;
      case FINISHED:
        return DeployStatusCode.FINISHED;
      case FAILED:
        return DeployStatusCode.FAILED;
      case CANCELLED:
        return DeployStatusCode.CANCELLED;
      default:
        throw new RuntimeException(String.format("Unexpected task stage %s", taskStage));
    }
  }

  /**
   * This method creates a RemoveDeploymentWorkflowService entity.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  public String remove(RemoveDeploymentRequest request) throws Throwable {
    RemoveDeploymentWorkflowService.State state = new RemoveDeploymentWorkflowService.State();
    state.deploymentId = request.getId();

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, RemoveDeploymentWorkflowFactoryService.SELF_LINK, null))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId())
        .setBody(state);

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);
    return operation.getBody(RemoveDeploymentWorkflowService.State.class).documentSelfLink;
  }

  /**
   * Returns the current remove deployment status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public RemoveDeploymentStatus getRemoveDeploymentStatus(String path) throws Throwable {
    RemoveDeploymentStatus removeDeploymentStatus = new RemoveDeploymentStatus();

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    RemoveDeploymentWorkflowService.State serviceState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(RemoveDeploymentWorkflowService.State.class);

    switch (serviceState.taskState.stage) {
      case CANCELLED:
        logger.error("Remove deployment cancelled: {}", Utils.toJson(serviceState));
        removeDeploymentStatus.setCode(RemoveDeploymentStatusCode.CANCELLED);
        removeDeploymentStatus.setError("Remove deployment was cancelled");
        break;

      case FAILED:
        logger.error("Deployment failed: {}", Utils.toJson(serviceState));
        removeDeploymentStatus.setCode(RemoveDeploymentStatusCode.FAILED);
        if (serviceState.taskState != null && serviceState.taskState.failure != null) {
          removeDeploymentStatus.setError(
              String.format("Remove deployment failed due to: %s", serviceState.taskState.failure.message));
        } else {
          removeDeploymentStatus.setError("Remove deployment failed.");
        }
        break;

      case FINISHED:
        removeDeploymentStatus.setCode(RemoveDeploymentStatusCode.FINISHED);
        break;

      case CREATED:
      case STARTED:
        removeDeploymentStatus.setCode(RemoveDeploymentStatusCode.IN_PROGRESS);
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", serviceState.taskState.stage));
    }
    return removeDeploymentStatus;
  }

  /**
   * This method creates a InitializeMigrateDeploymentWorkflowService entity.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  public String initializeMigrateDeployment(InitializeMigrateDeploymentRequest request) throws Throwable {
    InitializeDeploymentMigrationWorkflowService.State state = new InitializeDeploymentMigrationWorkflowService.State();
    state.destinationDeploymentId = request.getId();
    state.sourceLoadBalancerAddress = request.getSource_deployment_address();

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, InitializeDeploymentMigrationWorkflowFactoryService.SELF_LINK, null))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId())
        .setBody(state);

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);
    return operation.getBody(InitializeDeploymentMigrationWorkflowService.State.class).documentSelfLink;
  }

  /**
   * Returns the current initialize migrate deployment status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public InitializeMigrateDeploymentStatus getInitializeMigrateDeploymentStatus(String path) throws Throwable {
    InitializeMigrateDeploymentStatus initializeMigrateDeploymentStatus = new InitializeMigrateDeploymentStatus();

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    InitializeDeploymentMigrationWorkflowService.State serviceState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(InitializeDeploymentMigrationWorkflowService.State.class);

    switch (serviceState.taskState.stage) {
      case CANCELLED:
        logger.error("Initialize migrate deployment cancelled: {}", Utils.toJson(serviceState));
        initializeMigrateDeploymentStatus.setCode(InitializeMigrateDeploymentStatusCode.CANCELLED);
        initializeMigrateDeploymentStatus.setError("Initialize migrate deployment was cancelled");
        break;

      case FAILED:
        logger.error("Deployment failed: {}", Utils.toJson(serviceState));
        initializeMigrateDeploymentStatus.setCode(InitializeMigrateDeploymentStatusCode.FAILED);
        if (serviceState.taskState != null && serviceState.taskState.failure != null) {
          initializeMigrateDeploymentStatus.setError(
              String.format("Initialize migrate deployment failed due to: %s", serviceState.taskState.failure.message));
        } else {
          initializeMigrateDeploymentStatus.setError("Initialize migrate deployment failed.");
        }
        break;

      case FINISHED:
        initializeMigrateDeploymentStatus.setCode(InitializeMigrateDeploymentStatusCode.FINISHED);
        break;

      case CREATED:
      case STARTED:
        initializeMigrateDeploymentStatus.setCode(InitializeMigrateDeploymentStatusCode.IN_PROGRESS);
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", serviceState.taskState.stage));
    }
    return initializeMigrateDeploymentStatus;
  }

  /**
   * This method creates a FinalizeMigrateDeploymentWorkflowService entity.
   *
   * @param request
   * @return
   * @throws Throwable
   */
  public String finalizeMigrateDeployment(FinalizeMigrateDeploymentRequest request) throws Throwable {
    FinalizeDeploymentMigrationWorkflowService.State state = new FinalizeDeploymentMigrationWorkflowService.State();
    state.destinationDeploymentId = request.getId();
    state.sourceLoadBalancerAddress = request.getSource_deployment_address();

    Operation post = Operation
        .createPost(UriUtils.buildUri(dcpHost, FinalizeDeploymentMigrationWorkflowFactoryService.SELF_LINK, null))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId())
        .setBody(state);

    Operation operation = ServiceHostUtils.sendRequestAndWait(dcpHost, post, REFERRER_PATH);
    return operation.getBody(FinalizeDeploymentMigrationWorkflowService.State.class).documentSelfLink;
  }

  /**
   * Returns the current finalize migrate deployment status.
   *
   * @param path
   * @return
   * @throws Throwable
   */
  public FinalizeMigrateDeploymentStatus getFinalizeMigrateDeploymentStatus(String path) throws Throwable {
    FinalizeMigrateDeploymentStatus finalizeMigrateDeploymentStatus = new FinalizeMigrateDeploymentStatus();

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(dcpHost, path))
        .setReferer(UriUtils.buildUri(dcpHost, REFERRER_PATH))
        .setContextId(LoggingUtils.getRequestId());

    FinalizeDeploymentMigrationWorkflowService.State serviceState =
        ServiceHostUtils.sendRequestAndWait(dcpHost, getOperation, REFERRER_PATH)
        .getBody(FinalizeDeploymentMigrationWorkflowService.State.class);

    switch (serviceState.taskState.stage) {
      case CANCELLED:
        logger.error("Initialize migrate deployment cancelled: {}", Utils.toJson(serviceState));
        finalizeMigrateDeploymentStatus.setCode(FinalizeMigrateDeploymentStatusCode.CANCELLED);
        finalizeMigrateDeploymentStatus.setError("Finalize migrate deployment was cancelled");
        break;

      case FAILED:
        logger.error("Deployment failed: {}", Utils.toJson(serviceState));
        finalizeMigrateDeploymentStatus.setCode(FinalizeMigrateDeploymentStatusCode.FAILED);
        if (serviceState.taskState != null && serviceState.taskState.failure != null) {
          finalizeMigrateDeploymentStatus.setError(
              String.format("Finalize migrate deployment failed due to: %s", serviceState.taskState.failure.message));
        } else {
          finalizeMigrateDeploymentStatus.setError("Finalize migrate deployment failed.");
        }
        break;

      case FINISHED:
        finalizeMigrateDeploymentStatus.setCode(FinalizeMigrateDeploymentStatusCode.FINISHED);
        break;

      case CREATED:
      case STARTED:
        finalizeMigrateDeploymentStatus.setCode(FinalizeMigrateDeploymentStatusCode.IN_PROGRESS);
        break;

      default:
        throw new RuntimeException(String.format("Unexpected stage %s.", serviceState.taskState.stage));
    }
    return finalizeMigrateDeploymentStatus;
  }

  private DeploymentState parseDesiredState(String desiredState) {
    if (desiredState != null) {
      switch (desiredState) {
        case "READY":
          return DeploymentState.READY;
        case "PAUSED":
          return DeploymentState.PAUSED;
        case "BACKGROUND_PAUSED":
          return DeploymentState.BACKGROUND_PAUSED;
        default:
          throw new RuntimeException(String.format("Unexpected desired state for deployment: %s", desiredState));
      }
    }
    return null;
  }
}
