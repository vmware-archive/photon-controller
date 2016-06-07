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

import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.configuration.LoadBalancerServer;
import com.vmware.photon.controller.deployer.configuration.PeerNode;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class implements a Xenon task service which constructs the runtime configuration state for
 * a containerized service instance.
 */
public class BuildRuntimeConfigurationTaskService extends StatefulService {

  /**
   * N.B. These values are used in data processing when applying mustache templates in
   * {@link CreateManagementVmTaskService}.
   */
  public static final String MUSTACHE_KEY_COMMON_PEER_NODES = "PEER_NODES";
  public static final String MUSTACHE_KEY_CLOUDSTORE_PEER_NODES = "CLOUDSTORE_PEER_NODES";
  public static final String MUSTACHE_KEY_HOUSEKEEPER_PEER_NODES = "HOUSEKEEPER_PEER_NODES";
  public static final String MUSTACHE_KEY_SCHEDULER_PEER_NODES = "SCHEDULER_PEER_NODES";
  public static final String MUSTACHE_KEY_HAPROXY_MGMT_API_HTTP_SERVERS = "MGMT_API_HTTP_SERVERS";
  public static final String MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTP_SERVERS = "MGMT_UI_HTTP_SERVERS";
  public static final String MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTPS_SERVERS = "MGMT_UI_HTTPS_SERVERS";

  private static final String MUSTACHE_KEY_COMMON_CONTAINER_CPU_COUNT = "cpuCount";
  private static final String MUSTACHE_KEY_COMMON_CONTAINER_DISK_GB = "diskGb";
  private static final String MUSTACHE_KEY_COMMON_CONTAINER_MEMORY_MB = "memoryMb";
  private static final String MUSTACHE_KEY_COMMON_ENABLE_AUTH = "ENABLE_AUTH";
  private static final String MUSTACHE_KEY_COMMON_ENABLE_SYSLOG = "ENABLE_SYSLOG";
  private static final String MUSTACHE_KEY_COMMON_LOAD_BALANCER_IP = "APIFE_IP";
  private static final String MUSTACHE_KEY_COMMON_LOAD_BALANCER_PORT = "APIFE_PORT";
  private static final String MUSTACHE_KEY_COMMON_REGISTRATION_ADDRESS = "REGISTRATION_ADDRESS";
  private static final String MUSTACHE_KEY_COMMON_SHARED_SECRET = "SHARED_SECRET";
  private static final String MUSTACHE_KEY_COMMON_SYSLOG_ENDPOINT = "SYSLOG_ENDPOINT";
  private static final String MUSTACHE_KEY_COMMON_ZOOKEEPER_QUORUM = "ZOOKEEPER_QUORUM";
  private static final String MUSTACHE_KEY_HAPROXY_MGMT_API_PORT_SELECTOR = "MGMT_API_PORT_SELECTOR";
  private static final String MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTP_PORT = "MANAGEMENT_UI_HTTP_PORT";
  private static final String MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTPS_PORT = "MANAGEMENT_UI_HTTPS_PORT";
  private static final String MUSTACHE_KEY_LIGHTWAVE_ADDRESS = "LIGHTWAVE_ADDRESS";
  private static final String MUSTACHE_KEY_LIGHTWAVE_DOMAIN = "LIGHTWAVE_DOMAIN";
  private static final String MUSTACHE_KEY_LIGHTWAVE_PASSWORD = "LIGHTWAVE_PASSWORD";
  private static final String MUSTACHE_KEY_MGMT_API_AUTH_SERVER_ADDRESS = "AUTH_SERVER_ADDRESS";
  private static final String MUSTACHE_KEY_MGMT_API_AUTH_SERVER_TENANT = "AUTH_SERVER_TENANT";
  private static final String MUSTACHE_KEY_MGMT_API_AUTH_SERVER_PORT = "AUTH_SERVER_PORT";
  private static final String MUSTACHE_KEY_MGMT_API_DATASTORE = "DATASTORE";
  private static final String MUSTACHE_KEY_MGMT_API_ESX_HOST = "ESX_HOST";
  private static final String MUSTACHE_KEY_MGMT_API_SWAGGER_LOGIN_URL = "SWAGGER_LOGIN_URL";
  private static final String MUSTACHE_KEY_MGMT_API_SWAGGER_LOGOUT_URL = "SWAGGER_LOGOUT_URL";
  private static final String MUSTACHE_KEY_MGMT_API_USE_VIRTUAL_NETWORK = "USE_VIRTUAL_NETWORK";
  private static final String MUSTACHE_KEY_MGMT_UI_LOGIN_URL = "MGMT_UI_LOGIN_URL";
  private static final String MUSTACHE_KEY_MGMT_UI_LOGOUT_URL = "MGMT_UI_LOGOUT_URL";
  private static final String MUSTACHE_KEY_ZOOKEEPER_MY_ID = "ZOOKEEPER_MYID";

