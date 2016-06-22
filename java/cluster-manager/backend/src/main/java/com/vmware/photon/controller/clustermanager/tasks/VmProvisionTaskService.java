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
package com.vmware.photon.controller.clustermanager.tasks;

import com.vmware.photon.controller.api.AttachedDiskCreateSpec;
import com.vmware.photon.controller.api.EphemeralDisk;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.VmCreateSpec;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.clustermanager.servicedocuments.ClusterManagerConstants;
import com.vmware.photon.controller.clustermanager.servicedocuments.FileTemplate;
import com.vmware.photon.controller.clustermanager.utils.ApiUtils;
import com.vmware.photon.controller.clustermanager.utils.HostUtils;
import com.vmware.photon.controller.clustermanager.utils.ScriptRunner;
import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.WriteOnce;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Predicate;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class implements a Xenon service representing a task to provision a VM.
 */
public class VmProvisionTaskService extends StatefulService {

  public static final String SCRIPT_NAME = "esx-create-vm-iso";
  private static final String CONFIG_FILENAME = SCRIPT_NAME + "-config-file";

  public VmProvisionTaskService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    State startState = startOperation.getBody(State.class);
    InitializationUtils.initialize(startState);
    validateStartState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
      startState.taskState.subStage = State.TaskState.SubStage.CREATE_VM;
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
        TaskUtils.sendSelfPatch(this,
            buildPatch(startState.taskState.stage, State.TaskState.SubStage.CREATE_VM));
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

  private void processStateMachine(State currentState) {
    try {
      switch (currentState.taskState.subStage) {
        case CREATE_VM:
          createVm(currentState);
          break;
        case ATTACH_ISO:
          createIsoFile(currentState);
          break;
        case START_VM:
          startVm(currentState);
          break;
        case VERIFY_VM:
          verifyVm(currentState);
          break;

        default:
          throw new IllegalStateException("Unknown sub-stage: " + currentState.taskState.subStage);
      }
    } catch (Throwable ex) {
      failTask(ex);
    }
  }

  /**
   * Creates the VM in ESXCloud by calling API-FE. On success, the method save
   * the identifier of the created VM and moves the Task to the next sub-stage i.e. ATTACH_ISO.
   *
   * @param currentState
   */
  private void createVm(final State currentState) {
    FutureCallback<Task> callback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          if (null == result) {
            failTask(new IllegalStateException("createVmAsync returned null"));
            return;
          }

          processTask(result, currentState,
              buildPatch(TaskState.TaskStage.STARTED, State.TaskState.SubStage.ATTACH_ISO));
        } catch (Throwable e) {
          failTask(e);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    try {
      HostUtils.getApiClient(this).getProjectApi().createVmAsync(
          currentState.projectId,
          composeVmCreateSpec(currentState),
          callback);
    } catch (IOException ex) {
      failTask(ex);
    }
  }

  private VmCreateSpec composeVmCreateSpec(final State currentState) {

    VmCreateSpec spec = new VmCreateSpec();
    spec.setName(currentState.vmName);
    spec.setFlavor(currentState.vmFlavorName);
    spec.setTags(currentState.vmTags);
    spec.setSourceImageId(currentState.imageId);

    if (currentState.vmNetworkId != null) {
      List<String> networks = new ArrayList<>();
      networks.add(currentState.vmNetworkId);
      spec.setNetworks(networks);
    }

    List<AttachedDiskCreateSpec> attachedDisks = new ArrayList<>();

    AttachedDiskCreateSpec bootDisk = new AttachedDiskCreateSpec();
    bootDisk.setName(currentState.vmName + "-bootdisk");
    bootDisk.setBootDisk(true);
    bootDisk.setFlavor(currentState.diskFlavorName);
    bootDisk.setKind(EphemeralDisk.KIND);
    bootDisk.setCapacityGb(1);
    attachedDisks.add(bootDisk);

    spec.setAttachedDisks(attachedDisks);

    Map<String, String> environment = new HashMap<>();
    spec.setEnvironment(environment);

    return spec;
  }

