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

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.OperationJoin;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.StatefulService;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.dcp.services.common.ServiceUriPaths;
import com.vmware.photon.controller.cloudstore.dcp.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.common.dcp.CloudStoreHelper;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.configuration.LoadBalancerServer;
import com.vmware.photon.controller.deployer.configuration.ZookeeperServer;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.Pair;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.gson.Gson;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class implements a DCP micro-service which performs the task of
 * building the runtime configuration for a specific container.
 */
public class BuildRuntimeConfigurationTaskService extends StatefulService {

  public static final String ENV_ZOOKEEPER_MY_ID = "ZOOKEEPER_MYID";
  public static final String ENV_ZOOKEEPER_STANDALONE = "ZOOKEEPER_STANDALONE";
  public static final String ENV_ZOOKEEPER_QUORUM = "ZOOKEEPER_INSTANCES";

  public static final String ENV_LOADBALANCER_SERVERS = "LOAD_BALANCER_SERVERS";
  public static final String ENV_LOADBALANCER_IP = "APIFE_IP";
  public static final String ENV_LOADBALANCER_PORT = "APIFE_PORT";

  public static final String ENV_ZOOKEEPER_QUORUM_URL = "ZOOKEEPER_QUORUM";
  public static final String ENV_DEPLOYER_REGISTRATION_ADDRESS = "DEPLOYER_REGISTRATION_ADDRESS";
  public static final String ENV_API_REGISTRATION_ADDRESS = "MANAGEMENT-API_REGISTRATION_ADDRESS";
  public static final String ENV_CHAIRMAN_REGISTRATION_ADDRESS = "CHAIRMAN_REGISTRATION_ADDRESS";
  public static final String ENV_ROOT_SCHEDULER_REGISTRATION_ADDRESS = "ROOT-SCHEDULER_REGISTRATION_ADDRESS";
  public static final String ENV_HOUSEKEEPER_REGISTRATION_ADDRESS = "HOUSEKEEPER_REGISTRATION_ADDRESS";
  public static final String ENV_CLOUD_STORE_REGISTRATION_ADDRESS = "CLOUD-STORE_REGISTRATION_ADDRESS";
  public static final String ENV_ESX_HOST = "ESX_HOST";
  public static final String ENV_DATASTORE = "DATASTORE";
  public static final String ENV_ENABLE_SYSLOG = "ENABLE_SYSLOG";
  public static final String ENV_SYSLOG_ENDPOINT = "SYSLOG_ENDPOINT";
  public static final String ENV_NTP_ENDPOINT = "NTP_SERVER";

  public static final String ENV_ENABLE_AUTH = "ENABLE_AUTH";
  public static final String ENV_SWAGGER_LOGIN_URL = "SWAGGER_LOGIN_URL";
  public static final String ENV_SWAGGER_LOGOUT_URL = "SWAGGER_LOGOUT_URL";
  public static final String ENV_SHARED_SECRET = "SHARED_SECRET";
  public static final String ENV_AUTH_SERVER_ADDRESS = "AUTH_SERVER_ADDRESS";
  public static final String ENV_AUTH_SERVER_TENANT = "AUTH_SERVER_TENANT";
  public static final String ENV_AUTH_SERVER_PORT = "AUTH_SERVER_PORT";

  public static final String ENV_LIGHTWAVE_DOMAIN = "LIGHTWAVE_DOMAIN";
  public static final String ENV_LIGHTWAVE_ADMIN_USERNAME = "LIGHTWAVE_ADMIN_USERNAME";
  public static final String ENV_LIGHTWAVE_PASSWORD = "LIGHTWAVE_PASSWORD";
  public static final String ENV_LIGHTWAVE_ADDRESS = "LIGHTWAVE_ADDRESS";

  private static final String ZOOKEEPER_PORT = "2181";
  private static final String API_FE_PORT = "9000";

  /**
   * This class defines the document state associated with a single
   * {@link BuildRuntimeConfigurationTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the URL of the ContainerService object.
     */
    @NotNull
    @Immutable
    public String containerServiceLink;

