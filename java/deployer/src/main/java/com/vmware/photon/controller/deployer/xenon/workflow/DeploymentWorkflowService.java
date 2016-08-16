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

import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DhcpSubnetService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.SubnetAllocatorService;
import com.vmware.photon.controller.common.IpHelper;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.DeploymentMigrationInformation;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.xenon.ContainersConfig;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateClusterManagerResourcesTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.AllocateClusterManagerResourcesTaskService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskFactoryService;
import com.vmware.photon.controller.deployer.xenon.task.CopyStateTaskService;
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
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.commons.net.util.SubnetUtils;
import org.eclipse.jetty.util.BlockingArrayQueue;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class implements a Xenon service representing the end-to-end deployment workflow.
 */
public class DeploymentWorkflowService extends StatefulService {

  private static boolean inUnitTests = false;

  /**
   * This class defines the state of a {@link DeploymentWorkflowService} task.
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
      PROVISION_MANAGEMENT_HOSTS,
      CREATE_MANAGEMENT_PLANE,
      PROVISION_ALL_HOSTS,
      ALLOCATE_CM_RESOURCES,
      CREATE_SUBNET_ALLOCATOR,
      CREATE_DHCP_SUBNET,
      MIGRATE_DEPLOYMENT_DATA,
      SET_DEPLOYMENT_STATE
    }
  }

  /**
   * This class defines the document state associated with a single
   * {@link DeploymentWorkflowService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * This value represents the file name of the management VM image.
     */
    @Immutable
    public String managementVmImageFile;

    /**
     * This value represents the state of the current task.
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
     * This value represents the override value to use for the pollInterval field
     * in child tasks.
     */
    @DefaultInteger(value = 3000)
    @Immutable
    public Integer childPollInterval;

    /**
     * This value represents the link to the {@link DeploymentService.State} entity.
     */
    @WriteOnce
    public String deploymentServiceLink;