  /**
   * This class defines the state of a {@link BuildRuntimeConfigurationTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages for a {@link BuildRuntimeConfigurationTaskService}
     * task.
     */
    public enum SubStage {
      BUILD_COMMON_STATE,
      BUILD_TYPE_SPECIFIC_STATE,
      PATCH_ENTITY_DOCUMENTS,
    }

    /**
     * This value represents the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link BuildRuntimeConfigurationTaskService}
   * task.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
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
     * This value represents the document self-link of the {@link DeploymentService} representing
     * the deployment in whose context the operation is being performed.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the document self-link of the {@link ContainerService} representing
     * the container whose runtime state should be constructed by the current task.
     */
    @NotNull
    @Immutable
    public String containerServiceLink;

    /**
     * This value represents the type of the specified container.
     */
    @WriteOnce
    public ContainersConfig.ContainerType containerType;

    /**
     * This value represents the static IP address of the VM on which the container was or will be
     * created.
     */
    @WriteOnce
    public String vmIpAddress;

    /**
     * This value represents the document self-link of the {@link HostService} document
     * representing the host on which the container host VM was or will be created.
     */
    @WriteOnce
    public String hostServiceLink;

    /**
     * This value specifies whether authentication is enabled for the parent deployment.
     */
    @WriteOnce
    public Boolean oAuthEnabled;

