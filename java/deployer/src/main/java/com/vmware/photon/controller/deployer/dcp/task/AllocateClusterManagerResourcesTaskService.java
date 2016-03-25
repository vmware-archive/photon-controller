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

import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.constant.ServicePortConstants;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implements a DCP workflow service to allocate Cluster Manager resources.
 */
public class AllocateClusterManagerResourcesTaskService extends StatefulService {

  private static final String MANAGEMENT_API_PROTOCOL = "http";

  /**
   * This class defines the state of a {@link AllocateClusterManagerResourcesTaskService} task.
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
      GET_LOAD_BALANCER_ADDRESS,
      CREATE_MASTER_VM_FLAVOR,
      CREATE_OTHER_VM_FLAVOR,
      CREATE_VM_DISK_FLAVOR
    }
  }

  /**
   * This class represents the document state associated with a
   * {@link AllocateClusterManagerResourcesTaskService} service.
   */
  @NoMigrationDuringUpgrade
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
     * This value represents the address of the Load Balancer.
     */
    @WriteOnce
    public String loadBalancerAddress;
  }

  public AllocateClusterManagerResourcesTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.GET_LOAD_BALANCER_ADDRESS;
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
        TaskUtils.sendSelfPatch(this, buildPatch(startState.taskState.stage, startState.taskState.subStage, null));
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());
    State currentState = getState(patchOperation);
    State patchState = patchOperation.getBody(State.class);
    validatePatchState(currentState, patchState);
    PatchUtils.patchState(currentState, patchState);
    validateStartState(currentState);
    patchOperation.complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(currentState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping patch handling (disabled)");
      } else if (TaskState.TaskStage.STARTED == currentState.taskState.stage) {
        processStateMachine(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processStateMachine(final State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case GET_LOAD_BALANCER_ADDRESS:
        queryForLoadBalancerContainerTemplate(currentState);
        break;
      case CREATE_MASTER_VM_FLAVOR:
        createFlavor(currentState, createMasterVmFlavor(), TaskState.SubStage.CREATE_OTHER_VM_FLAVOR);
        break;
      case CREATE_OTHER_VM_FLAVOR:
        createFlavor(currentState, createOtherVmFlavor(), TaskState.SubStage.CREATE_VM_DISK_FLAVOR);
        break;
      case CREATE_VM_DISK_FLAVOR:
        createFlavor(currentState, createVmDiskFlavor(), null);
        break;

      default:
        throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
    }
  }

  private void queryForLoadBalancerContainerTemplate(final State currentState) {
    // The uploading of the kubernetes cluster image file happens after
    // the deployment of management plane in the deployment workflow, which
    // means that the APIFE data has already been migrated. Therefore,
    // if we still target the APIFE endpoint on the installer OVA to upload
    // the image, the imageId will not show up in the APIFE on the management
    // plane.
    // The solution is to target LoadBalancer when uploading the kubernetes
    // cluster image, which in turn uploads the image via APIFE on the
    // management plane.

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerTemplateService.State.class));

    QueryTask.Query containerNameClause = new QueryTask.Query()
        .setTermPropertyName(ContainerTemplateService.State.FIELD_NAME_NAME)
        .setTermMatchValue(ContainersConfig.ContainerType.ManagementApi.name());

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerNameClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
            QueryTaskUtils.logQueryResults(AllocateClusterManagerResourcesTaskService.this, documentLinks);
            checkState(!documentLinks.isEmpty(), "Found 0 ManagementApi container template entity");
            queryForLoadBalancerContainer(documentLinks.iterator().next());
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryForLoadBalancerContainer(String containerTemplateLink) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query containerTemplateLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_CONTAINER_TEMPLATE_SERVICE_LINK)
        .setTermMatchValue(containerTemplateLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(containerTemplateLinkClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(queryTask)
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
            QueryTaskUtils.logQueryResults(AllocateClusterManagerResourcesTaskService.this, documentLinks);
            checkState(!documentLinks.isEmpty(), "Found 0 container entity");
            getContainerState(documentLinks.iterator().next());
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getContainerState(String containerLink) {
    Operation getOperation = Operation
        .createGet(this, containerLink)
        .forceRemote()
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          ContainerService.State containerState = operation.getBody(ContainerService.State.class);
          getVmState(containerState.vmServiceLink);
        });

    sendRequest(getOperation);
  }

  private void getVmState(String vmLink) {
    Operation getOperation = Operation
        .createGet(this, vmLink)
        .forceRemote()
        .setCompletion((Operation operation, Throwable throwable) -> {
          if (null != throwable) {
            failTask(throwable);
            return;
          }

          try {
            VmService.State vmState = operation.getBody(VmService.State.class);
            State patchState = buildPatch(TaskState.TaskStage.STARTED,
                TaskState.SubStage.CREATE_MASTER_VM_FLAVOR, null);
            patchState.loadBalancerAddress = new URL(String.format("%s://%s:%s",
                MANAGEMENT_API_PROTOCOL,
                vmState.ipAddress,
                ServicePortConstants.MANAGEMENT_API_PORT)).toString();
            TaskUtils.sendSelfPatch(AllocateClusterManagerResourcesTaskService.this, patchState);
          } catch (Throwable t) {
            failTask(t);
          }
        });

    sendRequest(getOperation);

  }

  private void createFlavor(final State currentState,
                            final FlavorCreateSpec spec,
                            final TaskState.SubStage nextSubStage) throws Throwable {

    HostUtils.getApiClient(this, currentState.loadBalancerAddress).getFlavorApi().createAsync(spec,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {

            processTask(currentState, result, nextSubStage != null ?
                buildPatch(TaskState.TaskStage.STARTED, nextSubStage, null) :
                buildPatch(TaskState.TaskStage.FINISHED, null, null));
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        });
  }

  private FlavorCreateSpec createMasterVmFlavor() throws Throwable {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(ClusterManagerConstants.MASTER_VM_FLAVOR);
    spec.setKind("vm");

    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("vm", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem(String.format("vm.flavor.%s", spec.getName()), 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.cpu", 4.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.memory", 8, QuotaUnit.GB));
    cost.add(new QuotaLineItem("vm.cost", 1.0, QuotaUnit.COUNT));
    spec.setCost(cost);

    return spec;
  }

  private FlavorCreateSpec createOtherVmFlavor() throws Throwable {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(ClusterManagerConstants.OTHER_VM_FLAVOR);
    spec.setKind("vm");

    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("vm", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem(String.format("vm.flavor.%s", spec.getName()), 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.cpu", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.memory", 4, QuotaUnit.GB));
    cost.add(new QuotaLineItem("vm.cost", 1.0, QuotaUnit.COUNT));
    spec.setCost(cost);

    return spec;
  }

  private FlavorCreateSpec createVmDiskFlavor() throws Throwable {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(ClusterManagerConstants.VM_DISK_FLAVOR);
    spec.setKind("ephemeral-disk");

    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("ephemeral-disk", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem(String.format("ephemeral-disk.flavor.%s", spec.getName()), 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("ephemeral-disk.cost", 1.0, QuotaUnit.COUNT));
    spec.setCost(cost);

    return spec;
  }

  private void processTask(final State currentState, final Task task, final State patchState) {
    final Service service = this;

    ApiUtils.pollTaskAsync(
        task,
        HostUtils.getApiClient(this, currentState.loadBalancerAddress),
        this,
        HostUtils.getDeployerContext(this).getTaskPollDelay(),
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            TaskUtils.sendSelfPatch(service, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  private void validateStartState(State startState) {
    ValidationUtils.validateState(startState);
    ValidationUtils.validateTaskStage(startState.taskState);
    validateSubStage(startState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void validateSubStage(State state) {

    if (state.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(state.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (state.taskState.subStage) {
        case GET_LOAD_BALANCER_ADDRESS:
        case CREATE_MASTER_VM_FLAVOR:
        case CREATE_OTHER_VM_FLAVOR:
        case CREATE_VM_DISK_FLAVOR:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + state.taskState.subStage.toString());
      }
    } else {
      checkState(state.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }
  }

  protected State buildPatch(
      TaskState.TaskStage stage,
      TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse failure) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = failure;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    ServiceErrorResponse failure = Utils.toServiceErrorResponse(e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failure));
  }
}
