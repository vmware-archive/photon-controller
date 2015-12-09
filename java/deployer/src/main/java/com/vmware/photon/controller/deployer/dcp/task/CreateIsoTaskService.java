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

import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.client.ApiClient;
import com.vmware.photon.controller.common.dcp.InitializationUtils;
import com.vmware.photon.controller.common.dcp.QueryTaskUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.dcp.TaskUtils;
import com.vmware.photon.controller.common.dcp.ValidationUtils;
import com.vmware.photon.controller.common.dcp.validation.DefaultBoolean;
import com.vmware.photon.controller.common.dcp.validation.DefaultInteger;
import com.vmware.photon.controller.common.dcp.validation.DefaultTaskState;
import com.vmware.photon.controller.common.dcp.validation.Immutable;
import com.vmware.photon.controller.common.dcp.validation.NotNull;
import com.vmware.photon.controller.deployer.configuration.LoadBalancerServer;
import com.vmware.photon.controller.deployer.configuration.ServiceConfigurator;
import com.vmware.photon.controller.deployer.configuration.ZookeeperServer;
import com.vmware.photon.controller.deployer.dcp.ContainersConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerService;
import com.vmware.photon.controller.deployer.dcp.entity.ContainerTemplateService;
import com.vmware.photon.controller.deployer.dcp.entity.VmService;
import com.vmware.photon.controller.deployer.dcp.util.ApiUtils;
import com.vmware.photon.controller.deployer.dcp.util.ControlFlags;
import com.vmware.photon.controller.deployer.dcp.util.HostUtils;
import com.vmware.photon.controller.deployer.deployengine.ScriptRunner;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class implements a DCP micro-service which performs the task of creating an ISO file.
 */
public class CreateIsoTaskService extends StatefulService {

  @VisibleForTesting
  public static final String SCRIPT_NAME = "esx-create-vm-iso";

  private static final String CONFIG_FILENAME = SCRIPT_NAME + "-config-file";

  private static final String TMP_CONFIG_DIR = "/tmp/configuration";

  /**
   * This class defines a template used to create files with the specified parameters.
   */
  public static class FileTemplate {

    /**
     * This value represents the absolute path of the template file.
     */
    @NotNull
    @Immutable
    public String filePath;

    /**
     * This value represents a collection of parameters that are used to create the file from the template.
     */
    @NotNull
    @Immutable
    public Map<String, String> parameters;
  }

  /**
   * This class defines the document state associated with a single {@link CreateIsoTaskService} instance.
   */
  public static class State extends ServiceDocument {

    /**
     * This value represents the unique identifier of the VM that the new ISO will be attached to.
     */
    @NotNull
    @Immutable
    public String vmId;

    /**
     * This value represents the template for creating a cloud-init user-data file, which is added to the
     * created iso.
     */
    @NotNull
    @Immutable
    public FileTemplate userDataTemplate;

    /**
     * This value represents the template for creating a cloud-init meta-data file, which is added to the
     * created iso.
     */
    @NotNull
    @Immutable
    public FileTemplate metaDataTemplate;

    /**
     * This value represents the flag which will dictate if we need to place the config files inside ISO.
     */
    @Immutable
    @DefaultBoolean(value = false)
    public Boolean placeConfigFilesInISO;

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
  }

