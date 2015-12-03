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

package com.vmware.photon.controller.model.adapters.dockeradapter;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.dockeradapter.client.DockerClient;
import com.vmware.photon.controller.model.adapters.dockeradapter.client.DockerClientFactory;
import com.vmware.photon.controller.model.adapters.dockeradapter.client.DockerClientFactoryImpl;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.tasks.ComputeSubTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import java.util.function.Consumer;

/**
 * Service that creates and deletes Docker instance.
 */
public class DockerInstanceService extends StatelessService {

  public static final String SELF_LINK = UriPaths.PROVISIONING + "/docker/instance-service";

  @Override
  public void handleRequest(Operation op) {
    if (!op.hasBody()) {
      op.fail(new IllegalArgumentException("body is required"));
      return;
    }

    switch (op.getAction()) {
      case PATCH:
        op.complete();

        ComputeInstanceRequest request = op.getBody(ComputeInstanceRequest.class);
        handleDockerRequest(request);
        break;
      default:
        super.handleRequest(op);
        break;
    }
  }

  private void handleDockerRequest(ComputeInstanceRequest request) {
    if (request.isMockRequest) {
      finishParentTask(request);
      return;
    }

    switch (request.requestType) {
      case CREATE:
        createDockerContainer(request);
        break;
      case DELETE:
        deleteDockerContainer(request);
        break;
      default:
        failParentTask(
            request,
            new IllegalStateException("Invalid docker instance request: " + request.requestType.toString()));
    }
  }

  private void createDockerContainer(ComputeInstanceRequest request) {

    getComputeState(request, computeState ->
      getDiskState(request, computeState, diskState -> {
        DockerClient client = getDockerClient()
            .withComputeState(computeState)
            .withDiskState(diskState);
        final DockerClient.RunContainerResponse response;
        try {
          response = client.runContainer();
        } catch (Throwable t) {
          failParentTask(request, t);
          return;
        }

        ComputeService.ComputeState computePatchState = new ComputeService.ComputeState();
        computePatchState.address = response.address;
        computePatchState.primaryMAC = response.primaryMAC;
        computePatchState.powerState = response.powerState;
        computePatchState.documentSelfLink = computeState.documentSelfLink;

        patchComputeState(request, computePatchState, () -> {

          DiskService.Disk diskPatchState = new DiskService.Disk();
          diskPatchState.status = response.diskStatus;
          diskPatchState.documentSelfLink = diskState.documentSelfLink;

          patchDiskState(request, diskPatchState, () ->
            finishParentTask(request)
          );
        });
      }));
  }

  private void deleteDockerContainer(ComputeInstanceRequest request) {

    getComputeState(request, computeState -> {
      DockerClient client = getDockerClient()
          .withComputeState(computeState);
      client.deleteContainer();

      deleteComputeState(request, computeState, () ->
        finishParentTask(request));

      // TODO(ysheng): do we need to delete the disk state as well?
    });
  }

  private void getComputeState(ComputeInstanceRequest request,
                               Consumer<ComputeService.ComputeState> next) {
    Operation getOp = Operation
        .createGet(request.computeReference)
        //.setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            failParentTask(request, throwable);
            return;
          }

          ComputeService.ComputeStateWithDescription computeState =
              operation.getBody(ComputeService.ComputeStateWithDescription.class);
          next.accept(computeState);
        });
    sendRequest(getOp);
  }

  private void getDiskState(ComputeInstanceRequest request,
                            ComputeService.ComputeState computeState,
                            Consumer<DiskService.Disk> next) {
    if (computeState.diskLinks == null || computeState.diskLinks.size() != 1) {
      failParentTask(
          request,
          new IllegalArgumentException("Only one disk link is allowed for Docker compute state"));
      return;
    }

    Operation getOp = Operation
        .createGet(UriUtils.buildUri(getHost(), computeState.diskLinks.get(0)))
        //.setReferer(getUri())
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            failParentTask(request, throwable);
            return;
          }

          DiskService.Disk diskState = operation.getBody(DiskService.Disk.class);
          next.accept(diskState);
        });
    sendRequest(getOp);
  }

  private void patchComputeState(ComputeInstanceRequest request,
                                 ComputeService.ComputeState computePatchState,
                                 Runnable next) {
    Operation patchOp = Operation
        .createPatch(UriUtils.buildUri(getHost(), computePatchState.documentSelfLink))
        .setReferer(getUri())
        .setBody(computePatchState)
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            failParentTask(request, throwable);
            return;
          }

          next.run();
        });
    sendRequest(patchOp);
;  }

  private void patchDiskState(ComputeInstanceRequest request,
                              DiskService.Disk diskPatchState,
                              Runnable next) {
    Operation patchOp = Operation
        .createPatch(UriUtils.buildUri(getHost(), diskPatchState.documentSelfLink))
        .setReferer(getUri())
        .setBody(diskPatchState)
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            failParentTask(request, throwable);
            return;
          }

          next.run();
        });
    sendRequest(patchOp);
  }

  private void deleteComputeState(ComputeInstanceRequest request,
                                  ComputeService.ComputeState computeState,
                                  Runnable next) {
    Operation deleteOp = Operation
        .createDelete(UriUtils.buildUri(getHost(), computeState.documentSelfLink))
        .setReferer(getUri())
        .setBody(new ServiceDocument())
        .setCompletion((operation, throwable) -> {
          if (throwable != null) {
            failParentTask(request, throwable);
            return;
          }

          next.run();
        });
    sendRequest(deleteOp);
  }

  private void finishParentTask(ComputeInstanceRequest request) {
    ComputeSubTaskService.ComputeSubTaskState patchState =
        new ComputeSubTaskService.ComputeSubTaskState();
    patchState.taskInfo = new TaskState();
    patchState.taskInfo.stage = TaskState.TaskStage.FINISHED;

    Operation patchOp = Operation
        .createPatch(request.provisioningTaskReference)
        .setReferer(getUri())
        .setBody(patchState);
    sendRequest(patchOp);
  }

  private void failParentTask(ComputeInstanceRequest request, Throwable t) {
    ComputeSubTaskService.ComputeSubTaskState patchState =
        new ComputeSubTaskService.ComputeSubTaskState();
    patchState.taskInfo = new TaskState();
    patchState.taskInfo.stage = TaskState.TaskStage.FAILED;
    patchState.taskInfo.failure = Utils.toServiceErrorResponse(t);

    Operation patchOp = Operation
        .createPatch(request.provisioningTaskReference)
        .setReferer(getUri())
        .setBody(patchState);
    sendRequest(patchOp);
  }

  private DockerClient getDockerClient() {
    ServiceHost host = getHost();

    if (host instanceof DockerClientFactory) {
      return ((DockerClientFactory) host).build();
    }

    return new DockerClientFactoryImpl().build();
  }
}
