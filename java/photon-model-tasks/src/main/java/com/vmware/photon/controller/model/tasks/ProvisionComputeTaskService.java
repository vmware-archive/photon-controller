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

package com.vmware.photon.controller.model.tasks;

import com.vmware.photon.controller.model.adapterapi.ComputeBootRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.BootDevice;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.apache.commons.validator.routines.InetAddressValidator;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Task implementing the provision compute resource work flow. Utilizes sub tasks and services
 * provided in the compute host description to perform various sub stages.
 * <p>
 * This is service is not replicated or partitioned since its created by a higher level task, which
 * is partitioned. So this task executes in isolation, per node. It does however talk to replicated
 * DCP services, so it sets the operation.setTargetReplicated(true) to guard against services not
 * yet available on the current node.
 */
public class ProvisionComputeTaskService extends StatefulService {

  /**
   * Represent state of a provision task.
   */
  public static class ProvisionComputeTaskState extends ServiceDocument {

    public static final long DEFAULT_EXPIRATION_MICROS = TimeUnit.HOURS.toMicros(1);
    public static final String FIELD_NAME_PARENT_TASK_LINK = "parentTaskLink";

    public TaskState taskInfo = new TaskState();

    /**
     * SubStage.
     */
    public enum SubStage {
      CREATING_HOST,
      BOOTING_FROM_NETWORK,
      BOOTING_FROM_CDROM,
      BOOTING_FROM_ANY,
      VALIDATE_COMPUTE_HOST,
      DONE,
      FAILED
    }

    /**
     * Task SubStage.
     */
    public ProvisionComputeTaskState.SubStage taskSubStage;

    /**
     * URI reference to compute instance.
     */
    public String computeLink;

    /**
     * Optional, set by the task if not specified by the client, by querying the compute host.
     */
    public URI instanceAdapterReference;

    /**
     * Optional, set by the task if not specified by the client, by querying the compute host.
     */
    public URI powerAdapterReference;

    /**
     * Optional, set by the task if not specified by the client, by querying the compute host.
     */
    public URI bootAdapterReference;

    /**
     * Link that initiated this task.
     */
    public String parentTaskLink;

    /**
     * Value indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    public boolean isMockRequest;

    /**
     * A list of tenant links which can access this task.
     */
    public List<String> tenantLinks;
  }

  public ProvisionComputeTaskService() {
    super(ProvisionComputeTaskState.class);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startPost) {
    try {
      ProvisionComputeTaskState state =
          startPost.getBody(ProvisionComputeTaskState.class);
      validateState(state);

      // we need to retrieve and cache the power and boot services we need to actuate
      URI computeHost = buildComputeHostUri(state);
      sendRequest(Operation
          .createGet(computeHost)
          .setTargetReplicated(true)
          .setCompletion((o, e) -> {
            validateComputeHostAndStart(startPost, o, e, state);
          }));
    } catch (Throwable e) {
      logSevere(e);
      startPost.fail(e);
    }
  }

  private void validateComputeHostAndStart(Operation startPost,
                                           Operation get, Throwable e,
                                           ProvisionComputeTaskState state) {
    if (e != null) {
      logWarning("Failure retrieving host state (%s): %s", get.getUri(), e.toString());
      startPost.complete();
      failTask(e);
      return;
    }

    ComputeService.ComputeStateWithDescription hostState = get
        .getBody(ComputeService.ComputeStateWithDescription.class);
    if (hostState.description == null) {
      hostState.description = new ComputeDescription();
    }

    if (hostState.description.cpuCount <= 0) {
      hostState.description.cpuCount = 1;
    }

    if (hostState.description.totalMemoryBytes <= 0) {
      hostState.description.totalMemoryBytes = Integer.MAX_VALUE / 4L;
    }

    state.bootAdapterReference = hostState.description.bootAdapterReference;
    state.powerAdapterReference = hostState.description.powerAdapterReference;
    state.instanceAdapterReference = hostState.description.instanceAdapterReference;

    // we can complete start operation now, it will index and cache the
    // update state
    startPost.complete();

    if (state.bootAdapterReference == null ^ state.powerAdapterReference == null) {
      failTask(new IllegalArgumentException(
          "computeHost power and boot need to both be set or empty"));
      return;
    }

    if (state.taskSubStage == ProvisionComputeTaskState.SubStage.CREATING_HOST
        && state.instanceAdapterReference == null) {
      failTask(new IllegalArgumentException(
          "computeHost does not have create service specified"));
      return;
    }

    // now we are ready to start our self-running state machine
    sendSelfPatch(TaskStage.STARTED, state.taskSubStage, null);
  }

