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

import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.ssl.KeyStoreUtils;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateTenantResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateTenantResourcesTaskService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.ChildTaskAggregatorService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateContainerTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CreateManagementVmTaskService;
import com.vmware.photon.controller.deployer.xenon.task.RegisterAuthClientTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.RegisterAuthClientTaskService;
import com.vmware.photon.controller.deployer.xenon.task.UploadImageTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.UploadImageTaskService;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.photon.controller.deployer.xenon.util.Pair;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupState;
import com.vmware.xenon.services.common.NodeGroupService.UpdateQuorumRequest;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.NodeState.NodeStatus;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.FileUtils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon service representing the workflow of creating all management vms.
 */
public class BatchCreateManagementWorkflowService extends StatefulService {

  private static final int WAIT_FOR_CONVERGANCE_DELAY = 100;
  private static final int WAIT_FOR_CONVERGENCE_MAX_RETRIES = 60;

  private static final String MGMT_UI_LOGIN_REDIRECT_URL_TEMPLATE = "https://%s:4343/oauth_callback.html";
  private static final String MGMT_UI_LOGOUT_REDIRECT_URL_TEMPLATE = "https://%s:4343/logout_callback";
  private static final String SWAGGER_UI_LOGIN_REDIRECT_URL_TEMPLATE = "https://%s/api/login-redirect.html";
  private static final String SWAGGER_UI_LOGOUT_REDIRECT_URL_TEMPLATE = "https://%s/api/login-redirect.html";

  public static final String GENERATE_CERTIFICATE_SCRIPT_NAME = "generate-certificate";

  private static boolean inUnitTests = false;

  /**
   * This enum lists the services which require authentication.
   */
  public enum AuthenticationServiceType {
    SWAGGER,
    MGMT_UI
  }

  /**
   * This class defines the state of a {@link BatchCreateManagementWorkflowService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This value represents the current sub-stage for the task.
     */
    public SubStage subStage;

    /**
     * This enum represents the possible sub-states for this work-flow.
     */
    public enum SubStage {
      UPLOAD_IMAGE,
      ALLOCATE_RESOURCES,
      CREATE_LIGHTWAVE_VMS,
      WAIT_FOR_LIGHTWAVE_SERVICE,
      REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI,
      REGISTER_AUTH_CLIENT_FOR_MGMT_UI,
      GENERATE_CERT,
      CREATE_VMS,
      CREATE_CONTAINERS,
      WAIT_FOR_NODE_GROUP_CONVERGANCE,
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link BatchCreateManagementWorkflowService} instance.
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
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    /**
     * This value represents the interval, in milliseconds, to use when polling
     * the state of a task object returned by an API call.
     */
    @Positive
    public Integer taskPollDelay;

    /**
     * The image filename used to create the vm.
     */
    @NotNull
    @Immutable
    public String imageFile;

    /**
     * This value represents the polling interval override value to use for child tasks.
     */
    @Immutable
    public Integer childPollInterval;

    /**
     * This value represents if authentication is enabled or not.
     */
    @NotNull
    @Immutable
    public Boolean isAuthEnabled;

    /**
     * This value represents the oauth server address (lightwave VM IP address) for the current deployment.
     */
    @Immutable
    public String oAuthServerAddress;

    /**
     * This value represents the oauth tenant name (lightwave domain name) for the current deployment.
     */
    @Immutable
    public String oAuthTenantName;

