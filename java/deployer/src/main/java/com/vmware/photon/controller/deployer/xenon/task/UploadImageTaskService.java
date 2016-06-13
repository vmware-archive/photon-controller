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

package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.api.Image;
import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
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
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
import com.vmware.photon.controller.deployer.xenon.util.ApiUtils;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.output.CountingOutputStream;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a Xenon service which uploads an image.
 */
public class UploadImageTaskService extends StatefulService {

  /**
   * This class defines the state of a {@link UploadImageTaskService} task.
   */
  public static class TaskState extends com.vmware.xenon.common.TaskState {

    /**
     * This type defines the possible sub-stages for a {@link UploadImageTaskService} task.
     */
    public enum SubStage {
      UPLOAD_IMAGE,
      WAIT_FOR_IMAGE_UPLOAD,
      WAIT_FOR_IMAGE_SEEDING,
      UPDATE_VMS,
    }

    /**
     * This value defines the sub-stage of the current task.
     */
    public SubStage subStage;
  }

  /**
   * This class defines the document state associated with a {@link UploadImageTaskService} task.
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
     * This value represents the delay, in milliseconds, to use when polling child tasks.
     */
    @Positive
    @Immutable
    public Integer taskPollDelay;

    /**
     * This value represents the document self-link of the {@link DeploymentService} document to be
     * updated with the ID of the created image.
     */
    @NotNull
    @Immutable
    public String deploymentServiceLink;

    /**
     * This value represents the name to assign to the API-level image object when uploading the
     * image.
     */
    @NotNull
    @Immutable
    public String imageName;

    /**
     * This value represents the full path to the image file to upload.
     */
    @NotNull
    @Immutable
    public String imageFile;

    /**
     * This value represents the number of bytes which have been uploaded.
     */
    @DefaultLong(value = 0L)
    public Long bytesUploaded;

    /**
     * This value represents the ID of the API-level task to create the image.
     */
    @WriteOnce
    public String uploadImageTaskId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for the image upload operation to complete. It is informational
     * only.
     */
    @DefaultInteger(value = 0)
    public Integer uploadImagePollCount;

    /**
     * This value represents the ID of the API-level image object created by the current task.
     */
    @WriteOnce
    public String imageId;

    /**
     * This value represents the number of polling iterations which have been performed by the
     * current task while waiting for image replication to complete. It is informational only.
     */
    @DefaultInteger(value = 0)
    public Integer imageSeedingPollCount;