  public CreateIsoTaskService() {
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
    validateState(startState);

    if (TaskState.TaskStage.CREATED == startState.taskState.stage) {
      startState.taskState.stage = TaskState.TaskStage.STARTED;
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
        if (currentState.placeConfigFilesInISO) {
          getVmService(currentState);
        } else {
          createIsoFile(currentState, null);
        }
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
    ValidationUtils.validateTaskStageProgression(startState.taskState, patchState.taskState);
  }

  /**
   * This method applies a patch to a state object.
   *
   * @param startState Supplies the start state object.
   * @param patchState Supplies the patch state object.
   */
  private State applyPatch(State startState, State patchState) {
    if (patchState.taskState.stage != startState.taskState.stage) {
      ServiceUtils.logInfo(this, "Moving to stage %s", patchState.taskState.stage);
      startState.taskState = patchState.taskState;
    }

    return startState;
  }

  private void getVmService(final State currentState) throws Throwable {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(VmService.State.class));

    QueryTask.Query idClause = new QueryTask.Query()
        .setTermPropertyName(VmService.State.FIELD_NAME_VM_ID)
        .setTermMatchValue(currentState.vmId);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(kindClause);
    querySpecification.query.addBooleanClause(idClause);
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
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(CreateIsoTaskService.this, documentLinks);
              queryContainersForVm(currentState, documentLinks.iterator().next());
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void queryContainersForVm(final State currentState, String vmServiceLink) {
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(Utils.buildKind(ContainerService.State.class));

    QueryTask.Query vmServiceLinkClause = new QueryTask.Query()
        .setTermPropertyName(ContainerService.State.FIELD_NAME_VM_SERVICE_LINK)
        .setTermMatchValue(vmServiceLink);

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
              Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(operation);
              QueryTaskUtils.logQueryResults(CreateIsoTaskService.this, documentLinks);
              getContainerEntities(currentState, documentLinks);
            } catch (Throwable t) {
              failTask(t);
            }
          }
        });

    sendRequest(queryPostOperation);
  }

  private void getContainerEntities(final State currentState, Collection<String> containerLinks) {

    OperationJoin
        .create(containerLinks.stream()
            .map(containerLink -> Operation.createGet(this, containerLink)))
        .setCompletion((ops, exs) -> {
          if (null != exs && !exs.isEmpty()) {
            failTask(exs);
            return;
          }

          try {
            Set<ContainerService.State> containerStates = ops.values().stream()
                .map(getOperation -> getOperation.getBody(ContainerService.State.class))
                .collect(Collectors.toSet());
            applyConfiguration(currentState, containerStates);
          } catch (Throwable t) {
            failTask(t);
          }
        })
        .sendWith(this);
  }

  private void applyConfiguration(final State currentState, Set<ContainerService.State> containerServices)
      throws Exception {

    String mustacheDirectory = TMP_CONFIG_DIR + "-" + currentState.vmId + "/";
    ServiceConfigurator serviceConfigurator = HostUtils.getServiceConfiguratorFactory(this).create();
    serviceConfigurator.copyDirectory(HostUtils.getDeployerContext(CreateIsoTaskService.this).getConfigDirectory(),
        mustacheDirectory);

    final AtomicInteger pendingRequests = new AtomicInteger(containerServices.size());

    FutureCallback<ContainerService.State> callback =
        new FutureCallback<ContainerService.State>() {
          @Override
          public void onSuccess(@Nullable ContainerService.State result) {
            if (0 == pendingRequests.decrementAndGet()) {
              try {
                createIsoFile(currentState, mustacheDirectory);
              } catch (Throwable t) {
                failTask(t);
              }
            }
          }

          @Override
          public void onFailure(Throwable t) {
            failTask(t);
          }
        };

    containerServices.stream()
        .forEach(containerService -> getContainerTemplate(
            mustacheDirectory, Utils.fromJson(containerService, ContainerService.State.class), callback));
  }

  private void getContainerTemplate(String mustacheDirectory, ContainerService.State containerService,
                                    FutureCallback<ContainerService.State> callback) {
    final Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          failTask(throwable);
          return;
        }

        ContainerTemplateService.State templateState = operation.getBody(ContainerTemplateService.State.class);
        applyMustacheParameters(mustacheDirectory, containerService, templateState.name, callback);
      }
    };

    Operation getOperation = Operation
        .createGet(UriUtils.buildUri(getHost(), containerService.containerTemplateServiceLink))
        .setCompletion(completionHandler);

    sendRequest(getOperation);
  }

  private void applyMustacheParameters(String mustacheDirectory, ContainerService.State containerService, String
      containerType, FutureCallback<ContainerService.State> callback) {
    ServiceConfigurator serviceConfigurator = HostUtils.getServiceConfiguratorFactory(this).create();

    try {
      Map<String, Object> dynamicParameters = new HashMap<>();
      if (containerService.dynamicParameters != null) {
        dynamicParameters.putAll(containerService.dynamicParameters);
      }

      // Deserialize the string to get load balancer servers and zookeeper quorum
      Type lbType = new TypeToken<ArrayList<LoadBalancerServer>>() {
      }.getType();
      dynamicParameters.computeIfPresent(BuildRuntimeConfigurationTaskService.ENV_LOADBALANCER_SERVERS,
          (k, v) -> new Gson().fromJson(v.toString(), lbType));
      Type zkType = new TypeToken<ArrayList<ZookeeperServer>>() {
      }.getType();
      dynamicParameters.computeIfPresent(BuildRuntimeConfigurationTaskService.ENV_ZOOKEEPER_QUORUM,
          (k, v) -> new Gson().fromJson(v.toString(), zkType));

      serviceConfigurator.applyDynamicParameters(mustacheDirectory, ContainersConfig.ContainerType.valueOf
          (containerType), dynamicParameters);
      callback.onSuccess(containerService);
    } catch (Throwable t) {
      callback.onFailure(t);
    }
  }

  private void createIsoFile(final State currentState, String configDirectoryName) throws Throwable {

    final File isoFile = new File("/tmp",
        CONFIG_FILENAME + "-" + currentState.vmId + ".iso");
    DeployerContext deployerContext = HostUtils.getDeployerContext(this);
    final File userDataConfigFile = new File(deployerContext.getScriptDirectory(),
        CONFIG_FILENAME + "-user-data-" + currentState.vmId + ".yml");
    final File metaDataConfigFile = new File(deployerContext.getScriptDirectory(),
        CONFIG_FILENAME + "-meta-data-" + currentState.vmId + ".yml");
    File scriptLogFile = new File(deployerContext.getScriptDirectory(),
        SCRIPT_NAME + "-" + currentState.vmId + ".log");

    String userDataConfigFilename = createFile(currentState.userDataTemplate, userDataConfigFile);
    String metaDataConfigFilename = createFile(currentState.metaDataTemplate, metaDataConfigFile);

    List<String> command = new ArrayList<>();
    command.add("./" + SCRIPT_NAME);
    command.add(isoFile.getAbsolutePath());
    command.add(userDataConfigFilename);
    command.add(metaDataConfigFilename);
    if (configDirectoryName != null) {
      command.add(configDirectoryName);
    }

    ScriptRunner scriptRunner = new ScriptRunner.Builder(command, deployerContext.getScriptTimeoutSec())
        .directory(deployerContext.getScriptDirectory())
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
            if (configDirectoryName != null) {
              FileUtils.deleteDirectory(new File(configDirectoryName));
            }
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

  /**
   * This methods creates a file using the passed-in template and returns the absolute path of the created file.
   *
   * @param template
   * @param isoFile
   * @return
   * @throws IOException
   */
  private String createFile(FileTemplate template, File isoFile) throws IOException {
    String content = new String(Files.readAllBytes(Paths.get(template.filePath)),
        StandardCharsets.UTF_8);

    for (Map.Entry<String, String> parameter : template.parameters.entrySet()) {
      content = content.replace(parameter.getKey(), parameter.getValue());
    }

    Files.write(isoFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return isoFile.getAbsolutePath();
  }

  /**
   * This method performs document state updates in response to an operation which
   * sets the state to STARTED.
   *
   * @param currentState Supplies the current state object.
   */
  private void attachAndUploadIso(final State currentState, final String isoFile) throws IOException {
    final ApiClient client = HostUtils.getApiClient(this);
    ListenableFutureTask<Task> futureTask = ListenableFutureTask.create(new Callable<Task>() {
      @Override
      public Task call() {
        try {
          return client.getVmApi().uploadAndAttachIso(currentState.vmId, isoFile);
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
            failTask(new IllegalStateException("IsoUploadAndAttach returned null"));
            return;
          }
          Files.deleteIfExists(Paths.get(isoFile));
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
   * returned, the service is transitioned to the corresponding stage or sub-stage.
   *
   * @param currentState Supplies the current state object.
   * @param task         Supplies the task object.
   */
  private void processTask(final State currentState, final Task task) {
    switch (task.getState().toUpperCase()) {
      case "QUEUED":
      case "STARTED":
        pollTask(currentState, task.getId());
        break;
      case "ERROR":
        throw new RuntimeException(ApiUtils.getErrors(task));
      case "COMPLETED":
        TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FINISHED, null));
        break;
      default:
        throw new RuntimeException("Unknown task state: " + task.getState());
    }
  }

  /**
   * This method polls the status of a APIFE task.
   *
   * @param currentState Supplies the current state object.
   * @param taskId       Supplies the ID of the task to be polled.
   */
  private void pollTask(final State currentState, final String taskId) {
    Runnable taskPoller = new Runnable() {
      @Override
      public void run() {
        try {
          Task task = callGetTask(taskId);
          processTask(currentState, task);
        } catch (Throwable t) {
          failTask(t);
        }
      }
    };

    getHost().schedule(taskPoller, HostUtils.getDeployerContext(this).getTaskPollDelay(), TimeUnit.MILLISECONDS);
  }

  /**
   * This method gets a task object from APIFE for a given task ID.
   *
   * @param taskId Supplies the ID of the task object.
   * @return Returns the task object for the given task ID.
   * @throws IOException Throws exception if polling the task status from APIFE encounters
   *                     any error.
   */
  private Task callGetTask(final String taskId) throws IOException {
    Task task = HostUtils.getApiClient(this).getTasksApi().getTask(taskId);
    ServiceUtils.logInfo(this, "GetTask API call returned task %s", task.toString());
    return task;
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to a new state.
   *
   * @param state
   */
  private void sendStageProgressPatch(TaskState state) {
    ServiceUtils.logInfo(this, "Sending self-patch to stage %s", state.stage);
    TaskUtils.sendSelfPatch(this, buildPatch(state.stage, null));
  }

  /**
   * This method sends a patch operation to the current service instance to
   * move to the FAILED state in response to the specified exception.
   *
   * @param e
   */
  private void failTask(Throwable e) {
    ServiceUtils.logSevere(this, e);
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, e));
  }

  private void failTask(Map<Long, Throwable> exs) {
    exs.values().forEach(e -> ServiceUtils.logSevere(this, e));
    TaskUtils.sendSelfPatch(this, buildPatch(TaskState.TaskStage.FAILED, exs.values().iterator().next()));
  }

  /**
   * This method builds a patch state object which can be used to submit a
   * self-patch.
   *
   * @param stage
   * @param t
   * @return
   */
  @VisibleForTesting
  protected State buildPatch(TaskState.TaskStage stage, @Nullable Throwable t) {
    State state = new State();
    state.taskState = new TaskState();
    state.taskState.stage = stage;

    if (null != t) {
      state.taskState.failure = Utils.toServiceErrorResponse(t);
    }

    return state;
  }
}
