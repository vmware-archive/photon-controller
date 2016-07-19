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

import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Project;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.api.model.Tenant;
import com.vmware.photon.controller.api.model.VmDiskOperation;
import com.vmware.photon.controller.api.model.base.FlavoredCompact;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerService;
import com.vmware.photon.controller.deployer.xenon.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.xenon.entity.VmService;
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
import com.google.common.util.concurrent.FutureCallback;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements a Xenon service representing removing deployment.
 */
public class RemoveDeploymentWorkflowService extends StatefulService {

  /**
   * This class defines the state of a {@link RemoveDeploymentWorkflowService} task.
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
      REMOVE_FROM_API_FE,
      DEPROVISION_HOSTS,
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
     * This value represents the ID of the deployment to be deleted.
     */
    public String deploymentId;

    /**
     * This value represents the link to the {@link DeploymentService.State} entity.
     */
    @WriteOnce
    public String deploymentServiceLink;
  }

  public RemoveDeploymentWorkflowService() {
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
    ServiceUtils.logInfo(this, "Handling start for service %s", getSelfLink());
    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);

    if (null == startState.taskPollDelay) {
      startState.taskPollDelay = HostUtils.getDeployerContext(this).getTaskPollDelay();
    }

    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = TaskState.SubStage.REMOVE_FROM_API_FE;
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
        sendStageProgressPatch(startState.taskState.stage, startState.taskState.subStage);
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
        case REMOVE_FROM_API_FE:
        case DEPROVISION_HOSTS:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + currentState.taskState.subStage);
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
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void processStartedState(final State currentState) throws Throwable {
    switch (currentState.taskState.subStage) {
      case REMOVE_FROM_API_FE:
        removeFromAPIFE(currentState);
        break;
      case DEPROVISION_HOSTS:
        queryAndDeprovisionHosts(currentState);
        break;
    }
  }

  // ******************************************
  // Flow of cleanup:
  // STEP1: APIFE/DB cleanup
  // 1. deleteTenant flow
  // 1.1 deleteProjects flow
  // 1.1.1 deleteProject flow
  // 1.1.1.1 deleteDisks flow
  // 1.1.1.1.1 detachDisks from vms
  // 1.1.1.1.2 delete disks
  // 1.1.1.2 delete vms
  // 1.1.2 delete project
  // 1.2 delete tenant
  // 2. deleteImages
  // 3. deleteFlavors
  // STEP2: Deprovision hosts
  // ********************************************

  private void removeFromAPIFE(final State currentState) throws Throwable {
    ServiceUtils.logInfo(this, "Removing objects from API-FE..");
    ApiClient client = HostUtils.getApiClient(this);

    FutureCallback<Task> apiFEDoneCallback =
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            try {
              sendStageProgressPatch(TaskState.TaskStage.STARTED, TaskState.SubStage.DEPROVISION_HOSTS);
            } catch (Throwable t) {
              failTask(t);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    deleteTenants(client, currentState, apiFEDoneCallback);
  }

  private void deleteTenants(final ApiClient client, final State currentState,
                             final FutureCallback<Task> callback) {
    try {
      client.getTenantsApi().listAllAsync(new FutureCallback<ResourceList<Tenant>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<Tenant> result) {
          if (result == null || result.getItems().size() == 0) {
            //No tenants, move to images
            deleteImages(client, currentState, callback);
          } else {
            final AtomicInteger latch = new AtomicInteger(result.getItems().size());
            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {

                if (latch.decrementAndGet() == 0) {
                  //All projects and tenants are deleted
                  deleteImages(client, currentState, callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            final FutureCallback<Task> imageCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result,
                        currentState.taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    failTask(t);
                  }
                };

            for (final Tenant tenant : result.getItems()) {
              deleteTenantAndChildren(client, tenant.getId(), currentState, imageCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteTenantAndChildren(final ApiClient client, final String tenantId, final State currentState,
                                       final FutureCallback<Task> callback) {
    try {
      client.getTenantsApi().getProjectsAsync(tenantId, new FutureCallback<ResourceList<Project>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<Project> result) {
          if (result == null || result.getItems().size() == 0) {
            //No projects, delete tenant
            deleteTenant(client, tenantId, currentState, callback);
          } else {
            final AtomicInteger latch = new AtomicInteger(result.getItems().size());
            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {
                if (latch.decrementAndGet() == 0) {
                  //All projects are deleted
                  deleteTenant(client, tenantId, currentState, callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };
            final FutureCallback<Task> projectCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result,
                        currentState.taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    failTask(t);
                  }
                };

            for (final Project project : result.getItems()) {
              deleteProjectAndChildren(client, project.getId(), tenantId, currentState, projectCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteProject(final ApiClient client, final String projectId, final String tenantId,
                             final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(this, "Delete projects..");
    try {
      client.getProjectApi().deleteAsync(projectId, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteProjectAndChildren(final ApiClient client, final String projectId, final String tenantId,
                                        final State currentState, final FutureCallback<Task> callback) {
    try {
      client.getProjectApi().getDisksInProjectAsync(projectId, new FutureCallback<ResourceList<PersistentDisk>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<PersistentDisk> result) {
          if (result == null || result.getItems().size() == 0) {
            //No persistent disk, delete the VMs
            deleteVms(client, projectId, tenantId, currentState, callback);
          } else {
            final AtomicInteger latch = new AtomicInteger(result.getItems().size());

            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {
                if (latch.decrementAndGet() == 0) {
                  //All disks are detached and deleted
                  deleteVms(client, projectId, tenantId, currentState, callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            final FutureCallback<Task> diskCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result,
                        currentState.taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    failTask(t);
                  }
                };

            for (final PersistentDisk persistentDisk : result.getItems()) {
              detachAndDeleteDisk(client, persistentDisk, currentState, diskCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void detachAndDeleteDisk(final ApiClient client, final PersistentDisk persistentDisk,
                                   final State currentState, final FutureCallback<Task> callback) {
    final AtomicInteger latch = new AtomicInteger(persistentDisk.getVms().size());

    final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        if (latch.decrementAndGet() == 0) {
          // Disk is detached from all the vms
          deleteDisk(client, persistentDisk.getId(), currentState, callback);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    FutureCallback<Task> detachDiskCallback =
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result, currentState.taskPollDelay,
                finishedCallback);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    if (persistentDisk.getVms().size() == 0) {
      deleteDisk(client, persistentDisk.getId(), currentState, callback);
    } else {
      for (String vmId : persistentDisk.getVms()) {
        detachDisk(client, vmId, persistentDisk, currentState, detachDiskCallback);
      }
    }
  }

  private void detachDisk(final ApiClient client, String vmId, PersistentDisk disk,
                          State currentState, FutureCallback<Task> callback) {
    ServiceUtils.logInfo(this, "Detach disks..");
    VmDiskOperation vmDiskOperation = new VmDiskOperation();
    vmDiskOperation.setDiskId(disk.getId());
    try {
      client.getVmApi().detachDiskAsync(vmId, vmDiskOperation, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteDisk(final ApiClient client, String diskId, State currentState, FutureCallback<Task>
      callback) {
    ServiceUtils.logInfo(this, "Delete disks..");
    try {
      client.getDisksApi().deleteAsync(diskId, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteVms(final ApiClient client, final String projectId, final String tenantId,
                         final State currentState, final FutureCallback<Task> callback) {
    try {
      client.getProjectApi().getVmsInProjectAsync(projectId, new FutureCallback<ResourceList<FlavoredCompact>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<FlavoredCompact> result) {
          if (result == null || result.getItems().size() == 0) {
            //No Vms, delete project
            deleteProject(client, projectId, tenantId, callback);
          } else {
            final AtomicInteger latch = new AtomicInteger(result.getItems().size());

            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {
                if (latch.decrementAndGet() == 0) {
                  //All vms are deleted
                  deleteProject(client, projectId, tenantId, callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            final FutureCallback<Task> vmCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result,
                        currentState.taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    failTask(t);
                  }
                };

            for (final FlavoredCompact flavoredCompact : result.getItems()) {
              MiscUtils.stopAndDeleteVm(RemoveDeploymentWorkflowService.this, client, flavoredCompact.getId(),
                  currentState.taskPollDelay, vmCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteTenant(final ApiClient client, final String tenantId, State currentState,
                            final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(this, "Delete tenants..");
    try {
      client.getTenantsApi().deleteAsync(tenantId, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteImages(final ApiClient client, final State currentState,
                            final FutureCallback<Task> callback) {
    try {
      client.getImagesApi().getImagesAsync(new FutureCallback<ResourceList<Image>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<Image> result) {
          if (result == null || result.getItems().size() == 0) {
            //No Images, delete flavors
            deleteFlavors(client, currentState, callback);
          } else {
            final AtomicInteger latch = new AtomicInteger(result.getItems().size());

            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {
                if (latch.decrementAndGet() == 0) {
                  //All images are deleted
                  deleteFlavors(client, currentState, callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            final FutureCallback<Task> flavorCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result,
                        currentState.taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    // Log and ignore
                    MiscUtils.logError(RemoveDeploymentWorkflowService.this, t);

                    finishedCallback.onSuccess(null);
                  }
                };

            for (final Image image : result.getItems()) {
              deleteImage(client, image.getId(), currentState, flavorCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteImage(final ApiClient client, final String imageId, State currentState,
                           final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(this, "Delete images..");
    try {
      client.getImagesApi().deleteAsync(imageId, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void deleteFlavors(final ApiClient client, final State currentState, final FutureCallback<Task>
      callback) {
    try {
      client.getFlavorApi().listAllAsync(new FutureCallback<ResourceList<Flavor>>() {
        @Override
        public void onSuccess(@Nullable final ResourceList<Flavor> result) {
          if (result == null || result.getItems().size() == 0) {
            //No flavor
            deleteXenonEntities(callback);

          } else {

            final AtomicInteger latch = new AtomicInteger(result.getItems().size());

            final FutureCallback<Task> finishedCallback = new FutureCallback<Task>() {
              @Override
              public void onSuccess(@Nullable Task result) {
                if (latch.decrementAndGet() == 0) {
                  //All flavors are deleted
                  deleteXenonEntities(callback);
                }
              }

              @Override
              public void onFailure(Throwable t) {
                failTask(t);
              }
            };

            final FutureCallback<Task> nextCallback =
                new FutureCallback<Task>() {
                  @Override
                  public void onSuccess(@Nullable Task result) {
                    MiscUtils.waitForTaskToFinish(RemoveDeploymentWorkflowService.this, result, currentState
                        .taskPollDelay, finishedCallback);
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    failTask(t);
                  }
                };

            for (final Flavor flavor : result.getItems()) {
              deleteFlavor(client, flavor.getId(), nextCallback);
            }
          }
        }

        @Override
        public void onFailure(Throwable t) {
          failTask(t);
        }
      });
    } catch (Throwable t) {
      failTask(t);
    }
  }


  private Operation.CompletionHandler createCompletionHandlerForDeleteXenonEntities(boolean isCloudStoreEntity) {
    return new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        try {
          Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
          QueryTaskUtils.logQueryResults(RemoveDeploymentWorkflowService.this, documentLinks);
          if (documentLinks.size() > 0) {
            processDeleteFromXenon(documentLinks, isCloudStoreEntity);
          }
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };
  }

  private void deleteXenonEntities(final FutureCallback<Task> callback) {
    deleteXenonEntities(ContainerTemplateService.State.class);
    deleteXenonEntities(ContainerService.State.class);
    deleteXenonEntities(VmService.State.class);

    ServiceUtils.logInfo(this, "Remove from cloud store..");

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(buildQuerySpecification(DatastoreService.class)).setDirect(true))
            .setCompletion(createCompletionHandlerForDeleteXenonEntities(true)));

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(buildQuerySpecification(DeploymentService.class)).setDirect(true))
            .setCompletion(createCompletionHandlerForDeleteXenonEntities(true)));

    callback.onSuccess(null);
  }

  private void deleteXenonEntities(Class entityClass) {

    sendRequest(Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(QueryTask
            .create(buildQuerySpecification(entityClass))
            .setDirect(true))
        .setCompletion(createCompletionHandlerForDeleteXenonEntities(false)));
  }

  private void deleteFlavor(final ApiClient client, final String flavorId,
                            final FutureCallback<Task> callback) {
    ServiceUtils.logInfo(this, "Delete flavors..");

    try {
      client.getFlavorApi().deleteAsync(flavorId, callback);
    } catch (Throwable t) {
      failTask(t);
    }
  }

  private void processDeleteFromXenon(Collection<String> documentLinks, boolean isCloudStoreEntity) {

    OperationJoin
        .create(documentLinks.stream()
            .map(documentLink -> isCloudStoreEntity ?
                HostUtils.getCloudStoreHelper(this).createDelete(documentLink) :
                Operation.createDelete(this, documentLink).setBody(new ServiceDocument())))
        .setCompletion(
            (ops, failures) -> {
              if (null != failures && failures.size() > 0) {
                failTask(failures);
              }
            }
        )
        .sendWith(this);
  }

  private QueryTask.QuerySpecification buildQuerySpecification(Class xenonEntityClass) {
    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(xenonEntityClass));
    return querySpecification;
  }

  private void queryAndDeprovisionHosts(final State currentState) {

    sendRequest(
        HostUtils.getCloudStoreHelper(this)
            .createBroadcastPost(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, ServiceUriPaths.DEFAULT_NODE_SELECTOR)
            .setBody(QueryTask.create(buildQuerySpecification(HostService.State.class)).setDirect(true))
            .setCompletion(
                (completedOp, failure) -> {
                  if (null != failure) {
                    failTask(failure);
                    return;
                  }

                  try {
                    Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(completedOp);
                    QueryTaskUtils.logQueryResults(RemoveDeploymentWorkflowService.this, documentLinks);
                    if (documentLinks.size() > 0) {
                      deprovisionHosts(currentState, documentLinks);
                    }
                  } catch (Throwable t) {
                    failTask(t);
                  }
                }
            ));
  }

  private void deprovisionHosts(final State currentState, Collection<String> documentLinks) throws Throwable {

    ServiceUtils.logInfo(this, "Deprovisioning hosts..");
    final AtomicInteger numOfPendingHosts = new AtomicInteger(documentLinks.size());
    final Service service = this;

    FutureCallback<DeprovisionHostWorkflowService.State> callback
        = new FutureCallback<DeprovisionHostWorkflowService.State>() {
      @Override
      public void onSuccess(@Nullable DeprovisionHostWorkflowService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            if (0 == numOfPendingHosts.decrementAndGet()) {
              TaskUtils.sendSelfPatch(service, buildPatch(
                  TaskState.TaskStage.FINISHED,
                  null,
                  null));
            }
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

    DeprovisionHostWorkflowService.State startState = new DeprovisionHostWorkflowService.State();
    startState.taskPollDelay = currentState.taskPollDelay;
    startState.deploymentServiceLink = currentState.deploymentServiceLink;

    for (String hostServiceLink : documentLinks) {
      startState.hostServiceLink = hostServiceLink;
      TaskUtils.startTaskAsync(
          this,
          DeprovisionHostWorkflowFactoryService.SELF_LINK,
          startState,
          (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage),
          DeprovisionHostWorkflowService.State.class,
          currentState.taskPollDelay,
          callback);
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
    }

    PatchUtils.patchState(startState, patchState);
    return startState;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param patchStage
   * @param patchSubStage
   */
  private void sendStageProgressPatch(TaskState.TaskStage patchStage, @Nullable TaskState.SubStage patchSubStage) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s:%s", patchStage, patchSubStage);
    TaskUtils.sendSelfPatch(this, buildPatch(patchStage, patchSubStage, null));
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    MiscUtils.logError(RemoveDeploymentWorkflowService.this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  private void failTask(Map<Long, Throwable> failures) {
    failures.values().forEach(failure -> MiscUtils.logError(RemoveDeploymentWorkflowService.this, failure));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, failures.values().iterator().next()));
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