  /**
   * Attaches an ISO to the created VM. The ISO attached
   * contains the Cloud-Init configuration as specified through the template files passed-in the task.
   * On success, the task is moved to the next sub-stage i.e. START_VM
   *
   * @param currentState
   */
  private void createIsoFile(final State currentState) throws Throwable {

    final File isoFile = new File("/tmp",
        CONFIG_FILENAME + "-" + currentState.vmId + ".iso");
    final File userDataConfigFile = new File(HostUtils.getScriptsDirectory(this),
        CONFIG_FILENAME + "-user-data-" + currentState.vmId + ".yml");
    final File metaDataConfigFile = new File(HostUtils.getScriptsDirectory(this),
        CONFIG_FILENAME + "-meta-data-" + currentState.vmId + ".yml");
    File scriptLogFile = new File(HostUtils.getScriptsDirectory(this),
        SCRIPT_NAME + "-" + currentState.vmId + ".log");

    String userDataConfigFilename = createFile(currentState.userData, userDataConfigFile);
    String metaDataConfigFilename = createFile(currentState.metaData, metaDataConfigFile);

    List<String> command = new ArrayList<>();
    command.add("/bin/bash");
    command.add("./" + SCRIPT_NAME);
    command.add(isoFile.getAbsolutePath());
    command.add(userDataConfigFilename);
    command.add(metaDataConfigFilename);

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, ClusterManagerConstants.SCRIPT_TIMEOUT_IN_SECONDS)
        .directory(HostUtils.getScriptsDirectory(this))
        .redirectOutput(ProcessBuilder.Redirect.to(scriptLogFile))
        .build();

    ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
      @Override
      public void onSuccess(@Nullable Integer result) {
        if (null == result) {
          failTask(new NullPointerException(SCRIPT_NAME + " returned null"));
        } else if (0 != result) {
          failTask(new Exception(SCRIPT_NAME + " returned " + result.toString()));
        } else {
          try {
            Files.deleteIfExists(userDataConfigFile.toPath());
            Files.deleteIfExists(metaDataConfigFile.toPath());
            attachAndUploadIso(currentState, isoFile.getAbsolutePath());
          } catch (IOException e) {
            failTask(e);
          }
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    Futures.addCallback(futureTask, futureCallback);
  }

  private String createFile(FileTemplate template, File isoFile) throws IOException {
    String content = new String(Files.readAllBytes(Paths.get(template.filePath)),
        StandardCharsets.UTF_8);

    for (Map.Entry<String, String> parameter : template.parameters.entrySet()) {
      content = content.replace(parameter.getKey(), parameter.getValue());
    }

    Files.write(isoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return isoFile.getAbsolutePath();
  }

  private void attachAndUploadIso(final State currentState, final String isoFile) throws IOException {
    final ApiClient client = HostUtils.getApiClient(this);
    ListenableFutureTask<Task> futureTask = ListenableFutureTask.create(() -> {
      try {
        return client.getVmApi().uploadAndAttachIso(currentState.vmId, isoFile);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    HostUtils.getListeningExecutorService(this).submit(futureTask);

    FutureCallback<Task> futureCallback = new FutureCallback<Task>() {
      @Override
      public void onSuccess(@Nullable Task result) {
        try {
          if (null == result) {
            failTask(new IllegalStateException("IsoUploadAndAttach returned null"));
            return;
          }
          Files.deleteIfExists(Paths.get(isoFile));
          processTask(result, currentState,
              buildPatch(TaskState.TaskStage.STARTED, State.TaskState.SubStage.START_VM));
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
   * This method starts a vm.
   *
   * @param currentState Supplies the current state object.
   */
  private void startVm(State currentState) {
    try {
      HostUtils.getApiClient(this).getVmApi().performStartOperationAsync(
          currentState.vmId,
          new FutureCallback<Task>() {
            @Override
            public void onSuccess(@Nullable Task result) {
              processTask(result, currentState,
                  buildPatch(TaskState.TaskStage.STARTED, State.TaskState.SubStage.VERIFY_VM));
            }

            @Override
            public void onFailure(Throwable t) {
              failTask(t);
            }
          }
      );
    } catch (IOException e) {
      failTask(e);
    }
  }

  private void processTask(Task task, final State currentState, final State patchState) {
    ApiUtils.pollTaskAsync(
        task,
        HostUtils.getApiClient(this),
        this,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        new FutureCallback<Task>() {
          @Override
          public void onSuccess(@Nullable Task result) {
            if (currentState.vmId == null) {
              patchState.vmId = task.getEntity().getId();
            }
            TaskUtils.sendSelfPatch(VmProvisionTaskService.this, patchState);
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        }
    );
  }

  /**
   * Uses the {@link WaitForNetworkTaskService} until the VM has acquired an IP Address. On success, moves the task to
   * the FINISHED Stage.
   *
   * @param currentState
   */
  private void verifyVm(State currentState) {

    final Service service = this;

    Predicate<WaitForNetworkTaskService.State> predicate = new Predicate<WaitForNetworkTaskService.State>() {
      @Override
      public boolean apply(@Nullable WaitForNetworkTaskService.State state) {
        return TaskUtils.finalTaskStages.contains(state.taskState.stage);
      }
    };

    FutureCallback<WaitForNetworkTaskService.State> callback = new FutureCallback<WaitForNetworkTaskService.State>() {
      @Override
      public void onSuccess(@Nullable WaitForNetworkTaskService.State result) {
        switch (result.taskState.stage) {
          case FINISHED:
            State patchState = buildPatch(TaskState.TaskStage.FINISHED, null);
            patchState.vmIpAddress = result.vmIpAddress;
            TaskUtils.sendSelfPatch(service, patchState);
            break;
          case CANCELLED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.CANCELLED, null));
            break;
          case FAILED:
            TaskUtils.sendSelfPatch(service, buildPatch(TaskState.TaskStage.FAILED, null, result.taskState.failure));
            break;
        }
      }

      @Override
      public void onFailure(Throwable t) {
        failTask(t);
      }
    };

    WaitForNetworkTaskService.State startState = new WaitForNetworkTaskService.State();
    startState.vmId = currentState.vmId;

    TaskUtils.startTaskAsync(
        this,
        WaitForNetworkTaskFactoryService.SELF_LINK,
        startState,
        predicate,
        WaitForNetworkTaskService.State.class,
        ClusterManagerConstants.DEFAULT_TASK_POLL_DELAY,
        callback);
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
    validateSubStage(patchState);

    if (currentState.taskState.subStage != null && patchState.taskState.subStage != null) {
      checkState(patchState.taskState.subStage.ordinal() >= currentState.taskState.subStage.ordinal());
    }
  }

  private void validateSubStage(State state) {

    if (state.taskState.stage == TaskState.TaskStage.STARTED) {
      checkState(state.taskState.subStage != null, "Sub-stage cannot be null in STARTED stage.");

      switch (state.taskState.subStage) {
        case CREATE_VM:
        case ATTACH_ISO:
        case START_VM:
        case VERIFY_VM:
          break;
        default:
          throw new IllegalStateException("Unknown task sub-stage: " + state.taskState.subStage.toString());
      }
    } else {
      checkState(state.taskState.subStage == null, "Sub-stage must be null in stages other than STARTED.");
    }

  }

  private State buildPatch(
      TaskState.TaskStage stage, State.TaskState.SubStage subStage) {
    return buildPatch(stage, subStage, (Throwable) null);
  }

  private State buildPatch(
      TaskState.TaskStage stage, State.TaskState.SubStage subStage, @Nullable Throwable t) {
    return buildPatch(stage, subStage, null == t ? null : Utils.toServiceErrorResponse(t));
  }

  private State buildPatch(
      State.TaskState.TaskStage stage,
      State.TaskState.SubStage subStage,
      @Nullable ServiceErrorResponse errorResponse) {

    State state = new State();
    state.taskState = new State.TaskState();
    state.taskState.stage = stage;
    state.taskState.subStage = subStage;
    state.taskState.failure = errorResponse;
    return state;
  }

  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, null, e));
  }

  /**
   * This class defines the document state associated with a single VmProvisioningTaskService class.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {

    /**
     * State of the current task.
     */
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState taskState;

    /**
     * Control flags influencing the behavior of the task.
     */
    @Immutable
    @DefaultInteger(0)
    public Integer controlFlags;

    /**
     * Friendly Name of the provisioning VM.
     */
    @NotNull
    @Immutable
    public String vmName;

    /**
     * Name of the VM Flavor used for the provisioning VM.
     */
    @NotNull
    @Immutable
    public String vmFlavorName;

    /**
     * Name of the Disk Flavor used for the provisioning VM.
     */
    @NotNull
    @Immutable
    public String diskFlavorName;

    /**
     * Id of the network used for the provisioning VM.
     */
    @Immutable
    public String vmNetworkId;

    /**
     * Identifier of the Image used for the provisioning VM.
     */
    @NotNull
    @Immutable
    public String imageId;

    /**
     * Identifier of the Project used for the provisioning VM.
     */
    @NotNull
    @Immutable
    public String projectId;

    /**
     * Tags that will be registered for the provisioning VM.
     */
    @Immutable
    public Set<String> vmTags;

    /**
     * Template used for Cloud Init's user-data file.
     */
    @NotNull
    @Immutable
    public FileTemplate userData;

    /**
     * Template used for Cloud Init's meta-data file.
     */
    @NotNull
    @Immutable
    public FileTemplate metaData;

    /**
     * Unique Identifier of the VM as created by API-FE.
     * This property is set after the VM has been successfully created.
     */
    @WriteOnce
    public String vmId;

    /**
     * Ip Address of the VM.
     * This property is set after the VM has been successfully created.
     */
    @WriteOnce
    public String vmIpAddress;

    /**
     * This class represents the state of the VmProvisioningTask.
     */
    public static class TaskState extends com.vmware.xenon.common.TaskState {

      /**
       * Current sub-stage of the task.
       */
      public SubStage subStage;

      /**
       * Valid sub-states of the task.
       */
      public enum SubStage {
        CREATE_VM,
        ATTACH_ISO,
        START_VM,
        VERIFY_VM,
      }
    }
  }
}