    /**
     * This value represents the runtime configuration state allocated for the new container by the
     * current task.
     */
    public Map<String, String> dynamicParameters;
  }

  public BuildRuntimeConfigurationTaskService() {
    super(State.class);
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

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    try {
      validateState(startState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, startOp, e);
      return;
    }

    startOp.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (startState.taskState.stage == TaskState.TaskStage.CREATED) {
        sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.BUILD_COMMON_STATE);
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
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, e);
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
    validateTaskState(currentState.taskState);
  }

  private void validatePatch(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    validateTaskState(patchState.taskState);
    validateTaskStageProgression(currentState.taskState, patchState.taskState);
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
          case BUILD_COMMON_STATE:
          case BUILD_TYPE_SPECIFIC_STATE:
          case PATCH_ENTITY_DOCUMENTS:
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

  private void processStartedStage(State currentState) {
    switch (currentState.taskState.subStage) {
      case BUILD_COMMON_STATE:
        processBuildCommonStateSubStage(currentState);
        break;
      case BUILD_TYPE_SPECIFIC_STATE:
        processBuildTypeSpecificStateSubStage(currentState);
        break;
      case PATCH_ENTITY_DOCUMENTS:
        processPatchEntityDocumentsSubStage(currentState);
        break;
    }
  }

  //
  // BUILD_COMMON_STATE sub-stage routines
  //

  private void processBuildCommonStateSubStage(State currentState) {

    Operation containerGetOp = Operation.createGet(this, currentState.containerServiceLink);
    Operation deploymentGetOp = HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink);

    OperationJoin
        .create(containerGetOp, deploymentGetOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processBuildCommonStateSubStage(ops.get(containerGetOp.getId()).getBody(ContainerService.State.class),
                    ops.get(deploymentGetOp.getId()).getBody(DeploymentService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processBuildCommonStateSubStage(ContainerService.State containerState,
                                               DeploymentService.State deploymentState) {

    Operation templateGetOp = Operation.createGet(this, containerState.containerTemplateServiceLink);
    Operation vmGetOp = Operation.createGet(this, containerState.vmServiceLink);

    OperationJoin
        .create(templateGetOp, vmGetOp)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                processBuildCommonStateSubStage(containerState, deploymentState,
                    ops.get(templateGetOp.getId()).getBody(ContainerTemplateService.State.class),
                    ops.get(vmGetOp.getId()).getBody(VmService.State.class));
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void processBuildCommonStateSubStage(ContainerService.State containerState,
                                               DeploymentService.State deploymentState,
                                               ContainerTemplateService.State templateState,
                                               VmService.State vmState) {

    Map<String, String> dynamicParameters = containerState.dynamicParameters;
    if (dynamicParameters == null) {
      dynamicParameters = new HashMap<>();
    }

    ContainersConfig.Spec spec = HostUtils.getContainersConfig(this).getContainerSpecs().get(templateState.name);
    dynamicParameters.putAll(spec.getDynamicParameters());
    dynamicParameters.put(MUSTACHE_KEY_COMMON_ENABLE_AUTH, String.valueOf(deploymentState.oAuthEnabled));
    dynamicParameters.put(MUSTACHE_KEY_COMMON_CONTAINER_MEMORY_MB, String.valueOf(spec.getMemoryMb()));
    dynamicParameters.put(MUSTACHE_KEY_COMMON_CONTAINER_CPU_COUNT, String.valueOf(spec.getCpuCount()));
    dynamicParameters.put(MUSTACHE_KEY_COMMON_CONTAINER_DISK_GB, String.valueOf(spec.getDiskGb()));
    dynamicParameters.put(MUSTACHE_KEY_COMMON_SHARED_SECRET, HostUtils.getDeployerContext(this).getSharedSecret());
    dynamicParameters.put(MUSTACHE_KEY_COMMON_REGISTRATION_ADDRESS, vmState.ipAddress);

    if (deploymentState.oAuthEnabled) {
      dynamicParameters.put(MUSTACHE_KEY_COMMON_LOAD_BALANCER_PORT,
          String.valueOf(ServicePortConstants.LOADBALANCER_API_HTTPS_PORT));
    } else {
      dynamicParameters.put(MUSTACHE_KEY_COMMON_LOAD_BALANCER_PORT,
          String.valueOf(ServicePortConstants.LOADBALANCER_API_HTTP_PORT));
    }

    if (deploymentState.syslogEndpoint != null) {
      dynamicParameters.put(MUSTACHE_KEY_COMMON_ENABLE_SYSLOG, "true");
      dynamicParameters.put(MUSTACHE_KEY_COMMON_SYSLOG_ENDPOINT, deploymentState.syslogEndpoint);
    } else {
      dynamicParameters.put(MUSTACHE_KEY_COMMON_ENABLE_SYSLOG, "false");
      dynamicParameters.put(MUSTACHE_KEY_COMMON_SYSLOG_ENDPOINT, "");
    }

    switch (ContainersConfig.ContainerType.valueOf(templateState.name)) {
      case Lightwave:
        dynamicParameters.put(MUSTACHE_KEY_LIGHTWAVE_ADDRESS, vmState.ipAddress);
        dynamicParameters.put(MUSTACHE_KEY_LIGHTWAVE_DOMAIN, deploymentState.oAuthTenantName);
        dynamicParameters.put(MUSTACHE_KEY_LIGHTWAVE_PASSWORD, deploymentState.oAuthPassword);
        break;
      case ManagementApi:
        dynamicParameters.put(MUSTACHE_KEY_MGMT_API_DATASTORE, deploymentState.imageDataStoreNames.iterator().next());
        dynamicParameters.put(MUSTACHE_KEY_MGMT_API_USE_VIRTUAL_NETWORK,
            String.valueOf(deploymentState.virtualNetworkEnabled));
        if (deploymentState.oAuthEnabled) {
          dynamicParameters.put(MUSTACHE_KEY_MGMT_API_AUTH_SERVER_PORT,
              String.valueOf(ServicePortConstants.LIGHTWAVE_PORT));
          dynamicParameters.put(MUSTACHE_KEY_MGMT_API_AUTH_SERVER_TENANT, deploymentState.oAuthTenantName);
          dynamicParameters.put(MUSTACHE_KEY_MGMT_API_SWAGGER_LOGIN_URL, deploymentState.oAuthSwaggerLoginEndpoint);
          dynamicParameters.put(MUSTACHE_KEY_MGMT_API_SWAGGER_LOGOUT_URL, deploymentState.oAuthSwaggerLogoutEndpoint);
        }
        break;
      case ManagementUi:
        if (deploymentState.oAuthEnabled) {
          dynamicParameters.put(MUSTACHE_KEY_MGMT_UI_LOGIN_URL, deploymentState.oAuthMgmtUiLoginEndpoint);
          dynamicParameters.put(MUSTACHE_KEY_MGMT_UI_LOGOUT_URL, deploymentState.oAuthMgmtUiLogoutEndpoint);
        }
        break;
    }

    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.BUILD_TYPE_SPECIFIC_STATE);
    patchState.dynamicParameters = dynamicParameters;
    patchState.containerType = ContainersConfig.ContainerType.valueOf(templateState.name);
    patchState.vmIpAddress = vmState.ipAddress;
    patchState.hostServiceLink = vmState.hostServiceLink;
    patchState.oAuthEnabled = deploymentState.oAuthEnabled;
    sendStageProgressPatch(patchState);
  }

  //
  // BUILD_TYPE_SPECIFIC_STATE sub-stage routines
  //

  private void processBuildTypeSpecificStateSubStage(State currentState) {

    //
    // Most Photon Controller services require knowledge of the Zookeeper cluster in order to
    // discover peers and other services in the management plane.
    //

    switch (currentState.containerType) {
      case Deployer:
      case ManagementApi:
      case PhotonControllerCore:
        if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_COMMON_ZOOKEEPER_QUORUM)) {
          getIpsForContainerType(ContainersConfig.ContainerType.Zookeeper,
              (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_COMMON_ZOOKEEPER_QUORUM,
                  generateReplicaList(vmIpAddresses, String.valueOf(ServicePortConstants.ZOOKEEPER_PORT))));
          return;
        }
    }

    //
    // Xenon-based services require information about the set of peer nodes with which they should
    // form an initial cluster.
    //
    // N.B. Once our Xenon services are moved to their own groups, the special casing of individual
    // container types will no longer be necessary -- each service will be part of a single default
    // group, regardless of its type.
    //

    if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_COMMON_PEER_NODES)) {
      switch (currentState.containerType) {
        case Deployer:
          getIpsForContainerType(currentState.containerType,
              (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_COMMON_PEER_NODES,
                  generatePeerNodeList(vmIpAddresses, String.valueOf(ServicePortConstants.DEPLOYER_PORT))));
          return;
      }
    }

    //
    // Populate peer node information for cloudstore, housekeeper and scheduler services
    //

    if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_CLOUDSTORE_PEER_NODES)) {
      switch (currentState.containerType) {
        case PhotonControllerCore:
          getIpsForContainerType(currentState.containerType,
                  (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_CLOUDSTORE_PEER_NODES,
                          generatePeerNodeList(vmIpAddresses, String.valueOf(ServicePortConstants.CLOUD_STORE_PORT))));
          return;
      }
    }

    if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_HOUSEKEEPER_PEER_NODES)) {
      switch (currentState.containerType) {
        case PhotonControllerCore:
          getIpsForContainerType(currentState.containerType,
                  (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_HOUSEKEEPER_PEER_NODES,
                          generatePeerNodeList(vmIpAddresses,
                                  String.valueOf(ServicePortConstants.HOUSEKEEPER_PORT))));
          return;
      }
    }

    if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_SCHEDULER_PEER_NODES)) {
      switch (currentState.containerType) {
        case PhotonControllerCore:
          getIpsForContainerType(currentState.containerType,
                  (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_SCHEDULER_PEER_NODES,
                          generatePeerNodeList(vmIpAddresses,
                                  String.valueOf(ServicePortConstants.ROOT_SCHEDULER_PORT))));
          return;
      }
    }

    //
    // Some services require service-specific configuration data.
    //

    switch (currentState.containerType) {

      //
      // The deployer and the management UI service (incorrectly) require knowledge of the load
      // balancer IP address in order to communicate with API-FE.
      //

      case Deployer:
      case ManagementUi:
        if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_COMMON_LOAD_BALANCER_IP)) {
          getIpsForContainerType(ContainersConfig.ContainerType.LoadBalancer,
              (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_COMMON_LOAD_BALANCER_IP,
                  vmIpAddresses.iterator().next()));
          return;
        }
        break;

      //
      // The load balancer requires knowledge of the management API IP addresses so that it can
      // load balance traffic across them.
      //

      case LoadBalancer:
        if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_HAPROXY_MGMT_API_HTTP_SERVERS)) {
          getIpsForContainerType(ContainersConfig.ContainerType.ManagementApi,
              (vmIpAddresses) -> patchLoadBalancerParameters(currentState, vmIpAddresses));
          return;
        }
        break;

      //
      // The management API server requires information about the host and data store to use for
      // image upload as well as the address of the Lightwave server, if one is present.
      //

      case ManagementApi:
        if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_MGMT_API_ESX_HOST)) {
          patchEsxHostState(currentState);
          return;
        }

        if (currentState.oAuthEnabled &&
            !currentState.dynamicParameters.containsKey(MUSTACHE_KEY_MGMT_API_AUTH_SERVER_ADDRESS)) {
          getIpsForContainerType(ContainersConfig.ContainerType.Lightwave,
              (vmIpAddresses) -> patchDynamicParameter(currentState, MUSTACHE_KEY_MGMT_API_AUTH_SERVER_ADDRESS,
                  vmIpAddresses.iterator().next()));
          return;
        }
        break;

      //
      // Zookeeper requires special handling of the Zookeeper server information in order to be
      // configured correctly.
      //

      case Zookeeper:
        if (!currentState.dynamicParameters.containsKey(MUSTACHE_KEY_ZOOKEEPER_MY_ID)) {
          patchZookeeperParameters(currentState);
          return;
        }
        break;
    }

    sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.PATCH_ENTITY_DOCUMENTS);
  }

  private void patchDynamicParameter(State currentState, String key, String value) {
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.BUILD_TYPE_SPECIFIC_STATE);
    patchState.dynamicParameters = currentState.dynamicParameters;
    patchState.dynamicParameters.put(key, value);
    TaskUtils.sendSelfPatch(this, patchState);
  }

  private String generateReplicaList(List<String> replicaIps, String port) {
    return replicaIps.stream().sorted().map((ip) -> ip + ":" + port).collect(Collectors.joining(","));
  }

  private String generatePeerNodeList(List<String> peerNodeIps, String port) {

    List<PeerNode> peerNodeList = peerNodeIps.stream()
        .sorted()
        .map((peerNodeIp) -> new PeerNode(peerNodeIp, port))
        .collect(Collectors.toList());

    return Utils.toJson(peerNodeList);
  }

  private void patchLoadBalancerParameters(State currentState, List<String> vmIpAddresses) {
    Collections.sort(vmIpAddresses);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.BUILD_TYPE_SPECIFIC_STATE);
    patchState.dynamicParameters = currentState.dynamicParameters;
    patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_API_HTTP_SERVERS,
        generateServerList(vmIpAddresses, String.valueOf(ServicePortConstants.MANAGEMENT_API_PORT)));
    patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTP_SERVERS,
        generateServerList(vmIpAddresses, String.valueOf(ServicePortConstants.MANAGEMENT_UI_HTTP_PORT)));
    patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTPS_SERVERS,
        generateServerList(vmIpAddresses, String.valueOf(ServicePortConstants.MANAGEMENT_UI_HTTPS_PORT)));
    patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTP_PORT,
        String.valueOf(ServicePortConstants.LOADBALANCER_MGMT_UI_HTTP_PORT));
    patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_UI_HTTPS_PORT,
        String.valueOf(ServicePortConstants.LOADBALANCER_MGMT_UI_HTTPS_PORT));

    /**
     * N.B. Frustratingly, Mustache does not consider the value of this key when modifying the
     * contents of the template file; only whether it is present in the key set or not.
     */
    if (currentState.oAuthEnabled) {
      patchState.dynamicParameters.put(MUSTACHE_KEY_HAPROXY_MGMT_API_PORT_SELECTOR, "true");
    }

    TaskUtils.sendSelfPatch(this, patchState);
  }

  private String generateServerList(List<String> serverAddresses, String serverPort) {

    List<LoadBalancerServer> serverList = IntStream.range(0, serverAddresses.size())
        .mapToObj((i) -> new LoadBalancerServer("server-" + i, serverAddresses.get(i) + ":" + serverPort))
        .collect(Collectors.toList());

    return new Gson().toJson(serverList);
  }

  private void patchEsxHostState(State currentState) {

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createGet(currentState.hostServiceLink)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                patchDynamicParameter(currentState, MUSTACHE_KEY_MGMT_API_ESX_HOST,
                    o.getBody(HostService.State.class).hostAddress);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void patchZookeeperParameters(State currentState) {

    //
    // N.B. Zookeeper server ID mappings are maintained by the patch handler in the deployment
    // service entity, so it is necessary to register the current container with the deployment
    // document before computing the parameters here.
    //
    // This violates the sub-stage separation between generating dynamic state and writing it to
    // entities, but this is unavoidable.
    //

    DeploymentService.HostListChangeRequest hostListChangeRequest = new DeploymentService.HostListChangeRequest();
    hostListChangeRequest.kind = DeploymentService.HostListChangeRequest.Kind.UPDATE_ZOOKEEPER_INFO;
    hostListChangeRequest.zookeeperIpsToAdd = Collections.singletonList(currentState.vmIpAddress);

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
        .setBody(hostListChangeRequest)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {

                //
                // N.B. Because this operation can occur in parallel for multiple Zookeeper service
                // containers, it is impossible to generate the final Zookeeper quorum here. This
                // step is performed as a separate sub-stage in the parent workflow.
                //

                int myId = o.getBody(DeploymentService.State.class).zookeeperIdToIpMap.entrySet().stream()
                    .filter((entry) -> entry.getValue().equals(currentState.vmIpAddress))
                    .mapToInt(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Replica list does not contain " +
                        currentState.vmIpAddress));

                patchDynamicParameter(currentState, MUSTACHE_KEY_ZOOKEEPER_MY_ID, String.valueOf(myId));

              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  @FunctionalInterface
  private interface CompletionHandler {
    void handle(List<String> vmIpAddresses);
  }

  private void getIpsForContainerType(ContainersConfig.ContainerType containerType, CompletionHandler handler) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerTemplateService.State.class)
            .addFieldClause(ContainerTemplateService.State.FIELD_NAME_NAME, containerType.name())
            .build())
        .addOption(QueryTask.QuerySpecification.QueryOption.BROADCAST)
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
                checkState(documentLinks.size() == 1,
                    "Expected 1 document of type " + containerType + ", got " + documentLinks.size());
                getContainersForType(documentLinks.iterator().next(), handler);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void getContainersForType(String templateServiceLink, CompletionHandler handler) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerService.State.class)
            .addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK, templateServiceLink)
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
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                getVmsForContainers(o.getBody(QueryTask.class).results.documents, handler);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void getVmsForContainers(Map<String, Object> containerDocuments, CompletionHandler handler) {

    OperationJoin
        .create(containerDocuments.values().stream()
            .map((containerDocument) -> Utils.fromJson(containerDocument, ContainerService.State.class))
            .map((containerState) -> Operation.createGet(this, containerState.vmServiceLink)))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                getIpsForVms(ops, handler);
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void getIpsForVms(Map<Long, Operation> vmServiceOps, CompletionHandler handler) {

    List<String> vmIpAddresses = vmServiceOps.values().stream()
        .map((vmServiceOp) -> vmServiceOp.getBody(VmService.State.class).ipAddress)
        .collect(Collectors.toList());

    handler.handle(vmIpAddresses);
  }

  //
  // PATCH_ENTITY_DOCUMENTS sub-stage routines
  //

  private void processPatchEntityDocumentsSubStage(State currentState) {

    ContainerService.State containerPatchState = new ContainerService.State();
    containerPatchState.dynamicParameters = currentState.dynamicParameters;
    List<Operation> patchOps = new ArrayList<>(2);
    patchOps.add(Operation.createPatch(this, currentState.containerServiceLink).setBody(containerPatchState));

    switch (currentState.containerType) {
      case Lightwave: {
        DeploymentService.State deploymentPatchState = new DeploymentService.State();
        deploymentPatchState.oAuthServerAddress = currentState.vmIpAddress;
        deploymentPatchState.oAuthServerPort = ServicePortConstants.LIGHTWAVE_PORT;
        patchOps.add(HostUtils.getCloudStoreHelper(this).createPatch(currentState.deploymentServiceLink)
            .setBody(deploymentPatchState));
        break;
      }
      case LoadBalancer: {
        DeploymentService.State deploymentPatchState = new DeploymentService.State();
        deploymentPatchState.loadBalancerAddress = currentState.vmIpAddress;
        patchOps.add(HostUtils.getCloudStoreHelper(this).createPatch(currentState.deploymentServiceLink)
            .setBody(deploymentPatchState));
        break;
      }
    }

    OperationJoin
        .create(patchOps)
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
              } else {
                sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
              }
            })
        .sendWith(this);
  }

  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage));
  }

  private void sendStageProgressPatch(State patchState) {
    ServiceUtils.logInfo(this, "Sending self-patch: {}", patchState);
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
