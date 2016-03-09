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

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a DCP micro-service which performs the task of
 * uploading an VM image to the image datastore.
 */
public class UploadImageTaskService extends StatefulService {

  /**
   * This class defines the document state associated with a single
   * {@link UploadImageTaskService} instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the state of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.STARTED)
    public TaskState taskState;

    /**
     * This value represents the unique ID of the image upload task.
     */
    @NotNull
    @DefaultUuid
    @Immutable
    public String uniqueId;

    /**
     * This value represents the image filename.
     */
    @NotNull
    @Immutable
    public String imageFile;

    /**
     * This value represents the image name.
     */
    @NotNull
    @Immutable
    public String imageName;

    /**
     * This value represents the image replication type.
     */
    @NotNull
    @Immutable
    public ImageReplicationType imageReplicationType;

    /**
     * This value represents the APIFE endpoint for uploading the image. Note that this parameter is only used by
     * {@link AllocateClusterManagerResourcesTaskService}, where the cluster image needs to be uploaded to the
     * management plane. By default this value should be null, and the image will be uploaded to the installer.
     * <p>
     * The reason that the cluster image upload is different is because the upload happens after APIFE data being
     * migrated from installer to management plane. Therefore if we upload the cluster image to installer, the imageId
     * of the cluster image will not show up in the management plane.
     */
    @Immutable
    public String apiFeEndpoint;

    /**
     * This value represents the id of to the created image service.
     */
    @WriteOnce
    public String imageId;

    /**
     * This value represents the interval between querying the state of the
     * upload image task.
     */
    @Immutable
    public Integer queryUploadImageTaskInterval;

    /**
     * This value represents the control flags for the operation.
     */
    @DefaultInteger(value = 0)
    @Immutable
    public Integer controlFlags;
  }

  public UploadImageTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
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

    if (null == startState.queryUploadImageTaskInterval) {
      startState.queryUploadImageTaskInterval = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    if (startState.documentExpirationTimeMicros <= 0) {
      startState.documentExpirationTimeMicros =
          ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_DOC_EXPIRATION_TIME_MICROS);
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
    }

    start.setBody(startState).complete();

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

  /**
   * This method is called when a patch operation is performed on the current service.
   *
   * @param patch Supplies the patch operation object.
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
        processUploadImage(currentState);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  /**
   * This method validates the state of a service document for internal
   * consistency.
   *
   * @param currentState
   */
  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
    ValidationUtils.validateTaskStage(currentState.taskState);
  }

  /**
   * This method validates a patch against a valid service document.
   *
   * @param startState
   * @param patchState
   */
  private void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
    ValidationUtils.validateTaskStage(patchState.taskState);
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method creates a upload image task and submits it to the APIFE service
   * for the DCP host.
   *
   * @param currentState Supplies the current state object.
   * @throws IOException Throws exception if submitting the task to APIFE encounters
   *                     any error.
   */
  private void processUploadImage(final State currentState)
      throws IOException {

    ListenableFutureTask<Task> futureTask = ListenableFutureTask.create(new Callable<Task>() {
      @Override
      public Task call() {
        try {
          return getApiClient(currentState).getImagesApi()
              .uploadImage(currentState.imageFile, currentState.imageReplicationType.name());
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Task> futureCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          if (null == result) {
            failTask(new IllegalStateException("Image upload returned null"));
            return;
          }
          processTask(currentState, result);
        } catch (Throwable e) {
          failTask(e);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  /**
   * This method polls the status of the create VM task. Depending the status
   * returned, the service is transitioned to wait for image seeding completion or failure.
   *
   * @param currentState Supplies the current state object.
   * @param task         Supplies the task object.
   */
  private void processTask(final State currentState, final Task task) {

    FutureCallback<Task> callback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        waitForImageSeedingCompletion(currentState, task.getEntity().getId());
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    ApiUtils.pollTaskAsync(task,
        getApiClient(currentState),
        this,
        currentState.queryUploadImageTaskInterval,
        callback);
  }

  /**
   * This method polls the state of the image service. Depending on the state returned,
   * the service is transitioned to the corresponding stage or sub-stage.
   *
   * @param currentState Supplies the current state object.
   * @param imageId      Supplies the imageId.
   */
  private void waitForImageSeedingCompletion(final State currentState, String imageId) {
    getHost().schedule(
        () -> {
          Operation getOperation =
              HostUtils.getCloudStoreHelper(this).createGet(ImageServiceFactory.SELF_LINK + "/" + imageId)
                  .setCompletion((operation, throwable) -> {
                    if (throwable != null) {
                      failTask(throwable);
                      return;
                    }

                    ImageService.State imageState = operation.getBody(ImageService.State.class);
                    if (imageState.replicatedImageDatastore != null &&
                        imageState.totalImageDatastore != null &&
                        imageState.replicatedImageDatastore.equals(imageState.totalImageDatastore)) {

                      State patchState = new State();
                      patchState.taskState = new TaskState();
                      patchState.taskState.stage = TaskState.TaskStage.FINISHED;
                      patchState.imageId = imageId;
                      TaskUtils.sendSelfPatch(UploadImageTaskService.this, patchState);
                      return;
                    }

                    if (imageState.state == ImageState.ERROR) {
                      failTask(new IllegalStateException("Image state is ERROR. Image seeder process errors out " +
                          "unexpectedly."));
                      return;
                    }

                    waitForImageSeedingCompletion(currentState, imageId);
                  });
          sendRequest(getOperation);
        }, currentState.queryUploadImageTaskInterval, TimeUnit.MILLISECONDS);
  }

  private ApiClient getApiClient(State currentState) {
    return (null == currentState.apiFeEndpoint || currentState.apiFeEndpoint.isEmpty()) ?
        HostUtils.getApiClient(this) : HostUtils.getApiClient(this, currentState.apiFeEndpoint);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState != null && patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    if (startState.imageId == null) {
      startState.imageId = patchState.imageId;
    }

    return startState;
  }

  /**
   * This method sends a patch operation to the current service instance to move to
   * a new state.
   *
   * @param stage Supplies the stage that the current service instance is moving to.
   */
  private void sendStageProgressPatch(TaskState.TaskStage stage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", stage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, null));
  }

  /**
   * This method builds a state object which can be used to submit a stage
   * progress self-patch.
   *
   * @param stage Supplies the stage that the current service instance is moving to.
   * @param e     Supplies the exception that the current service instance encountered if any.
   * @return Returns a patch state object that the current service instance is moving to.
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
   * This method sends a patch operation to the current service instance
   * to moved to the FAILED state in response to the specified exception.
   *
   * @param e Supplies the exception that the current service instance encountered.
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }
}