    /**
     * This value represents the desired state of the deployed management plane.
     */
    public DeploymentState desiredState;
  }

  public DeploymentWorkflowService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    // It is intentional to leave out the OWNER_SELECTED and REPLICATION options, because
    // this task is only intended to run on the initial deployer, and should not be
    // replicated among the new deployers in the management plane we bring up.
  }

  public static void setInUnitTests(boolean inUnitTests) {
    DeploymentWorkflowService.inUnitTests = inUnitTests;
  }

  /**
   * This method is called when a start operation is performed for the current
   * service instance.
   *
   * @param start Supplies the start operation object.
   */
  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (null == startState.desiredState) {
      startState.desiredState = DeploymentState.PAUSED;
    }

    if (null == startState.managementVmImageFile) {
      startState.managementVmImageFile = DeployerConfig.getManagementImageFile();
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
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.PROVISION_MANAGEMENT_HOSTS;
      startState.taskSubStates.set(0, TaskState.TaskStage.STARTED);
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
        ServiceUtils.logInfo(this, "Skipping patch operation processing (disabled)");
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
  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
    validateTaskSubStage(currentState.taskState);

    if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
      switch (currentState.taskState.subStage) {
        case PROVISION_MANAGEMENT_HOSTS:
        case CREATE_MANAGEMENT_PLANE:
        case PROVISION_ALL_HOSTS:
        case ALLOCATE_CM_RESOURCES:
        case CREATE_SUBNET_ALLOCATOR:
        case CREATE_DHCP_SUBNET:
        case MIGRATE_DEPLOYMENT_DATA:
        case SET_DEPLOYMENT_STATE:
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

  /**
   * This method checks a patch object for validity against a document state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    validateTaskSubStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);

    if (null != startState.taskState.subStage && null != patchState.taskState.subStage) {
      checkState(patchState.taskState.subStage.ordinal() >= startState.taskState.subStage.ordinal());
    }
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage
        || patchState.taskState.subStage != startState.taskState.subStage) {
      ServiceUtils.logInfo(this, "Moving from %s:%s to stage %s:%s",
          startState.taskState.stage, startState.taskState.subStage,
          patchState.taskState.stage, patchState.taskState.subStage);

      switch (patchState.taskState.stage) {
        case STARTED:
          startState.taskSubStates.set(patchState.taskState.subStage.ordinal(), TaskState.TaskStage.STARTED);
          // fall through
        case FINISHED:
          startState.taskSubStates.set(startState.taskState.subStage.ordinal(), TaskState.TaskStage.FINISHED);
          break;
        case FAILED:
          startState.taskSubStates.set(startState.taskState.subStage.ordinal(), TaskState.TaskStage.FAILED);
          break;
        case CANCELLED:
          startState.taskSubStates.set(startState.taskState.subStage.ordinal(), TaskState.TaskStage.CANCELLED);
          break;
      }
    }

    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(final State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case PROVISION_MANAGEMENT_HOSTS:
        addHosts(currentState);
        break;
      case CREATE_MANAGEMENT_PLANE:
        batchCreateManagementPlane(currentState);
        break;
      case PROVISION_ALL_HOSTS:
        processProvisionAllHosts(currentState);
        break;
      case ALLOCATE_CM_RESOURCES:
        allocateClusterManagerResources(currentState);
        break;
      case CREATE_SUBNET_ALLOCATOR:
        createSubnetAllocator(currentState);
        break;
      case CREATE_DHCP_SUBNET:
        createDhcpSubnet(currentState);
        break;
      case MIGRATE_DEPLOYMENT_DATA:
        migrateData(currentState);
        break;
      case SET_DEPLOYMENT_STATE:
        setDesiredDeploymentState(currentState);
        break;
    }
  }

  private void addHosts(final State currentState) {
    ServiceUtils.logInfo(this, "Adding management hosts, provisioning cloud hosts..");
    final Service service = this;

    FutureCallback<AddManagementHostWorkflowService.State> callback =
        new FutureCallback<AddManagementHostWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable AddManagementHostWorkflowService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(
                    TaskState.TaskStage.STARTED,
                    TaskState.SubStage.CREATE_MANAGEMENT_PLANE,
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

    AddManagementHostWorkflowService.State startState =
        new AddManagementHostWorkflowService.State();
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.childPollInterval = currentState.childPollInterval;
    startState.isNewDeployment = true;

    TaskUtils.startTaskAsync(
        this,
        AddManagementHostWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        AddManagementHostWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void batchCreateManagementPlane(final State currentState) {
    ServiceUtils.logInfo(this, "Bulk provisioning management plane");
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
                    batchCreateManagementPlane(currentState, deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            )
    );
  }

  private void batchCreateManagementPlane(final State currentState, DeploymentService.State deploymentService) {

    final Service service = this;

    FutureCallback<BatchCreateManagementWorkflowService.State> callback =
        new FutureCallback<BatchCreateManagementWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable BatchCreateManagementWorkflowService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(
                    TaskState.TaskStage.STARTED, TaskState.SubStage.PROVISION_ALL_HOSTS, null));
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

    BatchCreateManagementWorkflowService.State startState = new BatchCreateManagementWorkflowService.State();

    startState.imageFile = currentState.managementVmImageFile;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;
    startState.isAuthEnabled = deploymentService.oAuthEnabled;
    startState.taskPollDelay = currentState.taskPollDelay;
    startState.ntpEndpoint = deploymentService.ntpEndpoint;
    startState.childPollInterval = currentState.childPollInterval;
    startState.oAuthServerAddress = deploymentService.oAuthServerAddress;
    startState.oAuthTenantName = deploymentService.oAuthTenantName;

    TaskUtils.startTaskAsync(
        this,
        BatchCreateManagementWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BatchCreateManagementWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void allocateClusterManagerResources(final State currentState) {
    ServiceUtils.logInfo(this, "Allocating ClusterManager resources");
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
                    allocateClusterManagerResources(currentState, deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            )
    );
  }

  private void allocateClusterManagerResources(final State currentState,
                                               DeploymentService.State deploymentState) throws Throwable {

    final Service service = this;

    FutureCallback<AllocateClusterManagerResourcesTaskService.State> callback =
        new FutureCallback<AllocateClusterManagerResourcesTaskService.State>() {
          @Override
          public void onSuccess(@Nullable AllocateClusterManagerResourcesTaskService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(
                    TaskState.TaskStage.STARTED,
                    TaskState.SubStage.CREATE_SUBNET_ALLOCATOR,
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

    AllocateClusterManagerResourcesTaskService.State startState =
        new AllocateClusterManagerResourcesTaskService.State();
    if (deploymentState.oAuthEnabled) {
      startState.apifeProtocol = "https";
    }

    TaskUtils.startTaskAsync(
        this,
        AllocateClusterManagerResourcesTaskFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        AllocateClusterManagerResourcesTaskService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void createSubnetAllocator(State currentState) {
    ServiceUtils.logInfo(this, "Creating Subnet Allocator if sdn is enabled");
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
                    createSubnetAllocator(deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            )
    );
  }

  private void createSubnetAllocator(DeploymentService.State deploymentState) {
    if (!deploymentState.sdnEnabled || deploymentState.ipRange == null || deploymentState.ipRange.isEmpty()) {
      TaskUtils.sendSelfPatch(this, buildPatch(
          TaskState.TaskStage.STARTED,
          TaskState.SubStage.CREATE_DHCP_SUBNET,
          null));
      return;
    }

    SubnetAllocatorService.State subnetAllocatorServiceState = new SubnetAllocatorService.State();
    subnetAllocatorServiceState.rootCidr = deploymentState.ipRange;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(SubnetAllocatorService.SINGLETON_LINK)
        .setBody(subnetAllocatorServiceState)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                TaskUtils.sendSelfPatch(this, buildPatch(
                    TaskState.TaskStage.STARTED,
                    TaskState.SubStage.CREATE_DHCP_SUBNET,
                    null));
              }
            }
        ));
  }

  private void createDhcpSubnet(State currentState) {
    ServiceUtils.logInfo(this, "Creating Dhcp Subnet if sdn is enabled");
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
                    createDhcpSubnet(deploymentState);
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            )
    );
  }

  private void createDhcpSubnet(DeploymentService.State deploymentState) {
    if (!deploymentState.sdnEnabled ||
        deploymentState.floatingIpRange == null ||
        deploymentState.floatingIpRange.isEmpty()) {
      TaskUtils.sendSelfPatch(this, buildPatch(
          TaskState.TaskStage.STARTED,
          TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
          null));
      return;
    }

    DhcpSubnetService.State dhcpSubnetServiceState = createDhcpSubnetServiceState(deploymentState.floatingIpRange);
    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPost(DhcpSubnetService.FLOATING_IP_SUBNET_SINGLETON_LINK)
        .setBody(dhcpSubnetServiceState)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                TaskUtils.sendSelfPatch(this, buildPatch(
                    TaskState.TaskStage.STARTED,
                    TaskState.SubStage.MIGRATE_DEPLOYMENT_DATA,
                    null));
              }
            }
        ));
  }

  private static DhcpSubnetService.State createDhcpSubnetServiceState(String cidr) {
    SubnetUtils subnetUtils = new SubnetUtils(cidr);
    SubnetUtils.SubnetInfo subnetInfo = subnetUtils.getInfo();
    InetAddress lowIpAddress = InetAddresses.forString(subnetInfo.getLowAddress());
    InetAddress highIpAddress = InetAddresses.forString(subnetInfo.getHighAddress());

    DhcpSubnetService.State state = new DhcpSubnetService.State();
    state.cidr = cidr;
    state.lowIp = IpHelper.ipToLong((Inet4Address) lowIpAddress);
    state.highIp = IpHelper.ipToLong((Inet4Address) highIpAddress);

    return state;
  }

  private void migrateData(State currentState) {
    ServiceUtils.logInfo(this, "Migrating deployment data");

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
                  String destinationProtocol = "http";
                  if (deploymentState.oAuthEnabled && !DeploymentWorkflowService.inUnitTests) {
                    destinationProtocol = "https";
                  }
                  migrateData(currentState, destinationProtocol);
                }
            )
    );
  }

  private void migrateData(State currentState, String destinationProtocol) {
    // get all container
    Operation queryContainersOp = buildBroadcastKindQuery(ContainerService.State.class);
    // get all container templates
    Operation queryTemplatesOp = buildBroadcastKindQuery(ContainerTemplateService.State.class);
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

          migrateData(currentState, photonControllerCoreVms, destinationProtocol);
      })
      .sendWith(this);
  }

  private void migrateData(State currentState,
                           List<VmService.State> managementVms,
                           final String destinationProtocol) {
    Collection<DeploymentMigrationInformation> migrationInformation
        = HostUtils.getDeployerContext(this).getDeploymentMigrationInformation();

    final AtomicInteger latch = new AtomicInteger(migrationInformation.size());
    final List<Throwable> errors = new BlockingArrayQueue<>();

    Set<InetSocketAddress> sourceServers = new HashSet<>();
    Set<InetSocketAddress> destinationServers = new HashSet<>();

    for (DeploymentMigrationInformation entry : migrationInformation) {
      if (sourceServers.size() == 0) {
        sourceServers.add(new InetSocketAddress(getHost().getPreferredAddress(), getHost().getPort()));
        for (VmService.State vm : managementVms) {
          destinationServers.add(new InetSocketAddress(vm.ipAddress, vm.deployerXenonPort));
        }
      }

      String factory = entry.factoryServicePath;
      if (!factory.endsWith("/")) {
        factory += "/";
      }

      CopyStateTaskService.State startState = MiscUtils.createCopyStateStartState(
          sourceServers,
          destinationServers,
          factory,
          factory);
      startState.destinationProtocol = destinationProtocol;

      TaskUtils.startTaskAsync(
          this,
          CopyStateTaskFactoryService.SELF_LINK,
          startState,
          state -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          CopyStateTaskService.State.class,
          currentState.taskPollDelay,
          new FutureCallback<CopyStateTaskService.State>() {

            @Override
            public void onSuccess(@Nullable CopyStateTaskService.State result) {
              switch (result.taskState.stage) {
                case FINISHED:
                  break;
                case FAILED:
                case CANCELLED:
                  errors.add(new Throwable(
                      "service: "
                          + result.documentSelfLink
                          + " did not finish. "
                          + result.taskState.failure.message));
                  break;
                default:
                  errors.add(new Throwable(
                      "service: "
                          + result.documentSelfLink
                          + " ended in unexpected stage "
                          + result.taskState.stage.name()));
                  break;
              }

              if (latch.decrementAndGet() == 0) {
                if (!errors.isEmpty()) {
                  failTask(errors);
                } else {
                  updateDeploymentServiceState(destinationServers, currentState, destinationProtocol);
                }
              }
            }

            @Override
            public void onFailure(Throwable t) {
              errors.add(t);
              if (latch.decrementAndGet() == 0) {
                failTask(errors);
              }
            }
          }
      );
    }
  }

  private void updateDeploymentServiceState(Set<InetSocketAddress> remoteCloudStoreServers,
                                            State currentState,
                                            String destinationProtocol) {

    DeploymentService.State deploymentServiceState = new DeploymentService.State();
    deploymentServiceState.state = DeploymentState.READY;

    try {
      sendRequest(Operation
          .createPatch(ServiceUtils.createUriFromServerSet(remoteCloudStoreServers,
              currentState.deploymentServiceLink, destinationProtocol))
          .addRequestHeader(Operation.REPLICATION_QUORUM_HEADER, Operation.REPLICATION_QUORUM_HEADER_VALUE_ALL)
          .setBody(deploymentServiceState)
          .setCompletion(
              (completedOp, failure) -> {
                if (null != failure) {
                  failTask(failure);
                } else {
                  TaskUtils.sendSelfPatch(
                      this,
                      buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.SET_DEPLOYMENT_STATE, null));
                }
              }
          ));
    } catch (URISyntaxException e) {
      failTask(e);
    }
  }

  private void processProvisionAllHosts(final State currentState) throws Throwable {

    final Service service = this;

    FutureCallback<BulkProvisionHostsWorkflowService.State> callback =
        new FutureCallback<BulkProvisionHostsWorkflowService.State>() {
          @Override
          public void onSuccess(@Nullable BulkProvisionHostsWorkflowService.State result) {
            switch (result.taskState.stage) {
              case FINISHED:
                TaskUtils.sendSelfPatch(service, buildPatch(
                    TaskState.TaskStage.STARTED, TaskState.SubStage.ALLOCATE_CM_RESOURCES, null));
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

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createGet(currentState.deploymentServiceLink)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  failTask(e);
                } else {
                  DeploymentService.State deploymentState = o.getBody(DeploymentService.State.class);
                  startState.createCert = deploymentState.oAuthEnabled;
                }
              } catch (Throwable t) {
                failTask(t);
              }
            }));

    startState.usageTag = UsageTag.CLOUD.name();

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(HostService.State.class));
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    startState.querySpecification = querySpecification;

    TaskUtils.startTaskAsync(
        this,
        BulkProvisionHostsWorkflowFactoryService.SELF_LINK,
        startState,
        (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
        BulkProvisionHostsWorkflowService.State.class,
        currentState.taskPollDelay,
        callback);
  }

  private void setDesiredDeploymentState(State currentState) {
    DeploymentService.State deployment = new DeploymentService.State();
    deployment.state = currentState.desiredState;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
        .addRequestHeader(Operation.REPLICATION_QUORUM_HEADER, Operation.REPLICATION_QUORUM_HEADER_VALUE_ALL)
        .setBody(deployment)
        .setCompletion(
            (completedOp, failure) -> {
              if (null != failure) {
                failTask(failure);
              } else {
                TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null, null));
              }
            }
        ));
  }

  private Operation buildBroadcastKindQuery(Class<? extends ServiceDocument> type) {
    QueryTask.Query query = QueryTask.Query.Builder.create().addKindFieldClause(type)
        .build();
    return Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask.Builder
            .createDirectTask()
            .addOptions(EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
            .setQuery(query)
            .build());
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

  private void failTask(Collection<Throwable> failures) {
    failures.forEach((throwable) -> ServiceUtils.logSevere(this, throwable));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.iterator().next()));
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