  private void sendSelfPatch(TaskStage newStage, ProvisionComputeTaskState.SubStage newSubStage, Throwable ex) {
    ProvisionComputeTaskState patchBody = new ProvisionComputeTaskState();
    patchBody.taskInfo = new TaskState();
    patchBody.taskInfo.stage = newStage;
    patchBody.taskSubStage = newSubStage;
    if (ex != null) {
      patchBody.taskInfo.failure = Utils.toServiceErrorResponse(ex);
    }
    Operation patch = Operation
        .createPatch(getUri())
        .setBody(patchBody)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                logWarning("Self patch failed: %s",
                    com.vmware.xenon.common.Utils.toString(e));
              }
            });
    sendRequest(patch);
  }

  @Override
  public void handlePatch(Operation patch) {
    ProvisionComputeTaskState patchBody = patch
        .getBody(ProvisionComputeTaskState.class);
    ProvisionComputeTaskState currentState = getState(patch);

    // this validates AND transitions the stage to the next state by using the patchBody.
    if (validateStageTransition(patch, patchBody, currentState)) {
      return;
    }

    handleStagePatch(patch, currentState);
  }

  private void handleStagePatch(Operation patch, ProvisionComputeTaskState currentState) {
    // Complete the self patch eagerly, after we clone state (since state is not "owned"
    // by the service handler once the operation is complete).
    patch.complete();

    switch (currentState.taskInfo.stage) {
      case CREATED:
        break;
      case STARTED:
        processNextSubStage(currentState);
        break;
      case CANCELLED:
        break;
      case FAILED:
        logWarning("Task failed with %s", Utils.toJsonHtml(currentState.taskInfo.failure));
        notifyParentTask(currentState);
        break;
      case FINISHED:
        notifyParentTask(currentState);
        break;
      default:
        break;
    }
  }

  private void notifyParentTask(ProvisionComputeTaskState clonedState) {
    if (clonedState.parentTaskLink == null) {
      return;
    }

    logFine("Patching parent task %s", clonedState.parentTaskLink);
    ProvisionComputeTaskState parentPatchBody = new ProvisionComputeTaskState();
    parentPatchBody.taskInfo = clonedState.taskInfo;
    sendRequest(Operation.createPatch(this, clonedState.parentTaskLink)
        .setBody(parentPatchBody));
  }

  private void processNextSubStage(ProvisionComputeTaskState updatedState) {
    ProvisionComputeTaskState.SubStage newStage = updatedState.taskSubStage;

    switch (newStage) {
      case CREATING_HOST:
        ProvisionComputeTaskState.SubStage nextStageOnSuccess = ProvisionComputeTaskState.SubStage.BOOTING_FROM_ANY;

        // containers and hosted instances don't have a boot operation.
        // They are simply instantiated. Go directly to validation in that case.
        if (updatedState.bootAdapterReference == null) {
          nextStageOnSuccess = ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST;
        }

        // the first reboot needs to be from the network, and the bare metal services
        // will provide the image reference (retrieved from the computeLink)
        doSubStageCreateHost(updatedState, nextStageOnSuccess);
        return;
      case BOOTING_FROM_NETWORK:
        doSubStageBootHost(updatedState, BootDevice.NETWORK, ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST);
        return;
      case BOOTING_FROM_CDROM:
        doSubStageBootHost(updatedState, BootDevice.CDROM, ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST);
        return;
      case BOOTING_FROM_ANY:
        BootDevice[] bootDevices = new BootDevice[]{
            BootDevice.DISK,
            BootDevice.CDROM,
            BootDevice.NETWORK
        };
        doSubStageBootHost(updatedState, bootDevices, ProvisionComputeTaskState.SubStage.VALIDATE_COMPUTE_HOST);
        return;
      case VALIDATE_COMPUTE_HOST:
        doSubStageValidateComputeHostState(updatedState);
        return;
      case DONE:
        sendSelfPatch(TaskStage.FINISHED, ProvisionComputeTaskState.SubStage.DONE, null);
        break;
      default:
        break;
    }
  }

  private void doSubStageCreateHost(ProvisionComputeTaskState updatedState,
                                    ProvisionComputeTaskState.SubStage nextStage) {
    CompletionHandler c = (o, e) -> {
      if (e != null) {
        failTask(e);
        return;
      }

      ComputeInstanceRequest cr = new ComputeInstanceRequest();
      cr.computeReference = UriUtils.buildUri(getHost(), updatedState.computeLink);
      cr.requestType = InstanceRequestType.CREATE;
      // the first reboot needs to be from the network, and the bare metal services
      // will provide the image reference (retrieved from the computeLink)

      cr.provisioningTaskReference = o.getUri();
      cr.isMockRequest = updatedState.isMockRequest;
      sendHostServiceRequest(cr, updatedState.instanceAdapterReference);
    };

    // after setting boot order and rebooting, we want the sub
    // task to patch us, the main task, to the "next" state
    createSubTask(c, nextStage, updatedState);
  }


  private URI buildComputeHostUri(ProvisionComputeTaskState updatedState) {
    URI computeHost = UriUtils.buildUri(getHost(), updatedState.computeLink);
    computeHost = ComputeService.ComputeStateWithDescription.buildUri(computeHost);
    return computeHost;
  }

  private void doSubStageBootHost(ProvisionComputeTaskState updatedState,
                                  BootDevice bootDevice,
                                  ProvisionComputeTaskState.SubStage nextStage) {
    doSubStageBootHost(updatedState, new BootDevice[]{bootDevice}, nextStage);
  }

  private void doSubStageBootHost(ProvisionComputeTaskState updatedState,
                                  BootDevice[] bootDevices,
                                  ProvisionComputeTaskState.SubStage nextStage) {
    CompletionHandler c = (o, e) -> {
      if (e != null) {
        failTask(e);
        return;
      }

      ComputeBootRequest br = new ComputeBootRequest();
      br.computeReference = UriUtils.buildUri(getHost(), updatedState.computeLink);
      for (BootDevice bootDevice : bootDevices) {
        br.bootDeviceOrder.add(bootDevice);
      }
      br.provisioningTaskReference = o.getUri();
      br.isMockRequest = updatedState.isMockRequest;
      sendHostServiceRequest(br, updatedState.bootAdapterReference);
    };

    // After setting boot order and rebooting, we want the sub-task
    // to patch the main task to the "next" state.
    createSubTask(c, nextStage, updatedState);
  }

  private void doSubStageValidateComputeHostState(ProvisionComputeTaskState updatedState) {
    // we need to make sure the boot/power services not only properly patched
    // our sub tasks but also patched the compute host with the IP address and other
    // runtime data

    sendRequest(Operation
        .createGet(this, updatedState.computeLink)
        .setTargetReplicated(true)
        .setCompletion((o, e) -> {
          if (e != null) {
            // the compute host is co-located so it is unexpected that it failed the GET
            logWarning("GET to %s failed:", o.getUri(), Utils.toString(e));
            failTask(e);
            return;
          }
          ComputeService.ComputeState chs = o.getBody(ComputeService.ComputeState.class);
          try {
            if (!updatedState.isMockRequest) {
              InetAddressValidator.getInstance().isValidInet4Address(chs.address);
            }
            sendSelfPatch(TaskStage.FINISHED, ProvisionComputeTaskState.SubStage.DONE, null);
          } catch (Throwable ex) {
            failTask(ex);
          }
        }));
  }

  public void createSubTask(CompletionHandler c, ProvisionComputeTaskState.SubStage nextStage,
                            ProvisionComputeTaskState currentState) {
    ProvisionComputeTaskState patchBody = new ProvisionComputeTaskState();
    patchBody.taskInfo.stage = TaskStage.STARTED;
    patchBody.taskSubStage = nextStage;

    ComputeSubTaskService.ComputeSubTaskState subTaskInitState = new ComputeSubTaskService.ComputeSubTaskState();
    subTaskInitState.parentPatchBody = Utils.toJson(patchBody);
    subTaskInitState.errorThreshold = 0;
    subTaskInitState.parentTaskLink = getSelfLink();
    subTaskInitState.tenantLinks = currentState.tenantLinks;
    Operation startPost = Operation
        .createPost(this, UUID.randomUUID().toString())
        .setBody(subTaskInitState)
        .setCompletion(c);
    getHost().startService(startPost, new ComputeSubTaskService());
  }

  private void sendHostServiceRequest(Object body, URI adapterReference) {
    sendRequest(Operation.createPatch(adapterReference)
        .setBody(body)
        .setCompletion((o, e) -> {
          if (e != null) {
            failTask(e);
            return;
          }
        }));
  }

  public boolean validateStageTransition(Operation patch,
                                         ProvisionComputeTaskState patchBody,
                                         ProvisionComputeTaskState currentState) {

    if (patchBody.taskInfo != null && patchBody.taskInfo.failure != null) {
      logWarning("Task failed: %s", Utils.toJson(patchBody.taskInfo.failure));
      currentState.taskInfo.failure = patchBody.taskInfo.failure;
      if (patchBody.taskSubStage == null) {
        patchBody.taskSubStage = currentState.taskSubStage;
      }
    } else {
      if (patchBody.taskInfo == null || patchBody.taskInfo.stage == null) {
        patch.fail(new IllegalArgumentException("taskInfo and taskInfo.stage are required"));
        return true;
      }

      // Current state always has a non-null taskSubStage, per validateState.
      if (currentState.taskSubStage == null) {
        patch.fail(new IllegalArgumentException("taskSubStage is required"));
        return true;
      }

      // Patched state must have a non-null taskSubStage,
      // because we're moving from one stage to the next.
      if (patchBody.taskSubStage == null) {
        patch.fail(new IllegalArgumentException("taskSubStage is required"));
        return true;
      }

      if (currentState.taskSubStage.ordinal() > patchBody.taskSubStage.ordinal()) {
        logWarning("Attempt to move progress backwards, not allowed");
        patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED).complete();
        return true;
      }
    }

    logFine("Current: %s(%s). New: %s(%s)",
        currentState.taskInfo.stage,
        currentState.taskSubStage,
        patchBody.taskInfo.stage,
        patchBody.taskSubStage);

    // update current stage to new stage
    currentState.taskInfo.stage = patchBody.taskInfo.stage;
    adjustStat(patchBody.taskInfo.stage.toString(), 1);

    // update sub stage
    currentState.taskSubStage = patchBody.taskSubStage;
    adjustStat(currentState.taskSubStage.toString(), 1);

    return false;
  }

  private void failTask(Throwable e) {
    logWarning("Self patching to FAILED, task failure: %s", e.toString());
    sendSelfPatch(TaskStage.FAILED, ProvisionComputeTaskState.SubStage.FAILED, e);
  }

  public void validateState(ProvisionComputeTaskState state) {
    if (state.computeLink == null) {
      throw new IllegalArgumentException("computeLink is required");
    }

    state.taskInfo = new TaskState();
    state.taskInfo.stage = TaskStage.CREATED;

    if (state.taskSubStage == null) {
      throw new IllegalArgumentException("taskSubStage is required");
    }

    if (state.documentExpirationTimeMicros == 0) {
      state.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
          + ProvisionComputeTaskState.DEFAULT_EXPIRATION_MICROS;
    }
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
