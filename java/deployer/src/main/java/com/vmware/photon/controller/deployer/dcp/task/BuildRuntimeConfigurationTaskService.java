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
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.configuration.LoadBalancerServer;
import com.vmware.photon.controller.deployer.configuration.ZookeeperServer;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.Pair;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.gson.Gson;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
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
  public static final String ENV_ESX_HOST = "ESX_HOST";
  public static final String ENV_DATASTORE = "DATASTORE";
  public static final String ENV_ENABLE_SYSLOG = "ENABLE_SYSLOG";
  public static final String ENV_SYSLOG_ENDPOINT = "SYSLOG_ENDPOINT";
  public static final String ENV_NTP_ENDPOINT = "NTP_SERVER";
  public static final String ENV_DEPLOYMENT_ID = "DEPLOYMENT_ID";

  public static final String ENV_ENABLE_AUTH = "ENABLE_AUTH";
  public static final String ENV_SWAGGER_LOGIN_URL = "SWAGGER_LOGIN_URL";
  public static final String ENV_SWAGGER_LOGOUT_URL = "SWAGGER_LOGOUT_URL";
  public static final String ENV_MGMT_UI_LOGIN_URL = "MGMT_UI_LOGIN_URL";
  public static final String ENV_MGMT_UI_LOGOUT_URL = "MGMT_UI_LOGOUT_URL";
  public static final String ENV_SHARED_SECRET = "SHARED_SECRET";
  public static final String ENV_AUTH_SERVER_ADDRESS = "AUTH_SERVER_ADDRESS";
  public static final String ENV_AUTH_SERVER_TENANT = "AUTH_SERVER_TENANT";
  public static final String ENV_AUTH_SERVER_PORT = "AUTH_SERVER_PORT";

  public static final String ENV_LIGHTWAVE_DOMAIN = "LIGHTWAVE_DOMAIN";
  public static final String ENV_LIGHTWAVE_ADMIN_USERNAME = "LIGHTWAVE_ADMIN_USERNAME";
  public static final String ENV_LIGHTWAVE_PASSWORD = "LIGHTWAVE_PASSWORD";
  public static final String ENV_LIGHTWAVE_ADDRESS = "LIGHTWAVE_ADDRESS";

  public static final String ENV_MGMT_UI_HTTP_SERVERS = "MGMT_UI_HTTP_SERVERS";
  public static final String ENV_MGMT_UI_HTTPS_SERVERS = "MGMT_UI_HTTPS_SERVERS";

  private static final String ZOOKEEPER_PORT = "2181";
  private static final String API_FE_PORT = "9000";
  private static final String MGMT_UI_HTTP_PORT = "20000";
  private static final String MGMT_UI_HTTPS_PORT = "20001";

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

    @Immutable
    @DefaultBoolean(value = true)
    public Boolean isNewDeployment;
  }

  public BuildRuntimeConfigurationTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
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

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME);
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
        getDocuments(currentState);
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
  private void getDocuments(State currentState) {

    Operation containerGet = Operation.createGet(this, currentState.containerServiceLink);
    Operation deploymentGet = HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink);

    OperationJoin
        .create(containerGet, deploymentGet)
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            getDocuments(currentState,
                ops.get(containerGet.getId()).getBody(ContainerService.State.class),
                ops.get(deploymentGet.getId()).getBody(DeploymentService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getDocuments(State currentState,
                            ContainerService.State containerState,
                            DeploymentService.State deploymentState) {

    Operation templateGet = Operation.createGet(this, containerState.containerTemplateServiceLink);
    Operation vmGet = Operation.createGet(this, containerState.vmServiceLink);

    OperationJoin
        .create(templateGet, vmGet)
        .setCompletion((ops, exs) -> {
          if (exs != null && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            setCommonState(currentState, containerState, deploymentState,
                ops.get(templateGet.getId()).getBody(ContainerTemplateService.State.class),
                ops.get(vmGet.getId()).getBody(VmService.State.class));
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void setCommonState(State currentState,
                              ContainerService.State containerState,
                              DeploymentService.State deploymentState,
                              ContainerTemplateService.State templateState,
                              VmService.State vmState) {

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

    String vmIpAddress = vmState.ipAddress;
    ContainersConfig.ContainerType containerType = ContainersConfig.ContainerType.valueOf(templateState.name);

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
    dynamicParameters.put("VM_IP", vmIpAddress);
    containerState.dynamicParameters.putAll(dynamicParameters);

    containerState.dynamicParameters.put(ENV_SHARED_SECRET, sharedSecret);
    containerState.dynamicParameters.put(ENV_DATASTORE, deploymentState.imageDataStoreNames.iterator().next());
    containerState.dynamicParameters.put(ENV_ENABLE_AUTH, deploymentState.oAuthEnabled.toString());

    if (deploymentState.oAuthEnabled) {
      containerState.dynamicParameters.put(ENV_SWAGGER_LOGIN_URL, deploymentState.oAuthSwaggerLoginEndpoint);
      containerState.dynamicParameters.put(ENV_SWAGGER_LOGOUT_URL, deploymentState.oAuthSwaggerLogoutEndpoint);
      containerState.dynamicParameters.put(ENV_MGMT_UI_LOGIN_URL, deploymentState.oAuthMgmtUiLoginEndpoint);
      containerState.dynamicParameters.put(ENV_MGMT_UI_LOGOUT_URL, deploymentState.oAuthMgmtUiLogoutEndpoint);
      containerState.dynamicParameters.put(ENV_AUTH_SERVER_TENANT, deploymentState.oAuthTenantName);
      containerState.dynamicParameters.put(ENV_AUTH_SERVER_PORT,
          String.valueOf(ServicePortConstants.LIGHTWAVE_PORT));
    }
    containerState.dynamicParameters.put(ENV_LIGHTWAVE_ADDRESS, vmIpAddress);
    containerState.dynamicParameters.put(ENV_LIGHTWAVE_ADMIN_USERNAME, deploymentState.oAuthUserName);
    containerState.dynamicParameters.put(ENV_LIGHTWAVE_ADMIN_USERNAME, deploymentState.oAuthUserName);
    containerState.dynamicParameters.put(ENV_LIGHTWAVE_PASSWORD, deploymentState.oAuthPassword);
    containerState.dynamicParameters.put(ENV_LIGHTWAVE_DOMAIN, deploymentState.oAuthTenantName);
    containerState.dynamicParameters.put(ENV_DEPLOYMENT_ID,
        ServiceUtils.getIDFromDocumentSelfLink(deploymentState.documentSelfLink));

    switch (containerType) {
      case Chairman:
      case RootScheduler:
      case Housekeeper:
      case CloudStore:
      case Zookeeper:
        scheduleQueriesForGeneratingRuntimeState(currentState, vmIpAddress, templateState, containerState,
            deploymentState, Collections.singletonList(ContainersConfig.ContainerType.Zookeeper));
        break;

      case ManagementApi:
        HostUtils.getCloudStoreHelper(this)
            .createGet(vmState.hostServiceLink)
            .setCompletion((op, ex) -> {
              if (null != ex) {
                failTask(ex);
                return;
              }

              try {
                containerState.dynamicParameters.put(ENV_ESX_HOST, op.getBody(HostService.State.class).hostAddress);
                scheduleQueriesForGeneratingRuntimeState(currentState, vmIpAddress, templateState, containerState,
                    deploymentState,
                    (deploymentState.oAuthEnabled && currentState.isNewDeployment) ?
                        Arrays.asList(ContainersConfig.ContainerType.Zookeeper,
                            ContainersConfig.ContainerType.Lightwave) :
                        Collections.singletonList(ContainersConfig.ContainerType.Zookeeper));
              } catch (Throwable t) {
                failTask(t);
              }
            })
            .sendWith(this);

        break;

      case Deployer:
        List<ContainersConfig.ContainerType> containerTypes = currentState.isNewDeployment ?
            Arrays.asList(ContainersConfig.ContainerType.Zookeeper, ContainersConfig.ContainerType.LoadBalancer) :
            Collections.singletonList(ContainersConfig.ContainerType.Zookeeper);
        scheduleQueriesForGeneratingRuntimeState(currentState, vmIpAddress, templateState, containerState,
            deploymentState, containerTypes);
        break;

      case LoadBalancer:

        DeploymentService.State patchState = new DeploymentService.State();
        patchState.loadBalancerAddress = vmIpAddress;

        HostUtils.getCloudStoreHelper(this)
            .createPatch(deploymentState.documentSelfLink)
            .setBody(patchState)
            .setCompletion((op, ex) -> {
              if (null != ex) {
                failTask(ex);
                return;
              }

              try {
                scheduleQueriesForGeneratingRuntimeState(currentState, vmIpAddress, templateState, containerState,
                  deploymentState, Collections.singletonList(ContainersConfig.ContainerType.ManagementApi));
              } catch (Throwable t) {
                failTask(t);
              }
            })
            .sendWith(this);
        break;

      case Lightwave:

        patchState = new DeploymentService.State();
        patchState.oAuthServerAddress = vmIpAddress;
        patchState.oAuthServerPort = ServicePortConstants.LIGHTWAVE_PORT;
        HostUtils.getCloudStoreHelper(this)
            .createPatch(deploymentState.documentSelfLink)
            .setBody(patchState)
            .setCompletion((op, ex) -> {
              if (null != ex) {
                failTask(ex);
                return;
              }

              try {
                patchContainerWithDynamicParameters(currentState, containerState);
              } catch (Throwable t) {
                failTask(t);
              }
            })
            .sendWith(this);

        break;

      default:
        ServiceUtils.logInfo(this, "No runtime environment needs to be generated for: ", containerType);
        patchContainerWithDynamicParameters(currentState, containerState);
        break;
    }
  }

  /**
   * Generates a bunch of queries to get the other container type related information
   * which is needed for the current container.
   *
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
      final DeploymentService.State deploymentState,
      List<ContainersConfig.ContainerType> containerTypeList) {
    final AtomicInteger pendingRequests = new AtomicInteger(containerTypeList.size());
    final Map<ContainersConfig.ContainerType, List<String>> response = new ConcurrentHashMap<>();

    FutureCallback<Triple<ContainersConfig.ContainerType, List<String>, DeploymentService.State>> futureCallback =
        new FutureCallback<Triple<ContainersConfig.ContainerType, List<String>, DeploymentService.State>>() {
      @Override
      public void onSuccess(@Nullable Triple<ContainersConfig.ContainerType, List<String>, DeploymentService.State>
                                result) {
        response.put(result.getLeft(), result.getMiddle());

        if (0 == pendingRequests.decrementAndGet()) {
          for (Map.Entry<ContainersConfig.ContainerType, List<String>> entry : response.entrySet()) {
            buildRuntimeEnvironmentVars(vmIpAddress, containerState, result.getRight(), containerTemplateState,
                entry.getValue(), entry.getKey());
          }
          patchContainerWithDynamicParameters(currentState, containerState);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>> setZookeeperMapCallback =
        new FutureCallback<Pair<ContainersConfig.ContainerType, List<String>>>() {
          @Override
          public void onSuccess(@Nullable Pair<ContainersConfig.ContainerType, List<String>> result) {
            // We have the IPs, now create the zookeeper map
            addZookeeperToDeploymentServiceMap(deploymentState, result, futureCallback);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    for (ContainersConfig.ContainerType containerType : containerTypeList) {
      scheduleQueryContainerTemplateService(containerType, setZookeeperMapCallback);
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
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
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
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
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
      throw new XenonRuntimeException("Document links set is empty");
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
      throw new XenonRuntimeException("VM service links set is empty");
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
      final DeploymentService.State deploymentState,
      final ContainerTemplateService.State containerTemplateState,
      List<String> ipList,
      ContainersConfig.ContainerType containerTypeForIpList) {

    ContainersConfig.ContainerType containerType = ContainersConfig.ContainerType.valueOf(containerTemplateState.name);
    String zookeeperUrl = generateReplicaList(ipList, ZOOKEEPER_PORT);

    switch (containerType) {
      case ManagementApi:
        if (containerTypeForIpList == ContainersConfig.ContainerType.Zookeeper) {
          containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        } else if (containerTypeForIpList == ContainersConfig.ContainerType.Lightwave) {
          if (0 != ipList.size()) {
            containerState.dynamicParameters.put(ENV_AUTH_SERVER_ADDRESS, ipList.get(0));
          }
        }
        break;

      case Chairman:
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case RootScheduler:
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case Deployer:
        if (containerTypeForIpList == ContainersConfig.ContainerType.LoadBalancer) {
          containerState.dynamicParameters.put(ENV_LOADBALANCER_IP, ipList.get(0));
        } else if (containerTypeForIpList == ContainersConfig.ContainerType.Zookeeper) {
          containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        }
        break;

      case Housekeeper:
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case CloudStore:
        containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM_URL, zookeeperUrl);
        break;

      case LoadBalancer:
        Map<String, String> serverPortMap = new HashMap<>();
        serverPortMap.put(ENV_LOADBALANCER_SERVERS, API_FE_PORT);
        serverPortMap.put(ENV_MGMT_UI_HTTP_SERVERS, MGMT_UI_HTTP_PORT);
        serverPortMap.put(ENV_MGMT_UI_HTTPS_SERVERS, MGMT_UI_HTTPS_PORT);

        for (Map.Entry<String, String> entry : serverPortMap.entrySet()) {
          List<LoadBalancerServer> serverList = generateServerList(ipList, entry.getValue());
          containerState.dynamicParameters.put(entry.getKey(), new Gson().toJson(serverList));
        }
        break;

      case Zookeeper:
        // Ensure that different instances of BuildRuntimeConfigurationTaskService will independently see replicas
        // in same order
        Collections.sort(ipList);
        generateZookeeperQuorumList(containerState, deploymentState, ipList, vmIpAddress);
        break;
      case Lightwave:
        break;
      default:
        throw new RuntimeException("Unkown Container Type");
    }
  }

  private void addZookeeperToDeploymentServiceMap(DeploymentService.State deploymentState,
                                                  Pair<ContainersConfig.ContainerType, List<String>> ipListPair,
                                                  final FutureCallback<Triple<ContainersConfig.ContainerType,
                                                      List<String>, DeploymentService.State>> callback) {
    DeploymentService.HostListChangeRequest hostListChangeRequest = new DeploymentService.HostListChangeRequest();
    hostListChangeRequest.kind = DeploymentService.HostListChangeRequest.Kind.UPDATE_ZOOKEEPER_INFO;
    hostListChangeRequest.zookeeperIpsToAdd = ipListPair.getSecond();

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createPatch(deploymentState.documentSelfLink)
            .setBody(hostListChangeRequest)
            .setCompletion(
                (completedOp, failure) -> {
                  if (failure != null) {
                    ServiceUtils.logSevere(BuildRuntimeConfigurationTaskService.this, failure);
                    failTask(failure);
                    return;
                  }

                  DeploymentService.State newDeploymentState = completedOp.getBody(DeploymentService.State.class);
                  callback.onSuccess(new ImmutableTriple<>
                      (ipListPair.getFirst(), ipListPair.getSecond(), newDeploymentState));
                }
            ));
  }

  private void generateZookeeperQuorumList(ContainerService.State containerState,
                                           DeploymentService.State deploymentState,
                                           List<String> ipList, String vmIpAddress) {

    String myId = null;
    String zookeeperServers = null;
    Pair<Integer, List<ZookeeperServer>> result = null;
    // New deployment or no management host on this deployment
    result = generateZookeeperQuorumList(ipList, vmIpAddress, deploymentState);
    myId = result.getFirst().toString();
    zookeeperServers = new Gson().toJson(result.getSecond());
    containerState.dynamicParameters.put(ENV_ZOOKEEPER_MY_ID, myId);
    containerState.dynamicParameters.put(ENV_ZOOKEEPER_QUORUM, zookeeperServers);
  }

  /**
   * This method creates a docker container by submitting a future task to
   * the executor service for the DCP host. On successful completion, the
   * service is transitioned to the FINISHED state.
   */
  private void patchContainerWithDynamicParameters(final State currentState,
                                                   final ContainerService.State containerState) {
    final Service service = this;

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        State selfPatchState = buildPatch(TaskState.TaskStage.FINISHED, null);
        TaskUtils.sendSelfPatch(service, selfPatchState);
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

  private Pair<Integer, List<ZookeeperServer>> generateZookeeperQuorumList(List<String> zookeeperReplicas,
      String myIp, DeploymentService.State deploymentState) {

    //'server.1=zookeeper1:2888:3888', 'server.2=zookeeper2:2888:3888',
    List<ZookeeperServer> quorumConfig = new ArrayList<>();
    boolean matchFound = false;
    int myId = 1;
    for (Map.Entry<Integer, String> zkPair : deploymentState.zookeeperIdToIpMap.entrySet()) {
      if (zkPair.getValue().equals(myIp)) {
        matchFound = true;
        myId = zkPair.getKey();
      }
      quorumConfig.add(new ZookeeperServer(String.format("server.%s=%s:2888:3888",
          zkPair.getKey(), zkPair.getValue())));
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