    /**
     * This value represents the seeding progress of the image, as reported by the image API.
     * It is informational only.
     */
    public String imageSeedingProgress;
  }

  /**
   * This class defines the patch body type used in image upload progress notification patches.
   */
  public static class ImageUploadProgressNotification {

    public static final String FIELD_NAME_KIND = "kind";

    /**
     * This type defines the possible purposes of the notification.
     */
    public enum Kind {
      ADD_UPLOAD_PROGRESS,
    }

    /**
     * This value represents the purpose of the current notification.
     */
    public Kind kind;

    /**
     * This value represents the change in the number of bytes uploaded.
     */
    public Integer delta;
  }

  @FunctionalInterface
  private interface IStreamListener {
    void counterChanged(int delta);
  }

  private static class FileBody extends org.apache.http.entity.mime.content.FileBody {

    private IStreamListener streamListener;

    public FileBody(File file) {
      super(file);
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {

      CountingOutputStream countingOutputStream = new CountingOutputStream(outputStream) {
        @Override
        protected void beforeWrite(int n) {
          if (streamListener != null && n != 0) {
            streamListener.counterChanged(n);
          }
          super.beforeWrite(n);
        }
      };

      super.writeTo(countingOutputStream);
    }

    public void setStreamListener(IStreamListener streamListener) {
      this.streamListener = streamListener;
    }
  }

  public UploadImageTaskService() {
    super(State.class);
  }

  @Override
  public OperationProcessingChain getOperationProcessingChain() {

    if (super.getOperationProcessingChain() != null) {
      return super.getOperationProcessingChain();
    }

    RequestRouter requestRouter = new RequestRouter();
    requestRouter.register(
        Action.PATCH,
        new RequestRouter.RequestBodyMatcher<>(
            ImageUploadProgressNotification.class,
            ImageUploadProgressNotification.FIELD_NAME_KIND,
            ImageUploadProgressNotification.Kind.ADD_UPLOAD_PROGRESS),
        this::handleImageUploadProgressNotification,
        "Image upload progress notification handling");

    OperationProcessingChain operationProcessingChain = new OperationProcessingChain(this);
    operationProcessingChain.add(requestRouter);
    this.setOperationProcessingChain(operationProcessingChain);
    return operationProcessingChain;
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

    if (startState.taskPollDelay == null) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

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
        sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPLOAD_IMAGE);
      }
    } catch (Throwable t) {
      failTask(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handing patch operation");
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

  private void handleImageUploadProgressNotification(Operation patchOp) {
    ServiceUtils.logTrace(this, "Handling image upload progress notification");
    if (!patchOp.hasBody()) {
      patchOp.fail(new IllegalArgumentException("Body is required"));
      return;
    }

    State currentState = getState(patchOp);
    ImageUploadProgressNotification notification = patchOp.getBody(ImageUploadProgressNotification.class);

    try {
      checkState(notification.delta > 0);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, e);
      return;
    }

    /**
     * N.B. Log only when the uploaded file size crosses a 1MB boundary.
     */
    currentState.bytesUploaded += notification.delta;
    if ((currentState.bytesUploaded % (1024 * 1024)) < notification.delta) {
      ServiceUtils.logInfo(this, "Uploaded " + currentState.bytesUploaded + " bytes");
    }

    try {
      validateState(currentState);
    } catch (IllegalStateException e) {
      ServiceUtils.failOperationAsBadRequest(this, patchOp, e);
      return;
    }

    patchOp.complete();
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
          case UPLOAD_IMAGE:
          case WAIT_FOR_IMAGE_UPLOAD:
          case WAIT_FOR_IMAGE_SEEDING:
          case UPDATE_VMS:
            break;
          default:
            throw new IllegalStateException("Unknown task sub-stage: " + taskState.subStage);
        }
    }
  }

  private void validateTaskStageProgression(TaskState currentState, TaskState patchState) {
    ValidationUtils.validateTaskStageProgression(currentState, patchState);
    if (currentState.subStage != null && patchState.subStage != null) {
      checkState(patchState.subStage.ordinal() >= currentState.subStage.ordinal());
    }
  }

  private void processStartedStage(State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case UPLOAD_IMAGE:
        processUploadImageSubStage(currentState);
        break;
      case WAIT_FOR_IMAGE_UPLOAD:
        processWaitForImageUploadSubStage(currentState);
        break;
      case WAIT_FOR_IMAGE_SEEDING:
        processWaitForImageSeedingSubStage(currentState);
        break;
      case UPDATE_VMS:
        processUpdateVmsSubStage(currentState);
        break;
    }
  }

  //
  // UPLOAD_IMAGE sub-stage routines
  //

  private void processUploadImageSubStage(State currentState) {

    FileBody fileBody = new FileBody(new File(currentState.imageFile));
    fileBody.setStreamListener(
        (delta) -> {
          ImageUploadProgressNotification notification = new ImageUploadProgressNotification();
          notification.kind = ImageUploadProgressNotification.Kind.ADD_UPLOAD_PROGRESS;
          notification.delta = delta;
          sendRequest(Operation.createPatch(this, getSelfLink()).setBody(notification));
        });

    //
    // N.B. Image upload is not performed asynchronously (and can't be), so this operation is
    // performed on a separate thread.
    //

    ListenableFutureTask<Task> futureTask = ListenableFutureTask.create(
        () -> HostUtils.getApiClient(this)
            .getImagesApi()
            .uploadImage(fileBody, ImageReplicationType.ON_DEMAND.name()));

    HostUtils.getListeningExecutorService(this).submit(futureTask);

    Futures.addCallback(futureTask,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@javax.validation.constraints.NotNull Task task) {
            try {
              processUploadImageTaskResult(task);
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

  private void processUploadImageTaskResult(Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_IMAGE_UPLOAD);
        patchState.uploadImageTaskId = task.getId();
        patchState.uploadImagePollCount = 1;
        sendStageProgressPatch(patchState);
        break;
      case "COMPLETED":
        updateImageId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  //
  // WAIT_FOR_IMAGE_UPLOAD sub-stage routines
  //

  private void processWaitForImageUploadSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getTasksApi()
        .getTaskAsync(currentState.uploadImageTaskId,
            new FutureCallback<Task>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Task task) {
                try {
                  processWaitForImageUploadTaskResult(currentState, task);
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

  private void processWaitForImageUploadTaskResult(State currentState, Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_IMAGE_UPLOAD);
              patchState.uploadImagePollCount = currentState.uploadImagePollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case "COMPLETED":
        updateImageId(task.getEntity().getId());
        break;
      case "ERROR":
        throw new IllegalStateException(ApiUtils.getErrors(task));
      default:
        throw new IllegalStateException("Unknown task state: " + task.getState());
    }
  }

  private void updateImageId(String imageId) {
    ServiceUtils.logInfo(this, "Created image with ID " + imageId);
    State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_IMAGE_SEEDING);
    patchState.imageSeedingPollCount = 1;
    patchState.imageId = imageId;
    sendStageProgressPatch(patchState);
  }

  //
  // WAIT_FOR_IMAGE_SEEDING sub-stage routines
  //

  private void processWaitForImageSeedingSubStage(State currentState) throws Throwable {

    HostUtils.getApiClient(this)
        .getImagesApi()
        .getImageAsync(currentState.imageId,
            new FutureCallback<Image>() {
              @Override
              public void onSuccess(@javax.validation.constraints.NotNull Image image) {
                try {
                  processImage(currentState, image);
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

  private void processImage(State currentState, Image image) {
    switch (image.getState()) {
      case READY:
        String imageSeedingProgress = image.getSeedingProgress();

        //
        // N.B. This is a best-effort computation based on the information provided in the image
        // API. It's not clear that this pattern will work well for callers.
        //

        if (imageSeedingProgress != null && Float.valueOf(imageSeedingProgress.trim().replace("%", "")) == 100.0) {
          State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.UPDATE_VMS);
          patchState.imageSeedingProgress = imageSeedingProgress;
          sendStageProgressPatch(patchState);
          return;
        }

        if (imageSeedingProgress != null && !imageSeedingProgress.equals(currentState.imageSeedingProgress)) {
          ServiceUtils.logInfo(this, "Image " + image.getId() + " reached seeding progress " +
              imageSeedingProgress.replace("%", "%%"));
        }

        getHost().schedule(
            () -> {
              State patchState = buildPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.WAIT_FOR_IMAGE_SEEDING);
              patchState.imageSeedingProgress = imageSeedingProgress;
              patchState.imageSeedingPollCount = currentState.imageSeedingPollCount + 1;
              TaskUtils.sendSelfPatch(this, patchState);
            },
            currentState.taskPollDelay, TimeUnit.MILLISECONDS);
        break;
      case ERROR:
        ServiceUtils.logSevere(this, "Image reached ERROR state: " + Utils.toJsonHtml(image));
        throw new IllegalStateException("Image " + image.getId() + " reached ERROR state");
      default:
        ServiceUtils.logSevere(this, "Image reached unexpected state: " + Utils.toJsonHtml(image));
        throw new IllegalStateException("Image " + image.getId() + " reached unexpected state " + image.getState());
    }
  }

  //
  // UPDATE_VMS sub-stage
  //

  private void processUpdateVmsSubStage(State currentState) {

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
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
                updateVms(currentState, o.getBody(QueryTask.class).results.documentLinks);
              } catch (Throwable t) {
                failTask(t);
              }
            }));
  }

  private void updateVms(State currentState, List<String> vmServiceLinks) {

    VmService.State vmPatchState = new VmService.State();
    vmPatchState.imageId = currentState.imageId;

    OperationJoin
        .create(vmServiceLinks.stream()
            .map((vmServiceLink) -> Operation.createPatch(this, vmServiceLink).setBody(vmPatchState)))
        .setCompletion(
            (ops, exs) -> {
              if (exs != null && !exs.isEmpty()) {
                failTask(exs.values());
                return;
              }

              try {
                updateDeployment(currentState);
              } catch (Throwable t) {
                failTask(t);
              }
            })
        .sendWith(this);
  }

  private void updateDeployment(State currentState) {

    DeploymentService.State deploymentPatchState = new DeploymentService.State();
    deploymentPatchState.imageId = currentState.imageId;

    sendRequest(HostUtils.getCloudStoreHelper(this)
        .createPatch(currentState.deploymentServiceLink)
        .setBody(deploymentPatchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                failTask(e);
              } else {
                sendStageProgressPatch(TaskState.TaskStage.FINISHED, null);
              }
            }));
  }
  //
  // Utility routines
  //

  private void sendStageProgressPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    ServiceUtils.logTrace(this, "Sending self-patch to stage %s:%s", stage, subStage);
    TaskUtils.sendSelfPatch(this, buildPatch(stage, subStage, null));
  }

  private void sendStageProgressPatch(State patchState) {
    ServiceUtils.logTrace(this, "Sending self-patch: {}", patchState);
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
  protected static State buildPatch(TaskState.TaskStage stage, TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, null);
  }

  @VisibleForTesting
  protected static State buildPatch(TaskState.TaskStage stage,
                                    TaskState.SubStage subStage,
                                    @Nullable Throwable failure) {
    State patchState = new State();
    patchState.taskState = new TaskState();
    patchState.taskState.stage = stage;
    patchState.taskState.subStage = subStage;
    if (failure != null) {
      patchState.taskState.failure = Utils.toServiceErrorResponse(failure);
    }

    return patchState;
  }
}