    /**
     * Control flags.
     */
    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;
  }

  public BuildRuntimeConfigurationTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  /**
   * This method is called when a start operation is performed on the current
   * service instance.
   *
   * @param start Supplies a patch operation to be handled.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    start.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method is called when a patch operation is performed on the current
   * service instance.
   *
   * @param patch Supplies a patch operation to be handled.
   */
  @Override
  public void handlePatch(Operation patch) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State startState = getState(patch);
    State patchState = patch.getBody(State.class);
    validatePatchState(startState, patchState);
    State currentState = applyPatch(startState, patchState);
    validateState(currentState);
    patch.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        getContainerService(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies the current state of the service instance.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method validates a patch object against a valid document state
   * object.
   *
   * @param startState Supplies the state of the current service instance.
   * @param patchState Supplies the state object specified in the patch
   *                   operation.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the initial state of the current service
   *                   instance.
   * @param patchState Supplies the patch state associated with a patch
   *                   operation.
   * @return The updated state of the current service instance.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage) {
        ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      }

      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  /**
   * This method retrieves the container service pointed to by the service link.
   *
   * @param currentState
   */
  private void getContainerService(final State currentState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerService.State containerState = operation.getBody(ContainerService.State.class);
          processGetVmState(currentState, containerState);
        } catch (Throwable t) {
          failTask(throwable);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.containerServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method retrieves the VM Service entity document pointed by the
   * container service to get the ip address of the vm.
   *
   * @param currentState
   */
  private void processGetVmState(final State currentState,
                                 final ContainerService.State containerState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          processGetContainerTemplate(currentState, vmState, containerState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerState.vmServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method retrieves the VM Service entity document pointed by the
   * container service to get the ip address of the vm.
   *
   * @param currentState
   */
  private void processGetContainerTemplate(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          ContainerTemplateService.State containerTemplateState = operation.getBody(ContainerTemplateService.State
              .class);
          setCommonEnvironmentVariablesFromDeploymentServiceState(currentState, vmState, containerState,
              containerTemplateState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerState.containerTemplateServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  /**
   * This method gets the deployment service state, and then sets common environmental variables.
   *
   * @param currentState
   */
  private void setCommonEnvironmentVariablesFromDeploymentServiceState(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState,
      final ContainerTemplateService.State containerTemplateState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);

                    // Set syslog endpoint and ntp server
                    if (containerState.dynamicParameters == null) {
                      containerState.dynamicParameters = new HashMap<>();
                    }

                    containerState.dynamicParameters.put(ENV_NTP_ENDPOINT, deploymentState.ntpEndpoint);

                    if (null != deploymentState.syslogEndpoint) {
                      containerState.dynamicParameters.put(ENV_ENABLE_SYSLOG, "true");
                      containerState.dynamicParameters.put(ENV_SYSLOG_ENDPOINT, deploymentState.syslogEndpoint);
                    } else {
                      containerState.dynamicParameters.put(ENV_ENABLE_SYSLOG, "false");
                      containerState.dynamicParameters.put(ENV_SYSLOG_ENDPOINT, "");
                    }

                    // Set load balancer port
                    int loadBalancerPort = deploymentState.oAuthEnabled ? ServicePortConstants.LOADBALANCER_HTTPS_PORT :
                        ServicePortConstants.LOADBALANCER_HTTP_PORT;
                    containerState.dynamicParameters.put(ENV_LOADBALANCER_PORT, String.valueOf(loadBalancerPort));

                    // Load state for particular services
                    loadRuntimeState(currentState, vmState, containerState, containerTemplateState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
   * This method loads the runtime state for the container based on its type.
   * @param currentState
   * @param vmState
   * @param containerState
   * @param containerTemplateState
   * @throws Exception
   */
  private void loadRuntimeState(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState,
      final ContainerTemplateService.State containerTemplateState) throws Exception {

    String vmIpAddress = vmState.ipAddress;

    if (null == containerState.dynamicParameters) {
      containerState.dynamicParameters = new HashMap<>();
    }

    ContainersConfig.ContainerType containerType =
        ContainersConfig.ContainerType.valueOf(containerTemplateState.name);

    String sharedSecret = HostUtils.getDeployerContext(this).getSharedSecret();
    ContainersConfig containersConfig = HostUtils.getContainersConfig(this);
    Map<String, String> dynamicParameters = containersConfig.getContainerSpecs().get(containerType.name())
        .getDynamicParameters();
    dynamicParameters.put("memoryMb",
        String.valueOf(containersConfig.getContainerSpecs().get(containerType.name()).getMemoryMb()));
    dynamicParameters.put("cpuCount",
        String.valueOf(containersConfig.getContainerSpecs().get(containerType.name()).getCpuCount()));
    dynamicParameters.put("diskGb",
        String.valueOf(containersConfig.getContainerSpecs().get(containerType.name()).getDiskGb()));
    if (dynamicParameters != null) {
      containerState.dynamicParameters.putAll(dynamicParameters);
    }
    switch (containerType) {
      case ManagementApi:
        containerState.dynamicParameters.put(ENV_API_REGISTRATION_ADDRESS, vmIpAddress);
        containerState.dynamicParameters.put(ENV_SHARED_SECRET, sharedSecret);
        setEnvironmentVariablesFromDeploymentServiceState(currentState, vmState, containerState,
            containerTemplateState);
        break;

      case Chairman:
        containerState.dynamicParameters.put(ENV_CHAIRMAN_REGISTRATION_ADDRESS, vmIpAddress);
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper));
        break;

      case RootScheduler:
        containerState.dynamicParameters.put(ENV_ROOT_SCHEDULER_REGISTRATION_ADDRESS, vmIpAddress);
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper));
        break;

      case Deployer:
        containerState.dynamicParameters.put(ENV_DEPLOYER_REGISTRATION_ADDRESS, vmIpAddress);
        containerState.dynamicParameters.put(ENV_SHARED_SECRET, sharedSecret);
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper,
                ContainersConfig.ContainerType.LoadBalancer));
        break;

      case Housekeeper:
        containerState.dynamicParameters.put(ENV_HOUSEKEEPER_REGISTRATION_ADDRESS, vmIpAddress);
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper));
        break;

      case CloudStore:
        containerState.dynamicParameters.put(ENV_CLOUD_STORE_REGISTRATION_ADDRESS, vmIpAddress);
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper));
        break;

      case Zookeeper:
        // Load list of zookeeper IPs
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.Zookeeper));

        break;

      case LoadBalancer:
        scheduleQueriesForGeneratingRuntimeState(
            currentState, vmIpAddress, containerTemplateState, containerState,
            Arrays.asList(
                ContainersConfig.ContainerType.ManagementApi));

        break;

      case Lightwave:
        containerState.dynamicParameters.put(ENV_LIGHTWAVE_ADDRESS, vmIpAddress);
        setLightwaveVariablesFromDeploymentServiceState(currentState, vmState, containerState,
            containerTemplateState);
        break;

      default:
        ServiceUtils.logInfo(this, "No runtime environment needs to be generated for: ", containerType);
        patchContainerWithDynamicParameters(currentState, containerState);
        break;
    }
  }

  /**
   * This method gets the deployment service state, and then set the related lightwave parameters.
   *
   * @param currentState
   * @param vmState
   * @param containerState
   * @param containerTemplateState
   */
  private void setLightwaveVariablesFromDeploymentServiceState(
        final State currentState,
        final VmService.State vmState,
        final ContainerService.State containerState,
        final ContainerTemplateService.State containerTemplateState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);
                    containerState.dynamicParameters.put(ENV_LIGHTWAVE_ADMIN_USERNAME, deploymentState.oAuthUserName);
                    containerState.dynamicParameters.put(ENV_LIGHTWAVE_PASSWORD, deploymentState.oAuthPassword);
                    containerState.dynamicParameters.put(ENV_LIGHTWAVE_DOMAIN, deploymentState.oAuthTenantName);
                    patchDeploymentStateWithAuthParameters(currentState, vmState, containerState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
  * Updates deployment state with lightwave container IP and port.
  *
  * @param currentState
  * @param vmState
  * @param containerState
  */
    private void patchDeploymentStateWithAuthParameters(
        final State currentState, final VmService.State vmState, final ContainerService.State containerState) {

    DeploymentService.State patchState = new DeploymentService.State();
    patchState.oAuthServerAddress = vmState.ipAddress;
    patchState.oAuthServerPort = ServicePortConstants.LIGHTWAVE_PORT;

    CloudStoreHelper cloudStoreHelper = ((DeployerDcpServiceHost) getHost()).getCloudStoreHelper();
    cloudStoreHelper.patchEntity(this, currentState.deploymentServiceLink, patchState,
      new Operation.CompletionHandler() {
        @Override
        public void handle(Operation operation, Throwable throwable) {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          patchContainerWithDynamicParameters(currentState, containerState);
        }
      }
    );
  }

  /**
   * This method gets the deployment service state, and then set the related dynamic parameters.
   *
   * @param currentState
   */
  private void setEnvironmentVariablesFromDeploymentServiceState(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState,
      final ContainerTemplateService.State containerTemplateState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(currentState.deploymentServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    DeploymentService.State deploymentState = completedOp.getBody(DeploymentService.State.class);

                    // Set Auth
                    containerState.dynamicParameters.put(ENV_ENABLE_AUTH,
                        deploymentState.oAuthEnabled.toString());
                    if (deploymentState.oAuthEnabled) {
                      containerState.dynamicParameters.put(ENV_SWAGGER_LOGIN_URL,
                          deploymentState.oAuthResourceLoginEndpoint);
                      containerState.dynamicParameters.put(ENV_SWAGGER_LOGOUT_URL,
                          deploymentState.oAuthLogoutEndpoint);
                      containerState.dynamicParameters.put(ENV_AUTH_SERVER_TENANT,
                          deploymentState.oAuthTenantName);
                      containerState.dynamicParameters.put(ENV_AUTH_SERVER_PORT,
                          String.valueOf(ServicePortConstants.LIGHTWAVE_PORT));
                    }

                    // Set Image Datastore
                    containerState.dynamicParameters.put(ENV_DATASTORE, deploymentState.imageDataStoreName);

                    // Set Host Ip
                    setEsxHostIp(currentState, vmState, containerState, containerTemplateState,
                        deploymentState.oAuthEnabled);

                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
   * Sets IP of the ESX host.
   * @param currentState
   * @param vmState
   * @param containerTemplateState
   * @param isAuthEnabled
   */
  private void setEsxHostIp(
      final State currentState,
      final VmService.State vmState,
      final ContainerService.State containerState,
      final ContainerTemplateService.State containerTemplateState, boolean isAuthEnabled) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(vmState.hostServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    HostService.State esxHypervisorState = completedOp.getBody(HostService.State.class);
                    containerState.dynamicParameters.put(ENV_ESX_HOST, esxHypervisorState.hostAddress);

                    List<ContainersConfig.ContainerType> containerList = null;
                    if (isAuthEnabled) {
                      containerList = Arrays.asList(
                          ContainersConfig.ContainerType.Zookeeper,
                          ContainersConfig.ContainerType.Lightwave
                      );
                    } else {
                      containerList = Arrays.asList(ContainersConfig.ContainerType.Zookeeper);
                    }

                    scheduleQueriesForGeneratingRuntimeState(
                        currentState, vmState.ipAddress, containerTemplateState, containerState, containerList);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  /**
   * Generates a bunch of queries to get the other container type related information
   * which is needed for the current container.
   * @param currentState
   * @param vmIpAddress
   * @param containerTemplateState
   * @param containerState
   * @param containerTypeList
   */
  private void scheduleQueriesForGeneratingRuntimeState(
      final State currentState,
      final String vmIpAddress,
      final ContainerTemplateService.State containerTemplateState,
      final ContainerService.State containerState,
      List<ContainersConfig.ContainerType> containerTypeList) {
    final AtomicInteger pendingRequests = new AtomicInteger(containerTypeList.size());
    final Map<ContainersConfig.ContainerType, List<String>> response = new ConcurrentHashMap<>();

    FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> futureCallback =
        new FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>>() {
          @Override
          public void onSuccess(@Nullable Pair<ContainersConfig.ContainerType, List<String>> result) {
            response.put(result.getFirst(), result.getSecond());

            if (0 == pendingRequests.decrementAndGet()) {
              for (Map.Entry<ContainersConfig.ContainerType, List<String>> entry : response.entrySet()) {
                buildRuntimeEnvironmentVars(vmIpAddress, containerState, containerTemplateState, entry.getValue(), entry
                    .getKey());
              }
              patchContainerWithDynamicParameters(currentState, containerState);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (ContainersConfig.ContainerType containerType : containerTypeList) {
      scheduleQueryContainerTemplateService(containerType, futureCallback);
    }
  }

  private void scheduleQueryContainerTemplateService(
      final ContainersConfig.ContainerType containerType,
      final FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> callback) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query nameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(containerType.name());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(nameClause);
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
              QueryTaskUtils.logQueryResults(BuildRuntimeConfigurationTaskService.this, documentLinks);
              checkState(1 == documentLinks.size());
              queryContainersForTemplate(documentLinks.iterator().next(), containerType, callback);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  public void queryContainersForTemplate(
      String containerTemplateServiceLink,
      final ContainersConfig.ContainerType containerType,
      final FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> callback) {

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
              QueryTaskUtils.logQueryResults(BuildRuntimeConfigurationTaskService.this, documentLinks);
              getContainerEntities(documentLinks, containerType, callback);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getContainerEntities(
      Collection<String> documentLinks,
      final ContainersConfig.ContainerType containerType,
      final FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> callback) {

    if (documentLinks.isEmpty()) {
      throw new DcpRuntimeException("Document links set is empty");
    }

    OperationJoin
        .create(documentLinks.stream()
            .map(documentLink -> Operation.createGet(this, documentLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            List<String> vmServiceLinks = ops.values().stream()
                .map(operation -> operation.getBody(ContainerService.State.class).vmServiceLink)
                .collect(Collectors.toList());
            loadIpsForVms(vmServiceLinks, containerType, callback);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void loadIpsForVms(
      List<String> vmServiceLinks,
      final ContainersConfig.ContainerType containerType,
      final FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> futureCallback) {

    if (vmServiceLinks.isEmpty()) {
      throw new DcpRuntimeException("VM service links set is empty");
    }

    OperationJoin
        .create(vmServiceLinks.stream()
            .map(vmServiceLink -> Operation.createGet(this, vmServiceLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            List<String> vmIps = ops.values().stream()
                .map(getOperation -> getOperation.getBody(VmService.State.class).ipAddress)
                .collect(Collectors.toList());
            futureCallback.onSuccess(new Pair<>(containerType, vmIps));
          } catch (Throwable t) {
            futureCallback.onFailure(t);
          }
        })
        .sendWith(this);
  }

  private void buildRuntimeEnvironmentVars(
      final String vmIpAddress,
      final ContainerService.State containerState,
      final ContainerTemplateService.State containerTemplateState,
      List<String> ipList,
      ContainersConfig.ContainerType containerTypeForIpList) {

    ContainersConfig.ContainerType containerType = ContainersConfig.ContainerType.valueOf(containerTemplateState.name);

    switch (containerType) {
      case ManagementApi:
        if (containerTypeForIpList == ContainersConfig.ContainerType.Zookeeper) {
          String zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
          containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        } else if (containerTypeForIpList == ContainersConfig.ContainerType.Lightwave) {
          if (0 != ipList.size()) {
            containerState.dynamicParameters.put(ENV_AUTH_SERVER_ADDRESS, ipList.get(0));
          }
        }
        break;

      case Chairman:
        String zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case RootScheduler:
        zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case Deployer:
        if (containerTypeForIpList == ContainersConfig.ContainerType.LoadBalancer) {
          containerState.dynamicParameters.put(ENV_LOADBALANCER_IP, ipList.get(0));
        } else if (containerTypeForIpList == ContainersConfig.ContainerType.Zookeeper) {
          zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
          containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        }
        break;

      case Housekeeper:
        zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case CloudStore:
        zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case LoadBalancer:
        List<LoadBalancerServer> serverList = generateServerList(ipList, API_FE_PORT);
        containerState.dynamicParameters.put(ENV_LOADBALANCER_SERVERS, new Gson().toJson(serverList));
        break;

      case Zookeeper:
        // Ensure that different instances of BuildRuntimeConfigurationTaskService will independently see replicas
        // in same order
        Collections.sort(ipList);

        Pair<Integer, List<ZookeeperServer>> result = generateZookeeperQuorumList(ipList, vmIpAddress);

        containerState.dynamicParameters.put(ENV_ZOOKEEPER_MY_ID, result.getFirst().toString());
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM, new Gson().toJson(result.getSecond()));
        if (ipList.size() == 1) {
          containerState.dynamicParameters.put(ENV_ZOOKEEPER_STANDALONE, Boolean.toString(true));
        }
        break;
      case Lightwave:
        break;
      default:
        throw new RuntimeException("Unkown Container Type");
    }
  }

  /**
   * This method creates a docker container by submitting a future task to
   * the executor service for the DCP host. On successful completion, the
   * service is transitioned to the FINISHED state.
   *
   */
  private void patchContainerWithDynamicParameters(final State currentState, final ContainerService
      .State containerState) {
    final Service service = this;

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
        TaskUtils.sendSelfPatch(service, patchState);
      }
    };

    ContainerService.State patchState = new ContainerService.State();
    patchState.dynamicParameters = containerState.dynamicParameters;

    // Update the Container service state with the dynamic parameters.
    Operation patchOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(), currentState.containerServiceLink))
        .setBody(patchState)
        .setCompletion(completionHandler);
    sendRequest(patchOperation);
  }


  private String generateReplicaList(List<String> replicaIps, String port) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < replicaIps.size(); i++) {
      builder.append(replicaIps.get(i)).append(":").append(port);
      if (i != replicaIps.size() - 1) {
        builder.append(",");
      }
    }
    return builder.toString();
  }

  private List<LoadBalancerServer> generateServerList(List<String> serverIps, String port) {
    List<LoadBalancerServer> serverList = new ArrayList<>();
    AtomicInteger index = new AtomicInteger(0);
    serverIps.stream()
        .forEach(serverIp ->
            serverList.add(new LoadBalancerServer("server-" + index.incrementAndGet(), serverIp + ":" + port)));
    return serverList;
  }

  private Pair<Integer, List<ZookeeperServer>> generateZookeeperQuorumList(
      List<String> zookeeperReplicas,
      String myIp) {
    int myId = 1;

    //'server.1=zookeeper1:2888:3888', 'server.2=zookeeper2:2888:3888',
    List<ZookeeperServer> quorumConfig = new ArrayList<>();
    boolean matchFound = false;
    for (int i = 0; i < zookeeperReplicas.size(); i++) {
      String replicaIp = zookeeperReplicas.get(i).trim();
      if (replicaIp.equals(myIp)) {
        myId = i + 1;
        matchFound = true;
      }

      quorumConfig.add(new ZookeeperServer(String.format("server.%s=%s:2888:3888", i + 1, replicaIp)));
    }

    if (!matchFound) {
      // Really should NEVER EVER happen. But just a sanity check
      throw new RuntimeException("Zookeeper replica list doesn't contain IP: " + myIp);
    }

    ServiceUtils.logInfo(this, "Generated Zookeeper(%s) Quorum: %s", myId, quorumConfig.toString());
    return new Pair<Integer, List<ZookeeperServer>>(myId, quorumConfig);
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage Supplies the state to which the service instance should be
   *              transitioned.
   * @param e     Supplies an optional Throwable object representing the failure
   *              encountered by the service instance.
   * @return A State object which can be used to submit a stage progress self-
   * patch.
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable e) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != e) {
      state.taskState.failure = Utils.toServiceErrorResponse(e);
    }

    return state;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * transition to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the failure encountered by the service instance.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }
}