    /**
     * This value represents the URL of the DeploymentService object.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    public String lightwaveVmServiceLink;

    /**
     * This value represents the NTP server configured at VM.
     */
    @Immutable
    public String ntpEndpoint;
  }

  public BatchCreateManagementWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  public static void setInUnitTests(boolean inUnitTests) {
    BatchCreateManagementWorkflowService.inUnitTests = inUnitTests;
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.UPLOAD_IMAGE;
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    start.setBody(startState).complete();

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

  /**
   * This method is called when a patch operation is performed for the current
   * service instance.
   *
   * @param patch Supplies the start operation object.
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
        processStartedState(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates a state object for internal consistency.
   *
   * @param currentState Supplies current state object.
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);

    if (currentState.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(null != currentState.taskState.subStage, "Sub-stage cannot be null in STARTED stage.");
      switch (currentState.taskState.subStage) {
        case UPLOAD_IMAGE:
        case ALLOCATE_RESOURCES:
        case CREATE_LIGHTWAVE_VMS:
        case WAIT_FOR_LIGHTWAVE_SERVICE:
        case REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI:
        case REGISTER_AUTH_CLIENT_FOR_MGMT_UI:
        case GENERATE_CERT:
        case CREATE_VMS:
        case CREATE_CONTAINERS:
        case WAIT_FOR_NODE_GROUP_CONVERGANCE:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage.toString());
      }
    } else {
      checkState(null == currentState.taskState.subStage, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    checkNotNull(startState.taskState.stage);
    checkNotNull(patchState.taskState.stage);

    // The task sub-state must be at least equal to the current task sub-state
    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
    // A document can never be patched to the CREATED state.
    checkState(patchState.taskState.stage.ordinal() > TaskState.TaskStage.CREATED.ordinal());

    // Patches cannot transition the document to an earlier state
    checkState(patchState.taskState.stage.ordinal() >= startState.taskState.stage.ordinal());

    // Patches cannot be applied to documents in terminal states.
    checkState(startState.taskState.subStage == null
        || startState.taskState.stage.ordinal() <= TaskState.TaskStage.STARTED.ordinal());
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null) {
      if (patchState.taskState.stage != startState.taskState.stage
          || patchState.taskState.subStage != startState.taskState.subStage) {
        ServiceUtils.logInfo(this, "Moving to stage %s:%s", patchState.taskState.stage, patchState.taskState.subStage);
      }
    }
    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method performs the appropriate tasks while in the STARTED state.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(State currentState) {
    switch (currentState.taskState.subStage) {
      case UPLOAD_IMAGE:
        uploadImage(currentState);
        break;
      case ALLOCATE_RESOURCES:
        allocateResources(currentState);
        break;
      case CREATE_LIGHTWAVE_VMS:
        createLightwaveVm(currentState, TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_LIGHTWAVE_SERVICE);
        break;
      case WAIT_FOR_LIGHTWAVE_SERVICE:
        waitForLightwaveService(currentState, TaskStage.STARTED,
            TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI);
        break;
      case REGISTER_AUTH_CLIENT_FOR_SWAGGER_UI:
        registerAuthClient(currentState, AuthenticationServiceType.SWAGGER, TaskStage.STARTED,
            TaskState.SubStage.REGISTER_AUTH_CLIENT_FOR_MGMT_UI);
        break;
      case REGISTER_AUTH_CLIENT_FOR_MGMT_UI:
        registerAuthClient(currentState, AuthenticationServiceType.MGMT_UI, TaskStage.STARTED,
            TaskState.SubStage.GENERATE_CERT);
        break;
      case GENERATE_CERT:
        generateCertificate(currentState);
        break;
      case CREATE_VMS:
        createVms(currentState);
        break;
      case CREATE_CONTAINERS:
        createContainers(currentState);
        break;
      case WAIT_FOR_NODE_GROUP_CONVERGANCE:
        waitForNodeGroupConvergance(currentState);
        break;
    }
  }

  private void waitForNodeGroupConvergance(State currentState) {
    // get all container
    Operation queryContainersOp = buildBroadcastKindQuery(ContainerService.State.class);
    // get all container templates
    Operation queryTemplatesOp =  buildBroadcastKindQuery(ContainerTemplateService.State.class);
    // get all vms
    Operation queryVmsOp = buildBroadcastKindQuery(VmService.State.class);

    OperationJoin.create(queryContainersOp, queryTemplatesOp, queryVmsOp)
      .setCompletion((os, ts) -> {
        if (ts != null && !ts.isEmpty()) {
          failTask(ts.values());
          return;
        }
        List<ContainerService.State> containers = QueryTaskUtils
            .getBroadcastQueryDocuments(ContainerService.State.class, os.get(queryContainersOp.getId()));
        List<ContainerTemplateService.State> templates = QueryTaskUtils
            .getBroadcastQueryDocuments(ContainerTemplateService.State.class, os.get(queryTemplatesOp.getId()));
        List<VmService.State> vms = QueryTaskUtils
            .getBroadcastQueryDocuments(VmService.State.class, os.get(queryVmsOp.getId()));

        String templateLink = templates.stream()
            .filter(template -> template.name.equals(ContainersConfig.ContainerType.PhotonControllerCore.name()))
            .findFirst().get().documentSelfLink;
        List<String> vmServiceLinks = containers.stream()
            .filter(container -> container.containerTemplateServiceLink.equals(templateLink))
            .map(container -> container.vmServiceLink)
            .collect(Collectors.toList());
        List<VmService.State> photonControllerCoreVms = vms.stream()
            .filter(vm -> vmServiceLinks.contains(vm.documentSelfLink))
            .collect(Collectors.toList());

        // Update Quorum on services
        Map<String, List<Pair<String, Integer>>> xenonServiceToIp = mapXenonServices(photonControllerCoreVms);
        List<Operation> quorumUpdates = getQuroumUpdateOperations(currentState, xenonServiceToIp, x -> x);
        OperationJoin.create(quorumUpdates)
            .setCompletion((os2, ts2) -> {
              if (ts2 != null && !ts2.isEmpty()) {
                failTask(ts2.values());
                return;
              }

              // Wait until services have stabilized
              List<Operation> nodeGroupStatusChecks = getNodeGroupStateOperations(currentState, xenonServiceToIp);
              checkNodeGroupStatus(currentState, nodeGroupStatusChecks, xenonServiceToIp, 0, 0);
            })
            .sendWith(BatchCreateManagementWorkflowService.this);

      })
      .sendWith(this);
  }

  private void checkNodeGroupStatus(
      State currentState,
      List<Operation> nodeGroupStatusChecks,
      Map<String, List<Pair<String, Integer>>> xenonServiceToIp,
      int conssecutiveSuccesses,
      int tries) {
    OperationJoin.create(nodeGroupStatusChecks)
      .setCompletion((os, ts) -> {
        if (ts != null && !ts.isEmpty()) {
          failTask(ts.values());
          return;
        }
        if (allServicesAvailable(os)) {
          int currentSuccess = conssecutiveSuccesses + 1;
          if (currentSuccess > 30) {
            resetQuorum(currentState, xenonServiceToIp);
          } else {
            getHost().schedule(() -> {
              checkNodeGroupStatus(currentState, nodeGroupStatusChecks, xenonServiceToIp, currentSuccess, tries);
            }, WAIT_FOR_CONVERGANCE_DELAY, TimeUnit.MILLISECONDS);
          }
        } else {
          int currentTry = tries + 1;
          if (currentTry > WAIT_FOR_CONVERGENCE_MAX_RETRIES) {
            String serviceList = logUnconvergedServices(os);
            failTask(new Exception("Nodegroup(s) did not converege [" + serviceList + "]"));
            return;
          }
          getHost().schedule(() -> {
            checkNodeGroupStatus(currentState, nodeGroupStatusChecks, xenonServiceToIp, 0, currentTry);
          }, WAIT_FOR_CONVERGANCE_DELAY, TimeUnit.MILLISECONDS);
        }
      })
      .sendWith(this);
  }

  private void resetQuorum(State currentState, Map<String, List<Pair<String, Integer>>> xenonServiceToIp) {
    OperationJoin.create(getQuroumUpdateOperations(currentState, xenonServiceToIp, x -> x / 2 + 1))
      .setCompletion((os, ts) -> {
        if (ts != null && !ts.isEmpty()) {
          failTask(ts.values());
          return;
        }
        TaskState state = new TaskState();
        state.stage = TaskStage.FINISHED;
        sendStageProgressPatch(state);
      })
      .sendWith(this);
  }

  private String logUnconvergedServices(Map<Long, Operation> os) {
    List<String> strings = new ArrayList<>();
    for (Operation o : os.values()) {
      NodeGroupState nodeGroupState = o.getBody(NodeGroupState.class);
      if (!serviceAvailable(nodeGroupState)) {
        strings.add(nodeGroupState.nodes.values().iterator().next().groupReference.toString());
      }
    }
    return String.join(",", strings);
  }

  private boolean allServicesAvailable(Map<Long, Operation> os) {
    boolean available = true;
    for (Operation o : os.values()) {
      NodeGroupState nodeGroupState = o.getBody(NodeGroupState.class);
      available = available && serviceAvailable(nodeGroupState);
    }
    return available;
  }

  private boolean serviceAvailable(NodeGroupState nodeGroupState) {
    boolean available = true;
    for (NodeState state : nodeGroupState.nodes.values()) {
      available = available && NodeStatus.AVAILABLE == state.status;
    }
    return available;
  }

  private List<Operation> getNodeGroupStateOperations(
      State currentState,
      Map<String, List<Pair<String, Integer>>> xenonServiceToIp) {

    String protocol = "http";
    if (!BatchCreateManagementWorkflowService.inUnitTests && currentState.isAuthEnabled) {
      protocol = "https";
    }

    List<Operation> ops = new ArrayList<>();
    for (Entry<String, List<Pair<String, Integer>>> e : xenonServiceToIp.entrySet()) {
      for (Pair<String, Integer> address : e.getValue()) {
        Operation op = Operation
            .createGet(
                UriUtils.buildUri(protocol, address.getFirst(), address.getSecond(), ServiceUriPaths
                    .DEFAULT_NODE_GROUP, null));
        ops.add(op);
      }
    }
    return ops;
  }

  private List<Operation> getQuroumUpdateOperations(
      State currentState,
      Map<String, List<Pair<String, Integer>>> xenonServiceToIp,
      IntUnaryOperator computeQuorum) {

    String protocol = "http";
    if (!BatchCreateManagementWorkflowService.inUnitTests && currentState.isAuthEnabled) {
      protocol = "https";
    }

    List<Operation> quorumUpdates = new ArrayList<>();
    for (Entry<String, List<Pair<String, Integer>>> entry : xenonServiceToIp.entrySet()) {
      UpdateQuorumRequest patch = new UpdateQuorumRequest();
      patch.kind = UpdateQuorumRequest.KIND;
      patch.membershipQuorum = computeQuorum.applyAsInt(entry.getValue().size());
      patch.isGroupUpdate = false;
      for (Pair<String, Integer> address : entry.getValue()) {
        quorumUpdates.add(
          Operation.createPatch(
              UriUtils.buildUri(protocol, address.getFirst(), address.getSecond(), ServiceUriPaths
                  .DEFAULT_NODE_GROUP, null))
            .setBody(patch)
        );
      }
    }
    return quorumUpdates;
  }

  private Map<String, List<Pair<String, Integer>>> mapXenonServices(
      List<VmService.State> vms) {

    List<String> xenonServices = ImmutableList.<String>builder()
        .add(Constants.CLOUDSTORE_SERVICE_NAME)
        .add(Constants.DEPLOYER_SERVICE_NAME)
        .add(Constants.HOUSEKEEPER_SERVICE_NAME)
        .build();
    Map<String, List<Pair<String, Integer>>> map = new HashMap<>();

    for (String serviceName : xenonServices) {
      List<Pair<String, Integer>> serverAddresses = vms.stream()
          .map(s -> {
            return new Pair<String, Integer>(s.ipAddress, s.deployerXenonPort);
          })
          .distinct()
          .collect(Collectors.toList());
      map.put(serviceName, serverAddresses);
    }
    return map;
  }

  private Operation buildBroadcastKindQuery(Class<? extends ServiceDocument> type) {
    Query query = Query.Builder.create().addKindFieldClause(type)
        .build();
    return Operation
      .createPost(UriUtils.buildBroadcastRequestUri(
          UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS), ServiceUriPaths.DEFAULT_NODE_SELECTOR))

      .setBody(QueryTask.Builder
          .createDirectTask()
          .addOptions(EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
          .setQuery(query)
          .build());
  }

  /**
   * This method starts the upload image task.
   *
   * @param currentState Supplies the current state object.
   */
  private void uploadImage(final State currentState) {

    UploadImageTaskService.State startState = new UploadImageTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false,
        buildPatch(TaskStage.STARTED, TaskState.SubStage.ALLOCATE_RESOURCES));
    startState.taskPollDelay = currentState.childPollInterval;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.imageName = "management-vm-image";
    startState.imageFile = currentState.imageFile;

    sendRequest(Operation
        .createPost(this, UploadImageTaskFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  private void allocateResources(State currentState) {

    AllocateTenantResourcesTaskService.State startState = new AllocateTenantResourcesTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false,
        buildPatch(TaskStage.STARTED, TaskState.SubStage.CREATE_LIGHTWAVE_VMS));
    startState.taskPollDelay = currentState.taskPollDelay;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.quotaLineItems = Collections.singletonList(
        new QuotaLineItem("vm.count", Integer.MAX_VALUE, QuotaUnit.COUNT));

    sendRequest(Operation
        .createPost(this, AllocateTenantResourcesTaskFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  private void createLightwaveVm(State currentState, TaskStage stage, TaskState.SubStage subStage) {

    if (!currentState.isAuthEnabled) {
      TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
      return;
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerTemplateService.State.class)
            .addFieldClause(ContainerTemplateService.State.FIELD_NAME_NAME,
                ContainersConfig.ContainerType.Lightwave.name())
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
                List<String> containerTemplateServiceDocumentLinks = o.getBody(QueryTask.class).results.documentLinks;
                if (containerTemplateServiceDocumentLinks.size() == 0) {
                  ServiceUtils.logInfo(this, "No container template found for Lightwave");
                  TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
                  return;
                }
                checkState(containerTemplateServiceDocumentLinks.size() == 1);
                createLightwaveVm(currentState, containerTemplateServiceDocumentLinks.get(0));
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void generateCertificate(State currentState) {
    HostUtils.getCloudStoreHelper(this).createGet(currentState.deploymentServiceLink)
      .setCompletion((o, e) -> {
        if (e != null) {
          failTask(e);
          return;
        }
        generateCertificate(o.getBody(DeploymentService.State.class));
      }).sendWith(this);
  }

  private void generateCertificate(DeploymentService.State deploymentState) {
    if (!deploymentState.oAuthEnabled) {
      sendStageProgressPatch(TaskStage.STARTED, TaskState.SubStage.CREATE_VMS);
      return;
    }

    List<String> command = new ArrayList<>();
    command.add("./" + GENERATE_CERTIFICATE_SCRIPT_NAME);
    command.add(deploymentState.oAuthServerAddress);
    command.add(deploymentState.oAuthPassword);
    command.add(deploymentState.oAuthTenantName);
    command.add(PhotonControllerXenonHost.KEYSTORE_FILE);
    command.add(PhotonControllerXenonHost.KEYSTORE_PASSWORD);

    DeployerContext deployerContext = HostUtils.getDeployerContext(this);
    File scriptLogFile = new File(
        deployerContext.getScriptLogDirectory(),
        GENERATE_CERTIFICATE_SCRIPT_NAME + ".log");

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    Futures.addCallback(futureTask,
        new FutureCallback<Integer>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull Integer result) {
            try {
              if (result != 0) {
                logScriptErrorAndFail(result, scriptLogFile);
              } else {
                // Set the inInstaller flag to true which would allow us to override the xenon service client to talk
                // to the auth enabled newly deployed management plane using https with two way SSL.
                ((PhotonControllerXenonHost) getHost()).setInInstaller(true);

                // need to switch the ssl context for the thrift clients to use
                // the generated certs to be able to talk to the authenticated
                // agents
                try {
                  SSLContext sslContext = SSLContext.getInstance(KeyStoreUtils.THRIFT_PROTOCOL);
                  TrustManagerFactory tmf = null;

                  tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                  KeyStore keyStore = KeyStore.getInstance("JKS");
                  InputStream in = FileUtils.openInputStream(new File(PhotonControllerXenonHost.KEYSTORE_FILE));
                  keyStore.load(in, PhotonControllerXenonHost.KEYSTORE_PASSWORD.toCharArray());
                  tmf.init(keyStore);
                  sslContext.init(null, tmf.getTrustManagers(), null);
                  ((PhotonControllerXenonHost) getHost()).regenerateThriftClients(sslContext);

                  KeyStoreUtils.acceptAllCerts(KeyStoreUtils.THRIFT_PROTOCOL);
                } catch (Throwable t) {
                  ServiceUtils.logSevere(BatchCreateManagementWorkflowService.this,
                      "Regenerating the SSL Context for thrift failed, ignoring to make tests pass, it fail later");
                  ServiceUtils.logSevere(BatchCreateManagementWorkflowService.this, t);
                }
                sendStageProgressPatch(TaskStage.STARTED, TaskState.SubStage.CREATE_VMS);
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

  private void createLightwaveVm(State currentState, String lightwaveContainerTemplateServiceLink) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerService.State.class)
            .addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK,
                lightwaveContainerTemplateServiceLink)
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
                checkState(documentLinks.size() == 1);
                createLightwaveVm(currentState, documentLinks.get(0),
                    TaskState.SubStage.WAIT_FOR_LIGHTWAVE_SERVICE);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createLightwaveVm(State currentState, String lightwaveContainerServiceLink,
                                 TaskState.SubStage subStage) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createGet(lightwaveContainerServiceLink)
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    ContainerService.State containerServiceState = completedOp.getBody(ContainerService.State.class);
                    createLightwaveVm(currentState, containerServiceState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void createLightwaveVm(State currentState, ContainerService.State lightwaveContainerServiceState) {
    CreateManagementVmTaskService.State startState = new CreateManagementVmTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.vmServiceLink = lightwaveContainerServiceState.vmServiceLink;
    startState.parentPatchBody = Utils.toJson(false, false,
        buildPatch(TaskStage.STARTED,
            TaskState.SubStage.WAIT_FOR_LIGHTWAVE_SERVICE, startState.vmServiceLink));
    startState.ntpEndpoint = currentState.ntpEndpoint;
    startState.taskPollDelay = currentState.childPollInterval;
    startState.isAuthEnabled = currentState.isAuthEnabled;
    startState.oAuthServerAddress = currentState.oAuthServerAddress;
    startState.oAuthTenantName = currentState.oAuthTenantName;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;

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

  private void waitForLightwaveService(State currentState, TaskStage stage, TaskState.SubStage subStage) {

    if (!currentState.isAuthEnabled) {
      TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
      return;
    }

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerTemplateService.State.class)
            .addFieldClause(ContainerTemplateService.State.FIELD_NAME_NAME,
                ContainersConfig.ContainerType.Lightwave.name())
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
                List<String> containerTemplateServiceDocumentLinks = o.getBody(QueryTask.class).results.documentLinks;
                if (containerTemplateServiceDocumentLinks.size() == 0) {
                  ServiceUtils.logInfo(this, "No container template found for Lightwave");
                  TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
                  return;
                }
                checkState(containerTemplateServiceDocumentLinks.size() == 1);
                waitForLightwaveService(currentState, containerTemplateServiceDocumentLinks.get(0), stage, subStage);
              } catch (Throwable t) {
                failTask(t);
              }
            }));

  }

  private void waitForLightwaveService(State currentState, String lightwaveContainerTemplateLink,
                                       TaskStage stage, TaskState.SubStage subStage) {
    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(ContainerService.State.class)
            .addFieldClause(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK,
                lightwaveContainerTemplateLink)
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
                checkState(documentLinks.size() == 1);
                waitForLightwaveService(currentState, documentLinks.get(0), subStage);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void waitForLightwaveService(State currentState, String lightwaveContainerLink, TaskState.SubStage subStage) {
    CreateContainerTaskService.State startState = new CreateContainerTaskService.State();
    startState.parentTaskServiceLink = getSelfLink();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.containerServiceLink = lightwaveContainerLink;
    startState.parentPatchBody = Utils.toJson(false, false, buildPatch(TaskStage.STARTED, subStage));
    sendRequest(Operation
        .createPost(this, CreateContainerTaskFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              }
            }));
  }

  private void createVms(State currentState) {

    QueryTask queryTask;

    queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(VmService.State.class)
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
                List<String> vmServiceDocumentLinks = o.getBody(QueryTask.class).results.documentLinks;
                if (currentState.lightwaveVmServiceLink != null) {
                  vmServiceDocumentLinks.remove(currentState.lightwaveVmServiceLink);
                }
                createVms(currentState, o.getBody(QueryTask.class).results.documentLinks);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createVms(State currentState, List<String> vmServiceLinks) {

    if (vmServiceLinks.size() == 0) {
      TaskUtils.sendSelfPatch(this, buildPatch(TaskStage.STARTED, TaskState.SubStage.CREATE_CONTAINERS));
      return;
    }
    ChildTaskAggregatorService.State startState = new ChildTaskAggregatorService.State();
    startState.parentTaskLink = getSelfLink();
    startState.parentPatchBody = Utils.toJson(false, false,
        buildPatch(TaskStage.STARTED, TaskState.SubStage.CREATE_CONTAINERS));
    startState.pendingCompletionCount = vmServiceLinks.size();
    startState.errorThreshold = 0.0;

    sendRequest(Operation
        .createPost(this, ChildTaskAggregatorFactoryService.SELF_LINK)
        .setBody(startState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
                return;
              }

              try {
                createVms(currentState, vmServiceLinks, o.getBody(ServiceDocument.class).documentSelfLink);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void createVms(State currentState,
                         List<String> vmServiceLinks,
                         String aggregatorServiceLink) {

    for (String vmServiceLink : vmServiceLinks) {
      CreateManagementVmTaskService.State startState = new CreateManagementVmTaskService.State();
      startState.parentTaskServiceLink = aggregatorServiceLink;
      startState.vmServiceLink = vmServiceLink;
      startState.ntpEndpoint = currentState.ntpEndpoint;
      startState.taskPollDelay = currentState.childPollInterval;
      startState.isAuthEnabled = currentState.isAuthEnabled;
      startState.oAuthServerAddress = currentState.oAuthServerAddress;
      startState.oAuthTenantName = currentState.oAuthTenantName;
      startState.deploymentServiceLink = currentState.deploymentServiceLink;

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
  }

  /**
   * This method starts the create containers workflow.
   *
   * @param currentState Supplies the current state object.
   */
  private void createContainers(final State currentState) {
    final Service service = this;

    FutureCallback<CreateContainersWorkflowService.State> callback = new
        FutureCallback<CreateContainersWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable CreateContainersWorkflowService.State result) {
            if (result.taskState.stage == TaskState.TaskStage.FAILED) {
              TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FAILED, null, result.taskState.failure));
            } else if (result.taskState.stage == TaskState.TaskStage.CANCELLED) {
              TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
            } else {
              sendProgressPatch(
                  result.taskState,
                  TaskState.TaskStage.STARTED,
                  TaskState.SubStage.WAIT_FOR_NODE_GROUP_CONVERGANCE);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };
    CreateContainersWorkflowService.State startState = new CreateContainersWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.isAuthEnabled = currentState.isAuthEnabled;
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


  private void registerAuthClient(State currentState,
                                  AuthenticationServiceType serviceType,
                                  TaskState.TaskStage nextStage,
                                  TaskState.SubStage nextSubStage) {

    if (!currentState.isAuthEnabled) {
      ServiceUtils.logInfo(this, "Skipping auth client registration for " + serviceType + " (auth is disabled");
      sendStageProgressPatch(nextStage, nextSubStage);
      return;
    }

    RegisterAuthClientTaskService.State startState = new RegisterAuthClientTaskService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;

    switch (serviceType) {
      case SWAGGER:
        startState.loginRedirectUrlTemplate = SWAGGER_UI_LOGIN_REDIRECT_URL_TEMPLATE;
        startState.logoutRedirectUrlTemplate = SWAGGER_UI_LOGOUT_REDIRECT_URL_TEMPLATE;
        break;
      case MGMT_UI:
        startState.loginRedirectUrlTemplate = MGMT_UI_LOGIN_REDIRECT_URL_TEMPLATE;
        startState.logoutRedirectUrlTemplate = MGMT_UI_LOGOUT_REDIRECT_URL_TEMPLATE;
        break;
    }

    TaskUtils.startTaskAsync(this,
        RegisterAuthClientTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        RegisterAuthClientTaskService.State.class,
        currentState.taskPollDelay,
        new FutureCallback<RegisterAuthClientTaskService.State>() {
          @Override
          public void onSuccess(@Nullable RegisterAuthClientTaskService.State state) {
            switch (state.taskState.stage) {
              case FINISHED: {
                DeploymentService.State patchState = new DeploymentService.State();
                switch (serviceType) {
                  case SWAGGER:
                    patchState.oAuthSwaggerLoginEndpoint = state.loginUrl;
                    patchState.oAuthSwaggerLogoutEndpoint = state.logoutUrl;
                    break;
                  case MGMT_UI:
                    patchState.oAuthMgmtUiLoginEndpoint = state.loginUrl;
                    patchState.oAuthMgmtUiLogoutEndpoint = state.logoutUrl;
                    break;
                }

                updateDeploymentLoginEndpoints(currentState, patchState, nextStage, nextSubStage);
                break;
              }

              case FAILED: {
                State patchState = buildPatch(TaskState.TaskStage.FAILED, null, state.taskState.failure);
                patchState.taskState.failure = state.taskState.failure;
                TaskUtils.sendSelfPatch(BatchCreateManagementWorkflowService.this, patchState);
                break;
              }

              case CANCELLED: {
                State patchState = buildPatch(TaskState.TaskStage.CANCELLED, null);
                TaskUtils.sendSelfPatch(BatchCreateManagementWorkflowService.this, patchState);
                break;
              }
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            failTask(throwable);
          }
        });
  }

  private void updateDeploymentLoginEndpoints(State currentState,
                                              DeploymentService.State patchState,
                                              TaskState.TaskStage nextStage,
                                              TaskState.SubStage nextSubStage) {

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              } else {
                sendStageProgressPatch(nextStage, nextSubStage);
              }
            }));
  }

  /**
   * This method sends a progress patch depending of the taskState of the provided state.
   *
   * @param state    Supplies the state.
   * @param stage    Supplies the stage to progress to.
   * @param subStage Supplies the substate to progress to.
   */
  private void sendProgressPatch(
      com.vmware.xenon.common.TaskState state,
      TaskState.TaskStage stage,
      TaskState.SubStage subStage) {
    switch (state.stage) {
      case FINISHED:
        TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage));
        break;
      case CANCELLED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.CANCELLED, null));
        break;
      case FAILED:
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, state.failure));
        break;
    }
  }

  private void logScriptErrorAndFail(Integer result, File scriptLogFile) throws Throwable {
    ServiceUtils.logSevere(this, GENERATE_CERTIFICATE_SCRIPT_NAME + " returned " + result.toString());
    ServiceUtils.logSevere(this, "Script output: " + FileUtils.readFileToString(scriptLogFile));
    failTask(new IllegalStateException("Generating certificate failed with exit code " + result.toString()));
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", state.stage, state.subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, state.subStage));
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", taskStage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, subStage));
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

  private void failTask(Collection<Throwable> failures) {
    failures.forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, String lightwaveVmServiceLink) {
    return buildPatch(stage, subStage, lightwaveVmServiceLink, null);
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage, @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param errorResponse
   * @return
   */
  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      String lightwaveVmServiceLink,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.lightwaveVmServiceLink = lightwaveVmServiceLink;
    state.taskState.failure = errorResponse;
    return state;
  }


  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param subStage
   * @param errorResponse
   * @return
   */
  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }
}
