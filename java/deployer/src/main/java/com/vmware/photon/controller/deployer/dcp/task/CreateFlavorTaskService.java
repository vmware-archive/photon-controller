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
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.FlavorServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
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
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.ExceptionUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.dcp.util.MiscUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static java.lang.Math.max;

/**
 * This class implements a DCP micro-service which performs the task of creating flavorservice entities
 * for the VM.
 */
public class CreateFlavorTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single {@link CreateFlavorTaskService} instance.
   */
  public static class State extends ServiceDocument {
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    @Immutable
    @DefaultInteger(value = 0)
    public Integer controlFlags;

    @NotNull
    @Immutable
    public String vmServiceLink;

    @Immutable
    public Integer queryTaskInterval;
  }

  public CreateFlavorTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.queryTaskInterval) {
      startState.queryTaskInterval = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    startOperation.setBody(startState).complete();

    try {
      if (ControlFlags.isOperationProcessingDisabled(startState.controlFlags)) {
        ServiceUtils.logInfo(this, "Skipping start operation processing (disabled)");
      } else if (TaskState.TaskStage.STARTED == startState.taskState.stage) {
        sendStageProgressPatch(startState.taskState.stage);
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
        getVmEntity(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  private void validatePatchState(State currentState, State patchState) {
    ValidationUtils.validatePatch(currentState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(currentState.taskState, patchState.taskState);
  }

  private void getVmEntity(final State currentState) {

    ServiceUtils.logInfo(this, "Querying VmService state at %s", currentState.vmServiceLink);

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          VmService.State vmState = operation.getBody(VmService.State.class);
          getHostEntity(currentState, vmState);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setCompletion(completionHandler);
    sendRequest(getOperation);
  }

  private void getHostEntity(final State currentState, final VmService.State vmState) {

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
                    HostService.State hostState = completedOp.getBody(HostService.State.class);
                    if (hostState.metadata.containsKey(
                        HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE) &&
                        hostState.metadata.containsKey(
                            HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE) &&
                        hostState.metadata.containsKey(
                            HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE)) {
                      int finalCpuCount = Integer.parseInt(hostState.metadata.get(
                          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_CPU_COUNT_OVERWRITE));
                      int finalMemoryMb = Integer.parseInt(hostState.metadata.get(
                          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_MEMORY_GB_OVERWIRTE));
                      int finalDiskGb = Integer.parseInt(hostState.metadata.get(
                          HostService.State.METADATA_KEY_NAME_MANAGEMENT_VM_DISK_GB_OVERWRITE));

                      ServiceUtils.logInfo(this, "Use VM resource overwrite values: %d CPU, %dGB memory, %d GB disk",
                          finalCpuCount, finalMemoryMb, finalDiskGb);

                      createFlavorInApife(currentState, vmState, finalCpuCount, finalMemoryMb, finalDiskGb);
                    } else {
                      queryContainerEntityLinks(currentState, vmState, hostState);
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void queryContainerEntityLinks(final State currentState, final VmService.State vmState, final HostService
      .State hostState) {

    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
        .setTermMatchValue(currentState.vmServiceLink);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(vmServiceLinkClause);
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
              QueryTaskUtils.logQueryResults(CreateFlavorTaskService.this, documentLinks);
              getContainerEntities(currentState, vmState, hostState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getContainerEntities(
      final State currentState,
      final VmService.State vmState,
      final HostService.State hostState,
      Collection<String> documentLinks) {

    if (documentLinks.isEmpty()) {
      throw new DcpRuntimeException("Document links is empty");
    }

    OperationJoin
        .create(documentLinks.stream().map(documentLink -> Operation.createGet(this, documentLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            List<String> containerTemplateServiceLinks = ops.values().stream()
                .map(operation -> operation.getBody(ContainerService.State.class).containerTemplateServiceLink)
                .collect(Collectors.toList());
            getContainerTemplateEntities(currentState, vmState, hostState, containerTemplateServiceLinks);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void getContainerTemplateEntities(final State currentState,
                                            final VmService.State vmState,
                                            final HostService.State hostState,
                                            final List<String> containerTemplateServiceLinks) {

    if (containerTemplateServiceLinks.isEmpty()) {
      throw new DcpRuntimeException("Container template service links set is empty");
    }

    OperationJoin
        .create(containerTemplateServiceLinks.stream()
            .map(templateServiceLink -> Operation.createGet(this, templateServiceLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            int finalCpuCount = 0;
            int finalMemoryMb = 0;
            int finalDiskGb = 0;

            for (Operation getOperation : ops.values()) {
              ContainerTemplateService.State containerTemplateState = getOperation.getBody(
                  ContainerTemplateService.State.class);
              finalCpuCount = max(containerTemplateState.cpuCount, finalCpuCount);
              finalMemoryMb += containerTemplateState.memoryMb;
              finalDiskGb += containerTemplateState.diskGb;
            }

            // The ratio of the resource which can be consumed by the management vm to the total host resource
            float mgmtVmHostRatio = MiscUtils.getManagementVmHostRatio(hostState);

            // If host memory and cpu count is set, consume them entirely for the management vm.
            if (hostState.memoryMb != null) {
              finalMemoryMb = (int) (hostState.memoryMb * mgmtVmHostRatio);
            }
            if (hostState.cpuCount != null) {
              finalCpuCount = (int) (hostState.cpuCount * mgmtVmHostRatio);
            }
            createFlavorInApife(currentState, vmState, finalCpuCount, finalMemoryMb, finalDiskGb);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void createFlavorInApife(final State currentState, final VmService.State vmState, final int finalCpuCount,
     final int finalMemoryMb, final int finalDiskGb) throws IOException {

    ApiClient client = HostUtils.getApiClient(this);

    final AtomicInteger finishLatch = new AtomicInteger(2);
    final List<Throwable> failures = new ArrayList<>();
    FlavorCreateSpec vmFlavorCreateSpec = composeVmFlavorCreateSpec(vmState, finalCpuCount, finalMemoryMb);
    FlavorCreateSpec diskFlavorCreateSpec = composeDiskFlavorCreateSpec(vmState, finalDiskGb);

    FutureCallback<Task> callback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        processTask(currentState, result, finishLatch, failures);
      }

      @Override
      public void onFailure(Throwable throwable) {
        synchronized (failures) {
          failures.add(throwable);
        }

        if (0 == finishLatch.decrementAndGet()) {
          failTask(ExceptionUtils.createMultiException(failures));
        }
      }
    };

    client.getFlavorApi().createAsync(vmFlavorCreateSpec, callback);
    client.getFlavorApi().createAsync(diskFlavorCreateSpec, callback);
  }

  private FlavorCreateSpec composeVmFlavorCreateSpec(final VmService.State vmState, int finalCpuCount,
    int finalMemoryMb) {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(String.format("mgmt-vm-%s", vmState.name));
    spec.setKind("vm");

    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("vm", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem(String.format("vm.flavor.%s", spec.getName()), 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.cpu", finalCpuCount, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("vm.memory", finalMemoryMb, QuotaUnit.MB));
    cost.add(new QuotaLineItem("vm.cost", 1.0, QuotaUnit.COUNT));
    spec.setCost(cost);

    return spec;
  }

  private FlavorCreateSpec composeDiskFlavorCreateSpec(final VmService.State vmState, int diskGb) {
    FlavorCreateSpec spec = new FlavorCreateSpec();
    spec.setName(String.format("mgmt-vm-disk-%s", vmState.name));
    spec.setKind("ephemeral-disk");

    List<QuotaLineItem> cost = new ArrayList<>();
    cost.add(new QuotaLineItem("ephemeral-disk", 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem(String.format("ephemeral-disk.flavor.%s", spec.getName()), 1.0, QuotaUnit.COUNT));
    cost.add(new QuotaLineItem("ephemeral-disk.cost", 1.0, QuotaUnit.COUNT));
    spec.setCost(cost);

    return spec;
  }

  private void processTask(final State currentState, final Task task,
    final AtomicInteger finishLatch, final List<Throwable> failures) {

    FutureCallback<Task> pollTaskCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task task) {
        if (0 == finishLatch.decrementAndGet()) {
          if (failures.isEmpty()) {
            updateVmService(currentState, task.getEntity().getId());
          } else {
            failTask(ExceptionUtils.createMultiException(failures));
          }
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        synchronized (failures) {
          failures.add(throwable);
        }

        if (0 == finishLatch.decrementAndGet()) {
          failTask(ExceptionUtils.createMultiException(failures));
        }
      }
    };

    ApiUtils.pollTaskAsync(task,
        HostUtils.getApiClient(this),
        this,
        currentState.queryTaskInterval,
        pollTaskCallback);
  }

  private void updateVmService(final State currentState, final String flavorServiceId) {

    ServiceUtils.logInfo(this, "Updating VM service %s with flavor service id %s", currentState.vmServiceLink,
        flavorServiceId);

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.flavorServiceLink = FlavorServiceFactory.SELF_LINK + "/" + flavorServiceId;
    final Service service = this;

    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (throwable != null) {
          failTask(throwable);
          return;
        }

        try {
          TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FINISHED, null));
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    Operation postOperation = Operation
        .createPatch(UriUtils.buildUri(getHost(), currentState.vmServiceLink))
        .setBody(vmPatchState)
        .setCompletion(completionHandler);
    sendRequest(postOperation);
  }

  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void sendStageProgressPatch(TaskState.TaskStage taskStage) {
    ServiceUtils.logInfo(this, "Sending stage progress patch %s", taskStage.toString());
    TaskUtils.sendSelfPatch(this, buildPatch(taskStage, null));
  }

  private void failTask(Throwable t) {
    ServiceUtils.logSevere(this, t);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, t));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }

  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage taskStage, @Nullable Throwable t) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = taskStage;

    if (null != t) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return patchState;
  }
}
